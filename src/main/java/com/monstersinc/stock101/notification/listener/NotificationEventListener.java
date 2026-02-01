package com.monstersinc.stock101.notification.listener;

import com.monstersinc.stock101.common.event.DocumentProcessedEvent;
import com.monstersinc.stock101.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 알림 이벤트 리스너
 * 도메인 이벤트를 구독하여 알림을 전송
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;

    /**
     * 문서 처리 완료 이벤트 핸들러
     * 비동기로 동작하여 메인 처리 흐름에 영향을 주지 않음
     *
     * @param event 문서 처리 완료 이벤트
     */
    @Async
    @EventListener
    public void handleDocumentProcessedEvent(DocumentProcessedEvent event) {
        log.info("문서 처리 이벤트 수신: sourceId={}, status={}", event.getSourceId(), event.getStatus());

        try {
            // 성공한 경우에만 알림 전송
            if (event.isSuccess()) {
                notificationService.createDocumentCompletedNotification(
                        event.getUserId(),
                        event.getSourceId(),
                        event.getCompanyName()
                );
                log.info("문서 처리 완료 알림 전송 성공: userId={}, sourceId={}", event.getUserId(), event.getSourceId());
            } else {
                // 실패한 경우 실패 알림 전송 (선택사항)
                String failureMessage = String.format("'%s' 문서 분석에 실패했습니다.", event.getCompanyName());
                notificationService.createNotification(
                        event.getUserId(),
                        event.getSourceId(),
                        failureMessage
                );
                log.warn("문서 처리 실패 알림 전송: userId={}, sourceId={}", event.getUserId(), event.getSourceId());
            }
        } catch (Exception e) {
            // 알림 전송 실패는 로깅만 하고 예외를 전파하지 않음
            log.error("알림 전송 중 오류 발생: sourceId={}, userId={}", event.getSourceId(), event.getUserId(), e);
        }
    }
}
