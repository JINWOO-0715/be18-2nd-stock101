package com.monstersinc.stock101.kis.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * API 토큰 정보 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiToken {

    /** API 이름 (예: KIS) */
    private String apiName;

    /** 액세스 토큰 */
    private String accessToken;

    /** 토큰 타입 (예: Bearer) */
    private String tokenType;

    /** 토큰 발급 시각 */
    private LocalDateTime issuedAt;

    /** 토큰 만료 시각 */
    private LocalDateTime expiresAt;

    /** 마지막 수정 시각 */
    private LocalDateTime updatedAt;

    /**
     * 토큰이 유효한지 확인 (만료 1분 전까지 유효로 판단)
     */
    public boolean isValid() {
        if (accessToken == null || expiresAt == null) {
            return false;
        }
        return expiresAt.isAfter(LocalDateTime.now().plusMinutes(1));
    }
}
