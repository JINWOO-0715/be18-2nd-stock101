package com.monstersinc.stock101.notification.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 알림 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {

    /**
     * 알림 고유 ID
     */
    private Long id;

    /**
     * 수신 사용자 ID
     */
    private Long userId;

    /**
     * 연관된 문서 ID (nullable)
     */
    private Long documentId;

    /**
     * 알림 내용
     */
    private String message;

    /**
     * 읽음 처리 여부 (0: 미읽음, 1: 읽음)
     */
    @Builder.Default
    private Boolean isRead = false;

    /**
     * 알림 생성 시간
     */
    private LocalDateTime createdAt;
}
