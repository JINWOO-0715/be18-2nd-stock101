package com.monstersinc.stock101.stock.model.dto;

import com.monstersinc.stock101.stock.model.vo.Stock;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class StocksCacheDto {
    private Long stockId;
    private String stockCode;
    private String name;
    private String  market;
    private Long marketCap;

    // 엔티티를 DTO로 변환하는 정적 메서드
    public static StocksCacheDto from(Stock stock) {
        return new StocksCacheDto(stock.getStockId(), stock.getStockCode(), stock.getName(),stock.getMarketType(), stock.getMarketCap());
    }
}