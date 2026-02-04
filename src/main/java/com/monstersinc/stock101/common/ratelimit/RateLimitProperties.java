package com.monstersinc.stock101.common.ratelimit;

import lombok.Builder;
import lombok.Getter;

/**
 * Rate Limit 설정 속성
 */
@Getter
@Builder
public class RateLimitProperties {

    /**
     * 버킷 최대 용량 (최대 토큰 수)
     */
    private final int capacity;

    /**
     * 토큰 리필 속도 (초당 생성되는 토큰 수)
     */
    private final double refillRate;

    /**
     * 토큰 획득 실패 시 최대 대기 시간 (밀리초)
     */
    private final long maxWaitMs;

    /**
     * 기본 설정 (1초에 1회, 최대 1개 토큰)
     */
    public static RateLimitProperties defaultConfig() {
        return RateLimitProperties.builder()
                .capacity(1)
                .refillRate(1.0)
                .maxWaitMs(5000)
                .build();
    }
}
