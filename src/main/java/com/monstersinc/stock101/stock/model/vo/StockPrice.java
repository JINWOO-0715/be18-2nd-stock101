package com.monstersinc.stock101.stock.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 주식 시세 정보 VO
 * stock_prices 테이블과 매핑
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockPrice {
    
    private Long id;
    private Long stockId;
    private LocalDate datetime;
    private Double stckOprc;   // 시가
    private Double stckHgpr;   // 고가
    private Double stckLwpr;   // 저가
    private Double stckClpr;   // 종가
    private Double acmlVol;    // 누적 거래량
    private Double acmlTrPbmn; // 누적 거래대금
    
    /**
     * KIS API 응답에서 엔티티로 변환하는 팩토리 메서드
     */
    public static StockPrice fromKisResponse(Long stockId, KisPriceData data) {
        return StockPrice.builder()
                .stockId(stockId)
                .datetime(LocalDate.parse(data.getStckBsopDate(), java.time.format.DateTimeFormatter.BASIC_ISO_DATE))
                .stckOprc(parseToDouble(data.getStckOprc()))
                .stckHgpr(parseToDouble(data.getStckHgpr()))
                .stckLwpr(parseToDouble(data.getStckLwpr()))
                .stckClpr(parseToDouble(data.getStckClpr()))
                .acmlVol(parseToDouble(data.getAcmlVol()))
                .acmlTrPbmn(parseToDouble(data.getAcmlTrPbmn()))
                .build();
    }
    
    private static Double parseToDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.replaceAll("[^0-9.-]", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
    
    /**
     * KIS API 응답 데이터 DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KisPriceData {
        private String stckBsopDate;  // 기준일자
        private String stckOprc;      // 시가
        private String stckHgpr;      // 고가
        private String stckLwpr;      // 저가
        private String stckClpr;      // 종가
        private String acmlVol;       // 누적 거래량
        private String acmlTrPbmn;    // 누적 거래대금
    }
}
