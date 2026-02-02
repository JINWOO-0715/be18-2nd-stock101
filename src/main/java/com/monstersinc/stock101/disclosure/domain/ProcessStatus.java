package com.monstersinc.stock101.disclosure.domain;

/**
 * 공시보고서 처리 상태
 * 각 상태는 처리 순서를 나타내는 order 값을 가지며, 순서 비교가 가능합니다.
 */
public enum ProcessStatus {
    PENDING(0),                     // 대기 중
    EXTRACTING(1),                  // PDF 추출 중
    EXTRACTION_FAILED(1),           // 추출 실패
    CHUNKING(2),                    // 청킹 중
    CHUNKING_FAILED(2),             // 청킹 실패
    EMBEDDING(3),                   // 임베딩 중
    EMBEDDING_FAILED(3),            // 임베딩 실패
    GENERATING_REPORT(4),           // 리포트 생성 중
    REPORT_FAILED(4),               // 리포트 생성 실패
    COMPLETED(5),                   // 완료
    FAILED(99);                     // 일반 실패

    private final int order;

    ProcessStatus(int order) {
        this.order = order;
    }

    public int getOrder() {
        return order;
    }

    /**
     * 현재 상태가 지정된 단계보다 이전이거나 같은지 확인
     */
    public boolean isBeforeOrEqual(ProcessStatus other) {
        return this.order <= other.order;
    }

    /**
     * 실패 상태인지 확인
     */
    public boolean isFailed() {
        return this == EXTRACTION_FAILED ||
               this == CHUNKING_FAILED ||
               this == EMBEDDING_FAILED ||
               this == REPORT_FAILED ||
               this == FAILED;
    }

    /**
     * 문자열로부터 ProcessStatus를 파싱 (DB 호환성을 위해)
     */
    public static ProcessStatus fromString(String status) {
        if (status == null) {
            return PENDING;
        }
        try {
            return ProcessStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return PENDING;
        }
    }
}
