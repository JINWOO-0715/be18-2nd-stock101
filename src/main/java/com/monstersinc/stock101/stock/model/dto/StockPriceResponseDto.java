package com.monstersinc.stock101.stock.model.dto;

import com.monstersinc.stock101.stock.model.vo.StockPrice;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 일봉 데이터 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockPriceResponseDto {

    private String stockCode;
    private String stockName;
    private LocalDate lastUpdated;
    private List<DailyPrice> prices;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyPrice {
        private LocalDate date;
        private Double open;      // 시가
        private Double high;      // 고가
        private Double low;       // 저가
        private Double close;     // 종가
        private Double volume;    // 거래량
        private Double tradingValue; // 거래대금

        public static DailyPrice from(StockPrice stockPrice) {
            return DailyPrice.builder()
                    .date(stockPrice.getDatetime())
                    .open(stockPrice.getStckOprc())
                    .high(stockPrice.getStckHgpr())
                    .low(stockPrice.getStckLwpr())
                    .close(stockPrice.getStckClpr())
                    .volume(stockPrice.getAcmlVol())
                    .tradingValue(stockPrice.getAcmlTrPbmn())
                    .build();
        }
    }

    public static StockPriceResponseDto of(String stockCode, String stockName, 
                                            LocalDate lastUpdated, List<StockPrice> prices) {
        return StockPriceResponseDto.builder()
                .stockCode(stockCode)
                .stockName(stockName)
                .lastUpdated(lastUpdated)
                .prices(prices.stream()
                        .map(DailyPrice::from)
                        .collect(Collectors.toList()))
                .build();
    }
}
