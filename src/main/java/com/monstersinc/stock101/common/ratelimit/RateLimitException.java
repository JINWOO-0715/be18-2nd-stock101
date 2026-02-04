package com.monstersinc.stock101.common.ratelimit;

import lombok.Getter;

/**
 * Rate Limit 초과 예외
 */
@Getter
public class RateLimitException extends RuntimeException {

    private final String apiType;
    private final long waitTimeMs;

    public RateLimitException(String apiType, long waitTimeMs) {
        super(String.format("%s API Rate Limit 초과, %dms 후 재시도 가능", apiType, waitTimeMs));
        this.apiType = apiType;
        this.waitTimeMs = waitTimeMs;
    }

    public RateLimitException(String apiType) {
        this(apiType, 1000);
    }
}
