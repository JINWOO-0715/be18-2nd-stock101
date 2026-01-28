package com.monstersinc.stock101.dart.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DartDisclosureResponse {

    private String status;
    private String message;

    @JsonProperty("total_count")
    private int totalCount;

    @JsonProperty("total_page")
    private int totalPage;

    @JsonProperty("page_no")
    private int pageNo;

    @JsonProperty("page_count")
    private int pageCount;

    private List<DartDisclosure> list;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DartDisclosure {
        @JsonProperty("corp_code")
        private String corpCode;

        @JsonProperty("corp_name")
        private String corpName;

        @JsonProperty("report_nm")
        private String reportNm;

        @JsonProperty("rcept_no")
        private String rceptNo;

        @JsonProperty("rcept_dt")
        private String rceptDt;

        // 수동 세팅용 필드
        private String pblntfTy;
    }
}