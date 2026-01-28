package com.monstersinc.stock101.dart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 공시정보 초기화 결과 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisclosureInitResult {
    private boolean success;
    private String message;
    private String stockName;
    private String corpCode;
    private int totalDisclosures;
    private int savedDisclosures;

    public static DisclosureInitResult success(String message, String stockName, String corpCode,
            int totalDisclosures, int savedDisclosures) {
        return DisclosureInitResult.builder()
                .success(true)
                .message(message)
                .stockName(stockName)
                .corpCode(corpCode)
                .totalDisclosures(totalDisclosures)
                .savedDisclosures(savedDisclosures)
                .build();
    }

    public static DisclosureInitResult fail(String message) {
        return DisclosureInitResult.builder()
                .success(false)
                .message(message)
                .totalDisclosures(0)
                .savedDisclosures(0)
                .build();
    }
}
