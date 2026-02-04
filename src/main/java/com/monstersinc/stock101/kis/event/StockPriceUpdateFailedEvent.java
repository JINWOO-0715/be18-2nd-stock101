package com.monstersinc.stock101.kis.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 주식 시세 업데이트 실패 이벤트
 */
@Getter
public class StockPriceUpdateFailedEvent extends ApplicationEvent {

    private final String requestId;
    private final String stockCode;
    private final String errorMessage;

    public StockPriceUpdateFailedEvent(Object source, String requestId, String stockCode, String errorMessage) {
        super(source);
        this.requestId = requestId;
        this.stockCode = stockCode;
        this.errorMessage = errorMessage;
    }

    public static StockPriceUpdateFailedEvent of(String requestId, String stockCode, String errorMessage) {
        return new StockPriceUpdateFailedEvent("StockPriceUpdateWorker", requestId, stockCode, errorMessage);
    }
}
