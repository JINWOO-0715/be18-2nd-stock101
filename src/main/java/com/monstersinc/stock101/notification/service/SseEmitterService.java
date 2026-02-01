package com.monstersinc.stock101.notification.service;

import com.monstersinc.stock101.notification.model.dto.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE Emitter 관리 서비스
 * 사용자별 SSE 연결을 관리하고 실시간 알림을 전송합니다.
 */
@Slf4j
@Service
public class SseEmitterService {

    // 타임아웃: 1시간 (밀리초)
    private static final Long DEFAULT_TIMEOUT = 60 * 60 * 1000L;

    // userId -> SseEmitter 매핑
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * SSE 연결 생성
     * @param userId 사용자 ID
     * @return SseEmitter
     */
    public SseEmitter createEmitter(Long userId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);

        // 기존 연결이 있다면 제거
        removeEmitter(userId);

        // 새 연결 저장
        emitters.put(userId, emitter);
        log.info("SSE 연결 생성: userId={}", userId);

        // 타임아웃 시 자동 제거
        emitter.onTimeout(() -> {
            log.info("SSE 연결 타임아웃: userId={}", userId);
            removeEmitter(userId);
        });

        // 완료 시 자동 제거
        emitter.onCompletion(() -> {
            log.info("SSE 연결 완료: userId={}", userId);
            removeEmitter(userId);
        });

        // 에러 시 자동 제거
        emitter.onError((e) -> {
            log.error("SSE 연결 에러: userId={}", userId, e);
            removeEmitter(userId);
        });

        // 연결 직후 초기 이벤트 전송 (연결 확인용)
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("SSE 연결이 설정되었습니다."));
        } catch (IOException e) {
            log.error("초기 이벤트 전송 실패: userId={}", userId, e);
            removeEmitter(userId);
        }

        return emitter;
    }

    /**
     * 특정 사용자에게 알림 이벤트 전송
     * @param userId 사용자 ID
     * @param event 알림 이벤트
     */
    public void sendNotification(Long userId, NotificationEvent event) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                String eventName = event.getType().equals("new") ? "notification" : "count_update";
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(event));
                log.info("알림 전송 성공: userId={}, type={}", userId, event.getType());
            } catch (IOException e) {
                log.error("알림 전송 실패: userId={}", userId, e);
                removeEmitter(userId);
            }
        } else {
            log.debug("연결된 SSE가 없습니다: userId={}", userId);
        }
    }

    /**
     * 미읽음 개수 업데이트 이벤트 전송
     * @param userId 사용자 ID
     * @param count 미읽음 개수
     */
    public void sendCountUpdate(Long userId, Integer count) {
        NotificationEvent event = NotificationEvent.builder()
                .type("count_update")
                .count(count)
                .build();
        sendNotification(userId, event);
    }

    /**
     * SSE 연결 제거
     * @param userId 사용자 ID
     */
    public void removeEmitter(Long userId) {
        SseEmitter emitter = emitters.remove(userId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("Emitter 완료 처리 중 에러: userId={}", userId, e);
            }
        }
    }

    /**
     * 현재 연결된 사용자 수 조회
     * @return 연결된 사용자 수
     */
    public int getConnectedUserCount() {
        return emitters.size();
    }
}
