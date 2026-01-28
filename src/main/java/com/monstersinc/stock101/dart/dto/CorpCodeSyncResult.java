package com.monstersinc.stock101.dart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DART 고유번호 동기화 결과 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorpCodeSyncResult {
    
    private boolean success;
    private String message;
    private int totalCount;       // 전체 회사 수
    private int listedCount;      // 상장회사 수
    private int updatedCount;     // Stock 테이블 업데이트 건수
    
    public static CorpCodeSyncResult success(String message, int totalCount, int listedCount, int updatedCount) {
        return CorpCodeSyncResult.builder()
                .success(true)
                .message(message)
                .totalCount(totalCount)
                .listedCount(listedCount)
                .updatedCount(updatedCount)
                .build();
    }
    
    public static CorpCodeSyncResult fail(String message) {
        return CorpCodeSyncResult.builder()
                .success(false)
                .message(message)
                .totalCount(0)
                .listedCount(0)
                .updatedCount(0)
                .build();
    }
}
