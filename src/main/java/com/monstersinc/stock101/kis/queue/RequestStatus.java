package com.monstersinc.stock101.kis.queue;

/**
 * 요청 처리 상태
 */
public enum RequestStatus {
    QUEUED,      // Queue 대기 중
    PROCESSING,  // 처리 중
    COMPLETED,   // 완료
    FAILED,      // 실패
    NOT_FOUND    // 없음 (만료되었거나 존재하지 않음)
}
