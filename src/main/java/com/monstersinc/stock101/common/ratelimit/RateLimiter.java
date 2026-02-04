package com.monstersinc.stock101.common.ratelimit;

/**
 * Rate Limiter 인터페이스
 * Token Bucket 알고리즘 기반
 */
public interface RateLimiter {

    /**
     * 토큰 획득 시도 (블로킹)
     * 최대 대기 시간까지 토큰을 얻으려고 시도
     *
     * @param key 제한 대상 키 (예: "KIS_API")
     * @param maxWaitMs 최대 대기 시간 (밀리초)
     * @return 획득 성공 여부
     * @throws InterruptedException 대기 중 인터럽트 발생 시
     */
    boolean acquire(String key, long maxWaitMs) throws InterruptedException;

    /**
     * 토큰 획득 시도 (논블로킹)
     * 즉시 토큰을 얻을 수 있으면 true, 아니면 false 반환
     *
     * @param key 제한 대상 키
     * @return 즉시 획득 가능하면 true, 아니면 false
     */
    boolean tryAcquire(String key);

    /**
     * 현재 가용 토큰 수 조회
     *
     * @param key 제한 대상 키
     * @return 현재 사용 가능한 토큰 수
     */
    int getAvailableTokens(String key);
}
