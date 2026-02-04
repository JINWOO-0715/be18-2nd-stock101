package com.monstersinc.stock101.common.ratelimit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Rate Limiter 설정
 */
@Configuration
public class RateLimitConfig {

    /**
     * API별 Rate Limit 설정
     */
    @Bean
    public Map<String, RateLimitProperties> rateLimitPropertiesMap() {
        Map<String, RateLimitProperties> config = new HashMap<>();

        // KIS API: 1초에 2회
        config.put("KIS_API", RateLimitProperties.builder()
                .capacity(2)           // 버킷 크기: 2개 (동시에 2개까지 버스트 가능)
                .refillRate(2.0)       // 초당 2개 토큰 생성
                .maxWaitMs(5000)       // 최대 5초 대기
                .build());

        // DART API: 1초에 5회 (보수적 설정)
        config.put("DART_API", RateLimitProperties.builder()
                .capacity(5)
                .refillRate(5.0)
                .maxWaitMs(3000)
                .build());

        return config;
    }
}
