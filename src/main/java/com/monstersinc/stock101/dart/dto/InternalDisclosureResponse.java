package com.monstersinc.stock101.dart.dto;

import com.monstersinc.stock101.dart.domain.DartDisclosureEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 내부 공시정보 조회 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalDisclosureResponse {
    private boolean success;
    private String message;
    private String stockName;
    private String corpCode;
    private int count;
    private List<DartDisclosureEntry> disclosures;

    public static InternalDisclosureResponse success(String stockName, String corpCode,
            List<DartDisclosureEntry> disclosures) {
        return InternalDisclosureResponse.builder()
                .success(true)
                .message("조회 성공")
                .stockName(stockName)
                .corpCode(corpCode)
                .disclosures(disclosures)
                .count((disclosures != null) ? disclosures.size() : 0)
                .build();
    }

    public static InternalDisclosureResponse fail(String message) {
        return InternalDisclosureResponse.builder()
                .success(false)
                .message(message)
                .count(0)
                .build();
    }
}
