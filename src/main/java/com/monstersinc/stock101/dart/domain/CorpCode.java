package com.monstersinc.stock101.dart.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DART 회사 고유번호 정보
 * corpcode.xml 파싱 결과를 담는 도메인 클래스
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorpCode {
    
    private String corpCode;      // 고유번호 (8자리)
    private String corpName;      // 정식회사명칭
    private String corpEngName;   // 영문정식회사명칭
    private String stockCode;     // 종목코드 (상장회사인 경우 6자리, 비상장: 공백)
    private String modifyDate;    // 최종변경일자 (YYYYMMDD)
    
    /**
     * 상장회사 여부 확인
     * @return 종목코드가 있으면 true
     */
    public boolean isListed() {
        return stockCode != null && !stockCode.trim().isEmpty();
    }
}
