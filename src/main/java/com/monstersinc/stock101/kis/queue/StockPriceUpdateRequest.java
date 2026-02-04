package com.monstersinc.stock101.kis.queue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 주식 시세 업데이트 요청 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockPriceUpdateRequest {

    /**
     * 요청 추적용 UUID
     */
    private String requestId;

    /**
     * 종목 ID
     */
    private Long stockId;

    /**
     * 종목 코드
     */
    private String stockCode;

    /**
     * 시작 날짜
     */
    private LocalDate startDate;

    /**
     * 종료 날짜
     */
    private LocalDate endDate;

    /**
     * 우선순위
     */
    private RequestPriority priority;

    /**
     * 생성 시각
     */
    private LocalDateTime createdAt;

    /**
     * 요청 우선순위
     */
    public enum RequestPriority {
        HIGH(1),   // 일반 조회 (1-2회 API 호출) - 즉시 처리
        LOW(2);    // 대량 조회 (3회 이상) - Queue로 처리

        private final int value;

        RequestPriority(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * 필요한 API 호출 횟수 추정
     * KIS API는 100건씩 반환하므로 일수 / 100
     */
    public int estimateApiCalls() {
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        return (int) Math.ceil(days / 100.0);
    }

    /**
     * 우선순위 자동 판단
     * API 호출 1-2회: HIGH (동기 처리)
     * API 호출 3회 이상: LOW (비동기 Queue 처리)
     */
    public static RequestPriority decidePriority(LocalDate startDate, LocalDate endDate) {
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        int apiCalls = (int) Math.ceil(days / 100.0);
        return apiCalls <= 2 ? RequestPriority.HIGH : RequestPriority.LOW;
    }
}
