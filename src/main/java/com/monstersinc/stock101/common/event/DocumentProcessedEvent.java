package com.monstersinc.stock101.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 문서 처리 완료 이벤트
 * 비동기 문서 처리가 완료되면 발행되는 도메인 이벤트
 */
@Getter
public class DocumentProcessedEvent extends ApplicationEvent {

    /**
     * 문서 소스 ID
     */
    private final Long sourceId;

    /**
     * 사용자 ID
     */
    private final Long userId;

    /**
     * 회사명
     */
    private final String companyName;

    /**
     * 처리 상태 (COMPLETED, FAILED 등)
     */
    private final String status;

    public DocumentProcessedEvent(Object source, Long sourceId, Long userId, String companyName, String status) {
        super(source);
        this.sourceId = sourceId;
        this.userId = userId;
        this.companyName = companyName;
        this.status = status;
    }

    /**
     * 처리 성공 이벤트 생성 팩토리 메서드
     */
    public static DocumentProcessedEvent success(Object source, Long sourceId, Long userId, String companyName) {
        return new DocumentProcessedEvent(source, sourceId, userId, companyName, "COMPLETED");
    }

    /**
     * 처리 실패 이벤트 생성 팩토리 메서드
     */
    public static DocumentProcessedEvent failure(Object source, Long sourceId, Long userId, String companyName) {
        return new DocumentProcessedEvent(source, sourceId, userId, companyName, "FAILED");
    }

    /**
     * 성공 여부 확인
     */
    public boolean isSuccess() {
        return "COMPLETED".equals(status);
    }
}
