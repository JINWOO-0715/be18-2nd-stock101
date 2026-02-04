package com.monstersinc.stock101.kis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.monstersinc.stock101.kis.dto.KisCandleResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * KIS API 캐싱 서비스
 * 일봉 데이터를 Redis에 캐시하여 API 호출 최소화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KisApiCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String CACHE_KEY_PREFIX = "kis:candle:";
    private static final String FALLBACK_KEY_PREFIX = "kis:fallback:";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * 캐시 조회
     *
     * @param stockCode 종목코드
     * @param startDate 시작일
     * @param endDate   종료일
     * @return 캐시된 데이터 (없으면 empty)
     */
    public Optional<KisCandleResponse> getCachedCandleData(
            String stockCode, LocalDate startDate, LocalDate endDate) {
        try {
            String key = buildCacheKey(stockCode, startDate, endDate);
            String cached = redisTemplate.opsForValue().get(key);

            if (cached != null) {
                log.debug("✅ 캐시 HIT: {}", key);
                return Optional.of(objectMapper.readValue(cached, KisCandleResponse.class));
            }

            log.debug("❌ 캐시 MISS: {}", key);
            return Optional.empty();

        } catch (Exception e) {
            log.warn("캐시 조회 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 캐시 저장
     * - 오늘 데이터: TTL 1시간 (장중에 변동)
     * - 과거 데이터: TTL 7일 (불변이지만 메모리 관리)
     *
     * @param stockCode 종목코드
     * @param startDate 시작일
     * @param endDate   종료일
     * @param data      응답 데이터
     */
    public void cacheCandleData(
            String stockCode, LocalDate startDate, LocalDate endDate, KisCandleResponse data) {
        try {
            String key = buildCacheKey(stockCode, startDate, endDate);
            String json = objectMapper.writeValueAsString(data);

            // TTL 계산: 오늘이 포함되면 1시간, 과거 데이터는 7일
            Duration ttl = endDate.equals(LocalDate.now())
                    ? Duration.ofHours(1)
                    : Duration.ofDays(7);

            redisTemplate.opsForValue().set(key, json, ttl);

            // Fallback 캐시도 업데이트 (30일 TTL)
            saveFallbackCache(stockCode, data);

            log.debug("캐시 저장: key={}, ttl={}", key, ttl);

        } catch (Exception e) {
            log.warn("캐시 저장 실패 (서비스 계속): {}", e.getMessage());
        }
    }

    /**
     * Fallback 캐시 저장 (API 장애 시 사용)
     * 가장 최근에 성공한 데이터를 30일간 보관
     *
     * @param stockCode 종목코드
     * @param data      응답 데이터
     */
    private void saveFallbackCache(String stockCode, KisCandleResponse data) {
        try {
            String key = FALLBACK_KEY_PREFIX + stockCode;
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(key, json, Duration.ofDays(30));
            log.debug("Fallback 캐시 저장: stockCode={}", stockCode);
        } catch (Exception e) {
            log.warn("Fallback 캐시 저장 실패: {}", e.getMessage());
        }
    }

    /**
     * Fallback 캐시 조회 (API 장애 시 사용)
     *
     * @param stockCode 종목코드
     * @return Fallback 데이터 (없으면 empty)
     */
    public Optional<KisCandleResponse> getFallbackCache(String stockCode) {
        try {
            String key = FALLBACK_KEY_PREFIX + stockCode;
            String cached = redisTemplate.opsForValue().get(key);

            if (cached != null) {
                log.info("⚠️ Fallback 캐시 사용: stockCode={}", stockCode);
                return Optional.of(objectMapper.readValue(cached, KisCandleResponse.class));
            }

            return Optional.empty();

        } catch (Exception e) {
            log.error("Fallback 캐시 조회 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 캐시 키 생성
     */
    private String buildCacheKey(String stockCode, LocalDate startDate, LocalDate endDate) {
        return String.format("%s%s:%s:%s",
                CACHE_KEY_PREFIX,
                stockCode,
                startDate.format(DATE_FORMATTER),
                endDate.format(DATE_FORMATTER)
        );
    }

    /**
     * 특정 종목의 모든 캐시 삭제 (MST 업데이트 시 사용)
     */
    public void evictAllCacheForStock(String stockCode) {
        try {
            String pattern = CACHE_KEY_PREFIX + stockCode + ":*";
            redisTemplate.keys(pattern).forEach(redisTemplate::delete);
            log.info("종목 {} 캐시 전체 삭제", stockCode);
        } catch (Exception e) {
            log.warn("캐시 삭제 실패: {}", e.getMessage());
        }
    }
}
