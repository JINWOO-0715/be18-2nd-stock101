package com.monstersinc.stock101.kis.queue;

import com.monstersinc.stock101.common.ratelimit.RateLimitException;
import com.monstersinc.stock101.common.ratelimit.RateLimiter;
import com.monstersinc.stock101.kis.dto.KisCandleResponse;
import com.monstersinc.stock101.kis.event.StockPriceUpdateCompletedEvent;
import com.monstersinc.stock101.kis.event.StockPriceUpdateFailedEvent;
import com.monstersinc.stock101.kis.service.KisApiClient;
import com.monstersinc.stock101.stock.model.mapper.StockPriceRepository;
import com.monstersinc.stock101.stock.model.vo.StockPrice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 백그라운드에서 Queue의 요청을 처리하는 Worker
 * 0.1초마다 Queue 체크하여 Rate Limit을 준수하며 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockPriceUpdateWorker {

    private final StockPriceUpdateQueue queue;
    private final RateLimiter rateLimiter;
    private final StockPriceRepository stockPriceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final KisApiClient kisApiClient;

    private static final int MAX_ITEMS_PER_REQUEST = 100;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * 0.1초마다 Queue 체크
     * Queue에서 요청을 꺼내 처리
     */
    @Scheduled(fixedDelay = 100)
    public void processQueue() {
        try {
            Optional<StockPriceUpdateRequest> requestOpt = queue.dequeue();

            if (requestOpt.isEmpty()) {
                return;
            }

            StockPriceUpdateRequest request = requestOpt.get();
            queue.setStatus(request.getRequestId(), RequestStatus.PROCESSING);

            log.info("Worker 시작: requestId={}, stockCode={}, startDate={}, endDate={}",
                    request.getRequestId(), request.getStockCode(),
                    request.getStartDate(), request.getEndDate());

            try {
                int totalSaved = processRequest(request);
                queue.setStatus(request.getRequestId(), RequestStatus.COMPLETED);

                // 완료 이벤트 발행 (알림용)
                eventPublisher.publishEvent(
                        StockPriceUpdateCompletedEvent.of(
                                request.getRequestId(),
                                request.getStockCode(),
                                totalSaved
                        )
                );

                log.info("✅ Worker 완료: requestId={}, stockCode={}, saved={}",
                        request.getRequestId(), request.getStockCode(), totalSaved);

            } catch (Exception e) {
                log.error("❌ Worker 실패: requestId={}, stockCode={}, error={}",
                        request.getRequestId(), request.getStockCode(), e.getMessage(), e);

                queue.setStatus(request.getRequestId(), RequestStatus.FAILED);

                // 실패 이벤트 발행
                eventPublisher.publishEvent(
                        StockPriceUpdateFailedEvent.of(
                                request.getRequestId(),
                                request.getStockCode(),
                                e.getMessage()
                        )
                );
            }

        } catch (Exception e) {
            log.error("Worker 처리 중 예외 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * 요청 처리 (while 루프로 100건씩 조회)
     * Rate Limiter 적용
     */
    private int processRequest(StockPriceUpdateRequest request) throws InterruptedException {
        int totalSaved = 0;
        LocalDate currentEndDate = request.getEndDate();

        while (currentEndDate.isAfter(request.getStartDate()) || currentEndDate.isEqual(request.getStartDate())) {

            // ⭐ Rate Limiter 토큰 획득 (최대 5초 대기)
            boolean acquired = rateLimiter.acquire("KIS_API", 5000);
            if (!acquired) {
                throw new RateLimitException("KIS_API", 5000);
            }

            // ⭐ KisApiClient 사용 (캐시 우선 조회)
            KisCandleResponse response = kisApiClient.fetchCandleData(
                    request.getStockCode(),
                    request.getStartDate(),
                    currentEndDate
            );

            if (response == null) {
                log.warn("KIS API 응답 없음");
                break;
            }

            if (!response.isSuccess() || response.getOutput2() == null || response.getOutput2().isEmpty()) {
                log.info("종목 {} 더 이상 데이터 없음", request.getStockCode());
                break;
            }

            // 데이터 변환 및 저장
            List<StockPrice> entities = new ArrayList<>();
            for (KisCandleResponse.KisCandleData data : response.getOutput2()) {
                try {
                    StockPrice.KisPriceData priceData = new StockPrice.KisPriceData(
                            data.getStckBsopDate(),
                            data.getStckOprc(),
                            data.getStckHgpr(),
                            data.getStckLwpr(),
                            data.getStckClpr(),
                            data.getAcmlVol(),
                            data.getAcmlTrPbmn());

                    StockPrice entity = StockPrice.fromKisResponse(request.getStockId(), priceData);
                    entities.add(entity);
                } catch (Exception e) {
                    log.warn("데이터 변환 실패: {} - {}", data.getStckBsopDate(), e.getMessage());
                }
            }

            // 배치 저장
            if (!entities.isEmpty()) {
                stockPriceRepository.insertPrices(entities);
                totalSaved += entities.size();
                log.info("✅ Worker 저장: stockCode={}, saved={}", request.getStockCode(), entities.size());
            }

            // 다음 조회 설정
            if (response.getOutput2().size() < MAX_ITEMS_PER_REQUEST) {
                log.debug("데이터 수 {} < {}, 조회 종료", response.getOutput2().size(), MAX_ITEMS_PER_REQUEST);
                break;
            }

            String lastDateStr = response.getOutput2().get(response.getOutput2().size() - 1).getStckBsopDate();
            LocalDate lastDate = LocalDate.parse(lastDateStr, DATE_FORMATTER);
            currentEndDate = lastDate.minusDays(1);

            log.debug("Worker 다음 조회: startDate={}, endDate={}", request.getStartDate(), currentEndDate);
        }

        return totalSaved;
    }
}
