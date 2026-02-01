package com.monstersinc.stock101.notification.service;

import com.monstersinc.stock101.notification.model.dto.NotificationData;
import com.monstersinc.stock101.notification.model.dto.NotificationDto;
import com.monstersinc.stock101.notification.model.dto.NotificationEvent;
import com.monstersinc.stock101.notification.model.dto.NotificationResponseDto;
import com.monstersinc.stock101.notification.model.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 알림 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final SseEmitterService sseEmitterService;

    /**
     * 알림 생성 및 SSE 실시간 전송
     */
    @Transactional
    public void createNotification(Long userId, Long documentId, String message) {
        // 1. DB에 알림 저장
        NotificationDto notification = NotificationDto.builder()
                .userId(userId)
                .documentId(documentId)
                .message(message)
                .isRead(false)
                .build();

        notificationMapper.insertNotification(notification);
        log.info("알림 생성 완료: userId={}, documentId={}, message={}", userId, documentId, message);

        // 2. SSE로 실시간 전송
        sendNotificationViaSSE(userId, notification);

        // 3. 미읽음 개수 업데이트 전송
        int unreadCount = notificationMapper.countUnreadByUserId(userId);
        sseEmitterService.sendCountUpdate(userId, unreadCount);
    }

    /**
     * SSE로 알림 전송
     */
    private void sendNotificationViaSSE(Long userId, NotificationDto notification) {
        try {
            // NotificationData 생성
            NotificationData notificationData = NotificationData.builder()
                    .id(notification.getId())
                    .message(notification.getMessage())
                    .targetUrl(notification.getDocumentId() != null
                            ? "/report/" + notification.getDocumentId()
                            : null)
                    .read(notification.getIsRead())
                    .createdAt(notification.getCreatedAt())
                    .build();

            // NotificationEvent 생성
            NotificationEvent event = NotificationEvent.builder()
                    .type("new")
                    .notification(notificationData)
                    .build();

            // SSE 전송
            sseEmitterService.sendNotification(userId, event);
        } catch (Exception e) {
            log.error("SSE 알림 전송 실패: userId={}", userId, e);
            // SSE 전송 실패해도 DB 저장은 성공했으므로 예외를 던지지 않음
        }
    }

    /**
     * 문서 처리 완료 알림 생성
     */
    @Transactional
    public void createDocumentCompletedNotification(Long userId, Long documentId, String companyName) {
        String message = String.format("'%s' 문서 분석이 완료되었습니다.", companyName);
        createNotification(userId, documentId, message);
    }

    /**
     * 사용자별 알림 목록 조회
     */
    @Transactional(readOnly = true)
    public List<NotificationResponseDto> getNotificationsByUserId(Long userId) {
        List<NotificationDto> notifications = notificationMapper.findByUserId(userId);
        return notifications.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * 사용자별 미읽음 알림 목록 조회
     */
    @Transactional(readOnly = true)
    public List<NotificationResponseDto> getUnreadNotificationsByUserId(Long userId) {
        List<NotificationDto> notifications = notificationMapper.findUnreadByUserId(userId);
        return notifications.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * 알림 읽음 처리 및 SSE 미읽음 개수 업데이트
     */
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        NotificationDto notification = notificationMapper.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다."));

        if (!notification.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인의 알림만 읽음 처리할 수 있습니다.");
        }

        notificationMapper.markAsRead(notificationId);
        log.info("알림 읽음 처리: notificationId={}, userId={}", notificationId, userId);

        // SSE로 미읽음 개수 업데이트 전송
        int unreadCount = notificationMapper.countUnreadByUserId(userId);
        sseEmitterService.sendCountUpdate(userId, unreadCount);
    }

    /**
     * 사용자의 모든 알림 읽음 처리 및 SSE 미읽음 개수 업데이트
     */
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationMapper.markAllAsReadByUserId(userId);
        log.info("모든 알림 읽음 처리: userId={}", userId);

        // SSE로 미읽음 개수 업데이트 전송 (0으로)
        sseEmitterService.sendCountUpdate(userId, 0);
    }

    /**
     * 사용자의 미읽음 알림 개수 조회
     */
    @Transactional(readOnly = true)
    public int getUnreadCount(Long userId) {
        return notificationMapper.countUnreadByUserId(userId);
    }

    /**
     * 알림 삭제
     */
    @Transactional
    public void deleteNotification(Long notificationId, Long userId) {
        NotificationDto notification = notificationMapper.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다."));

        if (!notification.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인의 알림만 삭제할 수 있습니다.");
        }

        notificationMapper.deleteById(notificationId);
        log.info("알림 삭제: notificationId={}, userId={}", notificationId, userId);
    }

    /**
     * DTO 변환
     */
    private NotificationResponseDto convertToResponseDto(NotificationDto dto) {
        return NotificationResponseDto.builder()
                .id(dto.getId())
                .documentId(dto.getDocumentId())
                .message(dto.getMessage())
                .isRead(dto.getIsRead())
                .createdAt(dto.getCreatedAt())
                .build();
    }
}
