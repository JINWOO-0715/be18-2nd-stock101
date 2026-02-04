package com.monstersinc.stock101.kis.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 주식 시세 업데이트 응답 DTO
 */
@Getter
@Builder
public class UpdateResponse {

    /**
     * 비동기 처리 여부
     */
    private boolean isAsync;

    /**
     * 요청 ID (비동기일 경우)
     */
    private String requestId;

    /**
     * 저장된 데이터 수 (동기일 경우)
     */
    private Integer savedCount;

    /**
     * 예상 API 호출 횟수
     */
    private Integer estimatedApiCalls;

    /**
     * 응답 메시지
     */
    private String message;

    /**
     * 동기 처리 완료 응답
     */
    public static UpdateResponse completedSync(int savedCount) {
        return UpdateResponse.builder()
                .isAsync(false)
                .savedCount(savedCount)
                .message("동기 처리 완료")
                .build();
    }

    /**
     * 비동기 Queue 추가 응답
     */
    public static UpdateResponse queuedAsync(String requestId, int estimatedApiCalls) {
        return UpdateResponse.builder()
                .isAsync(true)
                .requestId(requestId)
                .estimatedApiCalls(estimatedApiCalls)
                .message("대량 조회 요청이 Queue에 추가되었습니다. requestId로 진행 상황을 확인하세요.")
                .build();
    }

    /**
     * 이미 최신 상태
     */
    public static UpdateResponse alreadyUpToDate() {
        return UpdateResponse.builder()
                .isAsync(false)
                .savedCount(0)
                .message("이미 최신 상태입니다.")
                .build();
    }
}
