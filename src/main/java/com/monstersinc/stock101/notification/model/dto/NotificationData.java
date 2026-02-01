package com.monstersinc.stock101.notification.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * SSE 알림 데이터 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationData {

    /**
     * 알림 고유 ID
     */
    private Long id;

    /**
     * 알림 메시지
     */
    private String message;

    /**
     * 대상 URL (프론트엔드 라우팅용)
     */
    private String targetUrl;

    /**
     * 읽음 처리 여부
     */
    private Boolean read;

    /**
     * 알림 생성 시간
     */
    private LocalDateTime createdAt;
}
