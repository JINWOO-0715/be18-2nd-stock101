package com.monstersinc.stock101.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 한국투자증권 주식 기간별 시세 API 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KisCandleResponse {
    
    @JsonProperty("rt_cd")
    private String rtCd;  // 성공 여부
    
    @JsonProperty("msg_cd")
    private String msgCd;
    
    @JsonProperty("msg1")
    private String msg1;
    
    @JsonProperty("output1")
    private OutputHeader output1;
    
    @JsonProperty("output2")
    private List<KisCandleData> output2;  // 일봉 데이터 리스트
    
    /**
     * 응답 헤더 정보
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutputHeader {
        @JsonProperty("EXYM")
        private String exym;  // 
        
        @JsonProperty("HISBASE")
        private String hisbase;
        
        @JsonProperty("BASDT")
        private String basdt;
        
        @JsonProperty("PRDY_VRSS")
        private String prdyVrss;
        
        @JsonProperty("PRDY_VRSS_SIGN")
        private String prdyVrssSign;
        
        @JsonProperty("PRDY_CTRT")
        private String prdyCtrt;
        
        @JsonProperty("STCK_PRPR")
        private String stckPrpr;
    }
    
    /**
     * 일봉 데이터
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KisCandleData {
        @JsonProperty("stck_bsop_date")
        private String stckBsopDate;  // 기준일자 (YYYYMMDD)
        
        @JsonProperty("stck_oprc")
        private String stckOprc;  // 시가
        
        @JsonProperty("stck_hgpr")
        private String stckHgpr;  // 고가
        
        @JsonProperty("stck_lwpr")
        private String stckLwpr;  // 저가
        
        @JsonProperty("stck_clpr")
        private String stckClpr;  // 종가
        
        @JsonProperty("acml_vol")
        private String acmlVol;  // 누적 거래량
        
        @JsonProperty("acml_tr_pbmn")
        private String acmlTrPbmn;  // 누적 거래대금
        
        @JsonProperty("flng_cls_code")
        private String flngClsCode;
        
        @JsonProperty("prdy_vrss_sign")
        private String prdyVrssSign;
        
        @JsonProperty("prdy_vrss")
        private String prdyVrss;
        
        @JsonProperty("prdy_ctrt")
        private String prdyCtrt;
    }
    
    public boolean isSuccess() {
        return "0".equals(rtCd);
    }
}
