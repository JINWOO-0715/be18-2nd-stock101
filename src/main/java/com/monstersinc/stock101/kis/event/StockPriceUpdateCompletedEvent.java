package com.monstersinc.stock101.kis.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 주식 시세 업데이트 완료 이벤트
 */
@Getter
public class StockPriceUpdateCompletedEvent extends ApplicationEvent {

    private final String requestId;
    private final String stockCode;
    private final int savedCount;

    public StockPriceUpdateCompletedEvent(Object source, String requestId, String stockCode, int savedCount) {
        super(source);
        this.requestId = requestId;
        this.stockCode = stockCode;
        this.savedCount = savedCount;
    }

    public static StockPriceUpdateCompletedEvent of(String requestId, String stockCode, int savedCount) {
        return new StockPriceUpdateCompletedEvent("StockPriceUpdateWorker", requestId, stockCode, savedCount);
    }
}
