package com.monstersinc.stock101.notification.controller;

import com.monstersinc.stock101.auth.jwt.JwtTokenProvider;
import com.monstersinc.stock101.auth.jwt.JwtUtil;
import com.monstersinc.stock101.notification.model.dto.NotificationEvent;
import com.monstersinc.stock101.notification.model.dto.NotificationResponseDto;
import com.monstersinc.stock101.notification.service.NotificationService;
import com.monstersinc.stock101.notification.service.SseEmitterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 알림 컨트롤러
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notification API", description = "알림 관리 및 실시간 알림 스트리밍 API")
public class NotificationController {

    private final NotificationService notificationService;
    private final SseEmitterService sseEmitterService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtUtil jwtUtil;

    /**
     * SSE 스트림 연결
     */
    @Operation(
        summary = "알림 실시간 스트리밍 (SSE)",
        description = "JWT 토큰을 사용하여 SSE 연결을 생성하고 실시간 알림을 수신합니다. " +
                      "연결 후 'notification' 이벤트(새 알림)와 'count_update' 이벤트(미읽음 개수 업데이트)를 수신할 수 있습니다."
    )
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications(
            @Parameter(description = "JWT 액세스 토큰 (Bearer 접두사 포함 또는 제외)")
            @RequestParam String token) {
        try {
            // 1. Bearer 접두사 제거
            String jwtToken = jwtTokenProvider.resolveToken(token);
            if (jwtToken == null) {
                jwtToken = token; // Bearer 없이 토큰만 전달된 경우
            }

            // 2. 토큰 검증
            if (!jwtUtil.validateToken(jwtToken)) {
                log.warn("SSE 연결 실패: 유효하지 않은 토큰");
                throw new IllegalArgumentException("유효하지 않은 토큰입니다.");
            }

            // 3. userId 추출
            Long userId = Long.parseLong(jwtUtil.getUserId(jwtToken));

            // 4. SseEmitter 생성
            SseEmitter emitter = sseEmitterService.createEmitter(userId);

            // 5. 연결 직후 현재 미읽음 개수 전송
            int unreadCount = notificationService.getUnreadCount(userId);
            sseEmitterService.sendCountUpdate(userId, unreadCount);

            return emitter;

        } catch (Exception e) {
            log.error("SSE 연결 생성 실패", e);
            SseEmitter emitter = new SseEmitter(0L);
            emitter.completeWithError(e);
            return emitter;
        }
    }

    /**
     * 사용자의 모든 알림 조회
     */
    @GetMapping
    public ResponseEntity<List<NotificationResponseDto>> getAllNotifications(
            @AuthenticationPrincipal Long userId) {
        List<NotificationResponseDto> notifications = notificationService.getNotificationsByUserId(userId);
        return ResponseEntity.ok(notifications);
    }

    /**
     * 사용자의 미읽음 알림 조회
     */
    @GetMapping("/unread")
    public ResponseEntity<List<NotificationResponseDto>> getUnreadNotifications(
            @AuthenticationPrincipal Long userId) {
        List<NotificationResponseDto> notifications = notificationService.getUnreadNotificationsByUserId(userId);
        return ResponseEntity.ok(notifications);
    }

    /**
     * 미읽음 알림 개수 조회
     */
    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Integer>> getUnreadCount(
            @AuthenticationPrincipal Long userId) {
        int count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * 알림 읽음 처리
     */
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal Long userId) {
        notificationService.markAsRead(notificationId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 모든 알림 읽음 처리
     */
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @AuthenticationPrincipal Long userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 알림 삭제
     */
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal Long userId) {
        notificationService.deleteNotification(notificationId, userId);
        return ResponseEntity.ok().build();
    }
}
