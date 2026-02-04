package com.monstersinc.stock101.common.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * Redis 기반 Rate Limiter 구현
 * Token Bucket 알고리즘 사용
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final Map<String, RateLimitProperties> rateLimitPropertiesMap;

    private static final String KEY_PREFIX = "ratelimit:";

    /**
     * Lua 스크립트로 원자성 보장
     * Token Bucket 알고리즘 구현
     */
    private static final String LUA_SCRIPT = """
        local key = KEYS[1]
        local capacity = tonumber(ARGV[1])
        local refill_rate = tonumber(ARGV[2])
        local now = tonumber(ARGV[3])

        -- 현재 버킷 상태 조회
        local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
        local tokens = tonumber(bucket[1])
        local last_refill = tonumber(bucket[2])

        -- 초기화 (첫 호출)
        if tokens == nil then
            tokens = capacity
            last_refill = now
        end

        -- 경과 시간 계산 및 토큰 리필
        local elapsed = now - last_refill
        local refilled = math.floor(elapsed * refill_rate)

        if refilled > 0 then
            tokens = math.min(capacity, tokens + refilled)
            last_refill = now
        end

        -- 토큰 소비 시도
        if tokens >= 1 then
            tokens = tokens - 1
            redis.call('HMSET', key, 'tokens', tokens, 'last_refill', last_refill)
            redis.call('EXPIRE', key, 60)  -- 60초 후 자동 삭제
            return 1  -- 성공
        else
            return 0  -- 토큰 부족
        end
        """;

    /**
     * Lua 스크립트: 현재 토큰 수 조회
     */
    private static final String LUA_SCRIPT_GET_TOKENS = """
        local key = KEYS[1]
        local capacity = tonumber(ARGV[1])
        local refill_rate = tonumber(ARGV[2])
        local now = tonumber(ARGV[3])

        local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
        local tokens = tonumber(bucket[1])
        local last_refill = tonumber(bucket[2])

        if tokens == nil then
            return capacity
        end

        local elapsed = now - last_refill
        local refilled = math.floor(elapsed * refill_rate)

        if refilled > 0 then
            tokens = math.min(capacity, tokens + refilled)
        end

        return math.floor(tokens)
        """;

    @Override
    public boolean acquire(String key, long maxWaitMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + maxWaitMs;

        while (System.currentTimeMillis() < deadline) {
            if (tryAcquire(key)) {
                return true;
            }

            // 50ms 대기 후 재시도
            Thread.sleep(50);
        }

        log.warn("Rate Limit 획득 실패: key={}, maxWaitMs={}", key, maxWaitMs);
        return false;
    }

    @Override
    public boolean tryAcquire(String key) {
        try {
            RateLimitProperties properties = getProperties(key);
            String redisKey = KEY_PREFIX + key;

            Long result = redisTemplate.execute(
                new DefaultRedisScript<>(LUA_SCRIPT, Long.class),
                Collections.singletonList(redisKey),
                String.valueOf(properties.getCapacity()),
                String.valueOf(properties.getRefillRate()),
                String.valueOf(System.currentTimeMillis() / 1000.0)
            );

            boolean acquired = result != null && result == 1;

            if (!acquired) {
                log.debug("Rate Limit 토큰 부족: key={}, available={}", key, getAvailableTokens(key));
            }

            return acquired;

        } catch (Exception e) {
            log.error("Rate Limiter 오류 발생, 기본 허용 처리: key={}, error={}", key, e.getMessage());
            // Redis 장애 시 서비스 중단 방지 - 기본적으로 허용
            return true;
        }
    }

    @Override
    public int getAvailableTokens(String key) {
        try {
            RateLimitProperties properties = getProperties(key);
            String redisKey = KEY_PREFIX + key;

            Long tokens = redisTemplate.execute(
                new DefaultRedisScript<>(LUA_SCRIPT_GET_TOKENS, Long.class),
                Collections.singletonList(redisKey),
                String.valueOf(properties.getCapacity()),
                String.valueOf(properties.getRefillRate()),
                String.valueOf(System.currentTimeMillis() / 1000.0)
            );

            return tokens != null ? tokens.intValue() : properties.getCapacity();

        } catch (Exception e) {
            log.error("토큰 수 조회 실패: key={}, error={}", key, e.getMessage());
            return 0;
        }
    }

    /**
     * 설정 조회 (설정이 없으면 기본값)
     */
    private RateLimitProperties getProperties(String key) {
        return rateLimitPropertiesMap.getOrDefault(key, RateLimitProperties.defaultConfig());
    }
}
