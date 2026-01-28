package com.monstersinc.stock101.stock.service;

import com.monstersinc.stock101.kis.service.KisStockPriceService;
import com.monstersinc.stock101.stock.model.dto.StockPriceResponseDto;
import com.monstersinc.stock101.stock.model.mapper.StockMapper;
import com.monstersinc.stock101.stock.model.mapper.StockPriceRepository;
import com.monstersinc.stock101.stock.model.vo.Stock;
import com.monstersinc.stock101.stock.model.vo.StockPrice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 주식 시세 서비스
 * Redis 캐시를 활용한 일봉 데이터 조회
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockPriceService {

    private final StockPriceRepository stockPriceRepository;
    private final StockMapper stockMapper;
    private final KisStockPriceService kisStockPriceService;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String REDIS_KEY_PREFIX = "stock:price:lastUpdate:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * 종목의 일봉 데이터 조회 (최근 N일)
     * 내부적으로 기간 조회 메서드 호출
     *
     * @param stockCode 종목코드 (6자리)
     * @param days 조회할 일수 (기본 30일)
     * @return 일봉 데이터 응답
     */
    public StockPriceResponseDto getDailyPrices(String stockCode, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);
        return getDailyPrices(stockCode, startDate, endDate);
    }

    /**
     * 종목의 일봉 데이터 조회 (기간별)
     * - Redis에서 최종 업데이트 일자 확인
     * - 종료일이 오늘이고, 아직 업데이트 안 했으면 KIS API에서 최신 데이터 fetch 후 DB 저장
     * - DB에서 일봉 데이터 조회 후 반환
     *
     * @param stockCode 종목코드 (6자리)
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 일봉 데이터 응답
     */
    public StockPriceResponseDto getDailyPrices(String stockCode, LocalDate startDate, LocalDate endDate) {
        // 1. 종목 정보 조회
        Stock stock = stockMapper.selectStockByCode(stockCode);
        if (stock == null) {
            throw new IllegalArgumentException("존재하지 않는 종목코드입니다: " + stockCode);
        }

        Long stockId = stock.getStockId();
        LocalDate today = LocalDate.now();

        // 2. 종료일이 오늘 이후면 업데이트 체크
        if (endDate.isEqual(today) || endDate.isAfter(today)) {
            refreshTodayPriceIfNeeded(stockCode);
        }

        // 3. DB에서 기간별 데이터 조회
        List<StockPrice> prices = stockPriceRepository.findByStockIdAndDatetimeBetweenOrderByDatetimeAsc(
                stockId, startDate, endDate);

        // 4. 최종 업데이트 일자
        LocalDate actualLastUpdate = prices.isEmpty() ? null : prices.get(prices.size() - 1).getDatetime();

        return StockPriceResponseDto.of(
                stockCode,
                stock.getName(),
                actualLastUpdate,
                prices
        );
    }

    /**
     * 오늘 주가 데이터 업데이트 (Redis 캐시 체크)
     * - Redis에서 최종 업데이트 일자 확인
     * - 오늘 아직 업데이트 안 했으면 KIS API 호출 (DB 최신 날짜 기준으로 증분 업데이트)
     */
    private void refreshTodayPriceIfNeeded(String stockCode) {
        LocalDate today = LocalDate.now();
        String redisKey = REDIS_KEY_PREFIX + stockCode;
        String lastUpdateStr = redisTemplate.opsForValue().get(redisKey);
        LocalDate lastUpdate = (lastUpdateStr != null) ? LocalDate.parse(lastUpdateStr, DATE_FORMAT) : null;

        log.debug("종목 {} 최종 업데이트 일자: {}", stockCode, lastUpdate);

        if (lastUpdate != null && !lastUpdate.isBefore(today)) {
            log.debug("종목 {} 오늘 이미 업데이트됨, 캐시 사용", stockCode);
            return;
        }

        try {
            log.info("📈 종목 {} 오늘 데이터 업데이트 시작", stockCode);
            int savedCount = kisStockPriceService.updateStockPrices(stockCode);

            if (savedCount > 0) {
                redisTemplate.opsForValue().set(redisKey, today.format(DATE_FORMAT), 1, TimeUnit.DAYS);
                log.info("✅ 종목 {} 업데이트 완료, Redis 캐시 갱신", stockCode);
            } else {
                log.warn("종목 {} 업데이트 데이터 없음 (휴장일 또는 API 오류)", stockCode);
                // 반복 호출 방지 (짧은 TTL)
                redisTemplate.opsForValue().set(redisKey, today.format(DATE_FORMAT), 1, TimeUnit.HOURS);
            }
        } catch (Exception e) {
            log.error("종목 {} 업데이트 실패: {}", stockCode, e.getMessage());
            // 업데이트 실패해도 기존 데이터는 조회 가능하게 함
        }
    }

    /**
     * Redis 캐시 강제 무효화 (관리자용)
     */
    public void invalidateCache(String stockCode) {
        String redisKey = REDIS_KEY_PREFIX + stockCode;
        redisTemplate.delete(redisKey);
        log.info("종목 {} 캐시 무효화 완료", stockCode);
    }

    /**
     * 캐시 상태 확인
     */
    public LocalDate getLastUpdateDate(String stockCode) {
        String redisKey = REDIS_KEY_PREFIX + stockCode;
        String lastUpdateStr = redisTemplate.opsForValue().get(redisKey);
        return (lastUpdateStr != null) ? LocalDate.parse(lastUpdateStr, DATE_FORMAT) : null;
    }
}
