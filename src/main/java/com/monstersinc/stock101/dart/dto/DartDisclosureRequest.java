package com.monstersinc.stock101.dart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DART 공시 검색 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DartDisclosureRequest {
    private String corpCode;    // 고유번호 (8자리)
    private String beginDate;   // 시작일 (YYYYMMDD)
    private String endDate;     // 종료일 (YYYYMMDD)
    private String lastReportNumber;  // 최종보고서 검색여부 (Y/N)
    private String reportType;  // 보고서 유형 (A: 정기공시, B: 주요사항보고, C: 발행공시, D: 지분공시, E: 기타공시, F: 외부감사관련, G: 펀드공시, H: 자산유동화, I: 거래소공시, J: 공정위공시)
    private Integer pageNo;     // 페이지 번호 (기본값: 1)
    private Integer pageCount;  // 페이지당 건수 (기본값: 10, 최대: 100)
}
