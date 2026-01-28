package com.monstersinc.stock101.dart.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DART 공시정보 엔티티
 * dart_disclosure_entry 테이블과 매핑
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DartDisclosureEntry {
    
    private Long id;                    // PK
    private String rceptNo;             // 접수번호 (DART에서 제공)
    private String corpCode;            // 회사 고유번호 (8자리)
    private String corpName;            // 정식 회사명
    private String reportNm;            // 보고서명
    private LocalDate receptionDate;    // 접수일자
    private LocalDateTime createdAt;    // 데이터 생성 시간
    private String reportType; // 리포트 타입
    
/**
     * DART API 응답 데이터를 엔티티로 변환하는 정적 팩토리 메서드
     */
    public static DartDisclosureEntry fromDartResponse(
            String rceptNo, String corpCode, String corpName,
            String reportNm, LocalDate receptionDate, String reportType) {
        return DartDisclosureEntry.builder()
                .rceptNo(rceptNo)
                .corpCode(corpCode)
                .corpName(corpName)
                .reportNm(reportNm)
                .receptionDate(receptionDate)
                .reportType(reportType)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
