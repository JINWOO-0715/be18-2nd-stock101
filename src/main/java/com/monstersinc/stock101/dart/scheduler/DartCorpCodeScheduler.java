package com.monstersinc.stock101.dart.scheduler;

import com.monstersinc.stock101.dart.dto.CorpCodeSyncResult;
import com.monstersinc.stock101.dart.service.DartCorpCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * DART 고유번호 자동 동기화 스케줄러
 * 한국투자증권 MST 스케줄러와 같은 시간에 실행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DartCorpCodeScheduler {

    private final DartCorpCodeService dartCorpCodeService;

    /**
     * 09:00 - 개장 전 DART 고유번호 동기화
     * MST 업데이트 후 corpCode 매핑
     */
    @Scheduled(cron = "0 5 9 * * *") // 09:05 (MST 업데이트 후 5분 뒤)
    public void syncCorpCodesBeforeMarketOpen() {
        log.info("========== [09:05] DART 고유번호 동기화 시작 ==========");
        try {
            CorpCodeSyncResult result = dartCorpCodeService.syncCorpCodes();
            log.info("DART 고유번호 동기화 결과: {} - {} (전체: {}, 상장: {}, 업데이트: {})",
                    result.isSuccess(),
                    result.getMessage(),
                    result.getTotalCount(),
                    result.getListedCount(),
                    result.getUpdatedCount());
        } catch (Exception e) {
            log.error("DART 고유번호 동기화 중 오류 발생", e);
        }
        log.info("========== [09:05] DART 고유번호 동기화 완료 ==========");
    }


}
