package com.monstersinc.stock101.stock.scheduler;

import com.monstersinc.stock101.stock.service.StockMstDownloadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * MST 파일 자동 다운로드 스케줄러
 * 한국 증권시장 영업 시간에 맞춰 KOSPI/KOSDAQ 마스터 파일을 자동 동기화
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockMstScheduler {

    private final StockMstDownloadService stockMstDownloadService;

    /**
     * 09:00 - 개장 전 KOSPI/KOSDAQ 재업데이트
     * 전 날 업데이트 실패 시 재시도 목적
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void updateBeforeMarketOpen() {
        log.info("========== [09:00] 개장 전 KOSPI/KOSDAQ 재업데이트 시작 ==========");
        try {
            var kospiResult = stockMstDownloadService.downloadAndUpdateKospi();
            log.info("KOSPI 업데이트 결과: {} - {}", kospiResult.isSuccess(), kospiResult.getMessage());

            var kosdaqResult = stockMstDownloadService.downloadAndUpdateKosdaq();
            log.info("KOSDAQ 업데이트 결과: {} - {}", kosdaqResult.isSuccess(), kosdaqResult.getMessage());
        } catch (Exception e) {
            log.error("개장 전 업데이트 중 오류 발생", e);
        }
        log.info("========== [09:00] 개장 전 KOSPI/KOSDAQ 재업데이트 완료 ==========");
    }

    /**
     * 16:00 - KOSPI 마스터 파일 업데이트
     * 장 마감 후 KOSPI 종목 정보 동기화
     */
    @Scheduled(cron = "0 0 16 * * *")
    public void updateKospiDaily() {
        log.info("========== [16:00] KOSPI 마스터 파일 업데이트 시작 ==========");
        try {
            var result = stockMstDownloadService.downloadAndUpdateKospi();
            log.info("KOSPI 업데이트 완료: {} - {} ({}개)", result.isSuccess(), result.getMessage(), result.getUpdatedCount());
        } catch (Exception e) {
            log.error("KOSPI 업데이트 중 오류 발생", e);
        }
        log.info("========== [16:00] KOSPI 마스터 파일 업데이트 완료 ==========");
    }

    /**
     * 16:30 - KOSDAQ 마스터 파일 업데이트
     * 장 마감 후 KOSDAQ 종목 정보 동기화
     */
    @Scheduled(cron = "0 30 16 * * *")
    public void updateKosdaqDaily() {
        log.info("========== [16:30] KOSDAQ 마스터 파일 업데이트 시작 ==========");
        try {
            var result = stockMstDownloadService.downloadAndUpdateKosdaq();
            log.info("KOSDAQ 업데이트 완료: {} - {} ({}개)", result.isSuccess(), result.getMessage(), result.getUpdatedCount());
        } catch (Exception e) {
            log.error("KOSDAQ 업데이트 중 오류 발생", e);
        }
        log.info("========== [16:30] KOSDAQ 마스터 파일 업데이트 완료 ==========");
    }
}
