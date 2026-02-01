package com.monstersinc.stock101.notification.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE 이벤트 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationEvent {

    /**
     * 이벤트 타입: "new", "count_update"
     */
    private String type;

    /**
     * 알림 데이터 (type이 "new"일 때)
     */
    private NotificationData notification;

    /**
     * 미읽음 알림 개수 (type이 "count_update"일 때)
     */
    private Integer count;
}
