package com.monstersinc.stock101.dart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.monstersinc.stock101.dart.dto.DartDisclosureResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * DART API 캐싱 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DartApiCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String FALLBACK_KEY_PREFIX = "dart:fallback:";

    /**
     * Fallback 캐시 저장 (API 장애 시 사용)
     * 가장 최근에 성공한 데이터를 7일간 보관
     *
     * @param corpCode   기업 고유번호
     * @param reportType 공시 타입
     * @param data       응답 데이터
     */
    public void saveFallbackCache(String corpCode, String reportType, DartDisclosureResponse data) {
        try {
            String key = buildFallbackKey(corpCode, reportType);
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(key, json, Duration.ofDays(7));
            log.debug("DART Fallback 캐시 저장: corpCode={}, reportType={}", corpCode, reportType);
        } catch (Exception e) {
            log.warn("DART Fallback 캐시 저장 실패: {}", e.getMessage());
        }
    }

    /**
     * Fallback 캐시 조회 (API 장애 시 사용)
     *
     * @param corpCode   기업 고유번호
     * @param reportType 공시 타입
     * @return Fallback 데이터 (없으면 empty)
     */
    public Optional<DartDisclosureResponse> getFallbackCache(String corpCode, String reportType) {
        try {
            String key = buildFallbackKey(corpCode, reportType);
            String cached = redisTemplate.opsForValue().get(key);

            if (cached != null) {
                log.info("⚠️ DART Fallback 캐시 사용: corpCode={}, reportType={}", corpCode, reportType);
                return Optional.of(objectMapper.readValue(cached, DartDisclosureResponse.class));
            }

            return Optional.empty();

        } catch (Exception e) {
            log.error("DART Fallback 캐시 조회 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Fallback 캐시 키 생성
     */
    private String buildFallbackKey(String corpCode, String reportType) {
        return String.format("%s%s:%s", FALLBACK_KEY_PREFIX, corpCode, reportType != null ? reportType : "all");
    }
}
