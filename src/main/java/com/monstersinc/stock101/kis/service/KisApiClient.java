package com.monstersinc.stock101.kis.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.monstersinc.stock101.exception.message.GlobalExceptionMessage;
import com.monstersinc.stock101.exception.GlobalException;
import com.monstersinc.stock101.kis.dto.KisCandleResponse;
import com.monstersinc.stock101.kis.model.mapper.ApiTokenMapper;
import com.monstersinc.stock101.kis.model.vo.ApiToken;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;

/**
 * KIS API 호출 전담 클라이언트
 * 캐시 우선 조회 → API 호출 → Fallback 순서로 동작
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KisApiClient {

    private final RestTemplate restTemplate;
    private final KisApiCacheService cacheService;
    private final ApiTokenMapper apiTokenMapper;

    @Value("${apikey.kis-key}")
    private String kisKey;

    @Value("${apikey.kis-secret}")
    private String kisSecret;

    @Value("${kis.api-base-url:https://openapivts.koreainvestment.com:29443}")
    private String apiBaseUrl;

    private static final String API_NAME_KIS = "KIS";
    private static final String CANDLE_ENDPOINT = "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice";
    private static final String TOKEN_ENDPOINT = "/oauth2/tokenP";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TOKEN_EXPIRE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 일봉 데이터 조회 (캐시 우선)
     * 1. Redis 캐시 조회
     * 2. 캐시 없으면 API 호출
     * 3. API 실패 시 Fallback 캐시 사용
     *
     * @param stockCode 종목코드
     * @param startDate 시작일
     * @param endDate   종료일
     * @return 일봉 데이터
     */
    public KisCandleResponse fetchCandleData(
            String stockCode, LocalDate startDate, LocalDate endDate) {

        // 1. 캐시 조회
        Optional<KisCandleResponse> cached = cacheService.getCachedCandleData(stockCode, startDate, endDate);
        if (cached.isPresent()) {
            log.debug("캐시에서 반환: stockCode={}", stockCode);
            return cached.get();
        }

        // 2. API 호출
        try {
            KisCandleResponse response = callKisApi(stockCode, startDate, endDate);

            if (response != null && response.isSuccess()) {
                // 3. 캐시 저장
                cacheService.cacheCandleData(stockCode, startDate, endDate, response);
                return response;
            } else {
                log.warn("KIS API 실패 응답: {}", response != null ? response.getMsg1() : "null");
                // Fallback 시도
                return getFallbackOrThrow(stockCode);
            }

        } catch (Exception e) {
            log.error("KIS API 호출 실패: {}", e.getMessage(), e);
            // 4. Fallback 캐시 시도
            return getFallbackOrThrow(stockCode);
        }
    }

    /**
     * Fallback 캐시 조회, 없으면 예외 발생
     */
    private KisCandleResponse getFallbackOrThrow(String stockCode) {
        return cacheService.getFallbackCache(stockCode)
                .orElseThrow(() -> {
                    log.error("KIS API 실패 및 Fallback 캐시 없음: stockCode={}", stockCode);
                    return new GlobalException(GlobalExceptionMessage.STOCK_NOT_FOUND);
                });
    }

    /**
     * 실제 KIS API 호출
     */
    private KisCandleResponse callKisApi(String stockCode, LocalDate startDate, LocalDate endDate) {
        String url = UriComponentsBuilder.fromHttpUrl(apiBaseUrl + CANDLE_ENDPOINT)
                .queryParam("fid_cond_mrkt_div_code", "J")
                .queryParam("fid_input_iscd", stockCode)
                .queryParam("fid_input_date_1", startDate.format(DATE_FORMATTER))
                .queryParam("fid_input_date_2", endDate.format(DATE_FORMATTER))
                .queryParam("fid_period_div_code", "D")
                .queryParam("fid_org_adj_prc", "0")
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", "Bearer " + getAccessToken());
        headers.set("appKey", kisKey);
        headers.set("appSecret", kisSecret);
        headers.set("tr_id", "FHKST03010100");
        headers.set("custtype", "P");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        log.info("KIS API 호출: stockCode={}, start={}, end={}", stockCode, startDate, endDate);

        ResponseEntity<KisCandleResponse> responseEntity = restTemplate.exchange(
                url, HttpMethod.GET, entity, KisCandleResponse.class
        );

        KisCandleResponse response = responseEntity.getBody();

        if (response == null || !"0".equals(response.getRtCd())) {
            log.warn("KIS API 실패: {}", response != null ? response.getMsg1() : "null response");
        }

        return response;
    }

    /**
     * 액세스 토큰 조회 (DB 기반 + Double-Checked Locking)
     */
    private String getAccessToken() {
        // 1. DB 조회 (Lock 없이)
        ApiToken cachedToken = apiTokenMapper.selectByApiName(API_NAME_KIS);
        if (cachedToken != null && cachedToken.isValid()) {
            log.debug("DB에서 유효한 KIS 토큰 조회, 만료: {}", cachedToken.getExpiresAt());
            return cachedToken.getAccessToken();
        }

        // 2. 동기화 블록 진입
        synchronized (this) {
            // Double-Checked Locking
            cachedToken = apiTokenMapper.selectByApiName(API_NAME_KIS);
            if (cachedToken != null && cachedToken.isValid()) {
                log.debug("락 획득 후 유효한 토큰 발견, 만료: {}", cachedToken.getExpiresAt());
                return cachedToken.getAccessToken();
            }

            return refreshAndSaveToken();
        }
    }

    /**
     * KIS API에서 토큰 새로 발급
     */
    private String refreshAndSaveToken() {
        log.info("KIS 액세스 토큰 만료. 새로 발급 시작");
        try {
            String url = apiBaseUrl + TOKEN_ENDPOINT;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = Map.of(
                    "grant_type", "client_credentials",
                    "appkey", kisKey,
                    "appsecret", kisSecret
            );

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<KisTokenResponse> response = restTemplate.postForEntity(
                    url, entity, KisTokenResponse.class
            );

            KisTokenResponse tokenResponse = response.getBody();
            if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
                throw new IllegalStateException("KIS 토큰 발급 응답이 유효하지 않습니다.");
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiresAt = resolveExpiry(now, tokenResponse);

            // DB에 저장
            ApiToken newToken = ApiToken.builder()
                    .apiName(API_NAME_KIS)
                    .accessToken(tokenResponse.getAccessToken())
                    .tokenType(tokenResponse.getTokenType() != null ? tokenResponse.getTokenType() : "Bearer")
                    .issuedAt(now)
                    .expiresAt(expiresAt)
                    .build();

            apiTokenMapper.upsertToken(newToken);
            log.info("KIS 액세스 토큰 발급 및 DB 저장 완료, 만료: {}", expiresAt);

            return newToken.getAccessToken();

        } catch (Exception e) {
            log.error("KIS 토큰 갱신 실패: {}", e.getMessage(), e);
            throw new RuntimeException("API 토큰 갱신 실패", e);
        }
    }

    /**
     * 토큰 만료 시각 계산
     */
    private LocalDateTime resolveExpiry(LocalDateTime now, KisTokenResponse tokenResponse) {
        // 만료 시각 문자열 우선 사용
        if (tokenResponse.getAccessTokenTokenExpired() != null) {
            try {
                return LocalDateTime.parse(tokenResponse.getAccessTokenTokenExpired(), TOKEN_EXPIRE_FORMAT);
            } catch (DateTimeParseException ex) {
                log.warn("토큰 만료일시 파싱 실패: {}", tokenResponse.getAccessTokenTokenExpired());
            }
        }

        // expires_in 사용
        long expiresInSeconds = 0L;
        try {
            if (tokenResponse.getExpiresIn() != null) {
                expiresInSeconds = Long.parseLong(tokenResponse.getExpiresIn());
            }
        } catch (NumberFormatException ex) {
            log.warn("expires_in 파싱 실패: {}", tokenResponse.getExpiresIn());
        }

        // 기본값: 23시간
        if (expiresInSeconds <= 0) {
            expiresInSeconds = 23 * 60 * 60;
        }

        return now.plusSeconds(expiresInSeconds);
    }

    /**
     * KIS 토큰 응답 DTO
     */
    @Getter
    @NoArgsConstructor
    private static class KisTokenResponse {
        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("token_type")
        private String tokenType;

        @JsonProperty("expires_in")
        private String expiresIn;

        @JsonProperty("access_token_token_expired")
        private String accessTokenTokenExpired;
    }
}
