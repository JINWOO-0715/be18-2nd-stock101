package com.monstersinc.stock101.kis.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.monstersinc.stock101.kis.dto.KisCandleResponse;
import com.monstersinc.stock101.kis.model.mapper.ApiTokenMapper;
import com.monstersinc.stock101.kis.model.vo.ApiToken;
import com.monstersinc.stock101.stock.model.mapper.StockPriceRepository;
import com.monstersinc.stock101.stock.model.vo.Stock;
import com.monstersinc.stock101.stock.model.vo.StockPrice;
import com.monstersinc.stock101.stock.model.mapper.StockMapper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * KIS(한국투자증권) API 서비스
 * 국내주식 기간별 시세 API
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KisStockPriceService {

    private final RestTemplate restTemplate;
    private final StockMapper stockMapper;
    private final StockPriceRepository stockPriceRepository;
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
    private static final int MAX_ITEMS_PER_REQUEST = 100; // API 최대 반환 건수
    private static final LocalDate DEFAULT_START_DATE = LocalDate.of(2023, 1, 1); // 데이터 없을 때 시작일
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TOKEN_EXPIRE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 종목의 일봉 데이터를 최신 상태로 업데이트
     * - DB에서 가장 최근 날짜 조회
     * - 최근 날짜가 없으면 2023-01-01부터, 있으면 그 다음날부터 오늘까지 KIS API 호출
     * - 이미 오늘까지 데이터가 있으면 업데이트 스킵
     *
     * @param stockCode 종목코드 (6자리 숫자 문자열)
     * @return 저장/업데이트된 시세 데이터 개수
     */
    @Transactional
    public int updateStockPrices(String stockCode) {
        try {
            // 1. 종목 정보 조회
            Stock stock = stockMapper.selectStockByCode(stockCode);
            if (stock == null) {
                log.warn("종목을 찾을 수 없습니다: {}", stockCode);
                return 0;
            }

            Long stockId = stock.getStockId();
            LocalDate today = LocalDate.now();

            // 2. DB에서 가장 최근 날짜 조회
            LocalDate latestDate = stockPriceRepository.findLatestDateByStockId(stockId);
            
            LocalDate startDate;
            if (latestDate == null) {
                // 데이터가 없으면 2023-01-01부터
                startDate = DEFAULT_START_DATE;
                log.info("📊 종목 {} 데이터 없음. {}부터 전체 조회 시작", stockCode, startDate);
            } else if (!latestDate.isBefore(today)) {
                // 이미 오늘까지 데이터가 있으면 스킵
                log.debug("종목 {} 이미 최신 상태 (최근 데이터: {})", stockCode, latestDate);
                return 0;
            } else {
                // 마지막 날짜 다음날부터
                startDate = latestDate.plusDays(1);
                log.info("📊 종목 {} 마지막 데이터: {}, {}부터 업데이트 시작", stockCode, latestDate, startDate);
            }

            // 3. KIS API에서 데이터 조회 및 저장
            return fetchAndSavePrices(stockId, stockCode, startDate, today);

        } catch (Exception e) {
            log.error("종목 {} 시세 업데이트 중 오류 발생: {}", stockCode, e.getMessage(), e);
            throw new RuntimeException("시세 업데이트 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 지정 기간의 일봉 데이터를 KIS API에서 가져와 DB에 저장
     */
    private int fetchAndSavePrices(Long stockId, String stockCode, LocalDate startDate, LocalDate endDate) {
        int totalSaved = 0;
        LocalDate currentEndDate = endDate;

        while (currentEndDate.isAfter(startDate) || currentEndDate.isEqual(startDate)) {
            KisCandleResponse response = fetchCandleData(stockCode, startDate, currentEndDate);

            if (response == null || !response.isSuccess() || response.getOutput2() == null
                    || response.getOutput2().isEmpty()) {
                log.info("종목 {} 더 이상 데이터가 없습니다", stockCode);
                break;
            }

            // 데이터를 엔티티로 변환
            List<StockPrice> entities = new ArrayList<>();
            for (KisCandleResponse.KisCandleData data : response.getOutput2()) {
                try {
                    StockPrice.KisPriceData priceData = new StockPrice.KisPriceData(
                            data.getStckBsopDate(),
                            data.getStckOprc(),
                            data.getStckHgpr(),
                            data.getStckLwpr(),
                            data.getStckClpr(),
                            data.getAcmlVol(),
                            data.getAcmlTrPbmn());

                    StockPrice entity = StockPrice.fromKisResponse(stockId, priceData);
                    entities.add(entity);
                } catch (Exception e) {
                    log.warn("데이터 변환 실패: {} - {}", data.getStckBsopDate(), e.getMessage());
                }
            }

            // 배치 저장 (UPSERT)
            if (!entities.isEmpty()) {
                stockPriceRepository.insertPrices(entities);
                totalSaved += entities.size();
                log.info("✅ 종목 {} {} 건 저장 완료", stockCode, entities.size());
            }

            // 반복 조건 확인
            if (response.getOutput2().size() < MAX_ITEMS_PER_REQUEST) {
                log.debug("데이터 수 {} < {}, 조회 종료", response.getOutput2().size(), MAX_ITEMS_PER_REQUEST);
                break;
            }

            // 마지막 데이터의 날짜를 다음 조회의 종료일로 설정
            String lastDateStr = response.getOutput2().get(response.getOutput2().size() - 1).getStckBsopDate();
            LocalDate lastDate = LocalDate.parse(lastDateStr, DATE_FORMATTER);
            currentEndDate = lastDate.minusDays(1);

            log.debug("다음 조회: startDate={}, endDate={}", startDate, currentEndDate);
        }

        log.info("📊 종목 {} 완료: 총 {} 건 저장", stockCode, totalSaved);
        return totalSaved;
    }

    /**
     * KIS API에서 일봉 데이터 조회
     * 
     * @param stockCode 종목코드
     * @param startDate 시작일
     * @param endDate   종료일
     * @return KIS API 응답
     */
    private KisCandleResponse fetchCandleData(String stockCode, LocalDate startDate, LocalDate endDate) {
        try {
            // API 요청 URL 구성
            String url = UriComponentsBuilder.fromHttpUrl(apiBaseUrl + CANDLE_ENDPOINT)
                    .queryParam("fid_cond_mrkt_div_code", "J") // J: 주식, ETF, ETN
                    .queryParam("fid_input_iscd", stockCode) // 종목코드 (예: 005930)
                    .queryParam("fid_input_date_1", startDate.format(DATE_FORMATTER)) // 시작일
                    .queryParam("fid_input_date_2", endDate.format(DATE_FORMATTER)) // 종료일
                    .queryParam("fid_period_div_code", "D") // D: 일봉, W: 주봉, M: 월봉
                    .queryParam("fid_org_adj_prc", "0") // 0: 수정주가
                    .toUriString();

            // 요청 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("authorization", "Bearer " + getAccessToken());
            headers.set("appKey", kisKey);
            headers.set("appSecret", kisSecret);
            headers.set("tr_id", "FHKST03010100"); // 국내주식 기간별 시세 TR ID
            headers.set("custtype", "P"); // P: 개인, B: 법인 (보통 P)

            HttpEntity<String> entity = new HttpEntity<>(headers);
            log.debug("KIS API 호출 URL: {}", url);
            ResponseEntity<KisCandleResponse> responseEntity = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    KisCandleResponse.class);

            KisCandleResponse response = responseEntity.getBody();

            if (response == null) {
                log.warn("KIS API 응답이 null입니다");
                return null;
            }

            // KIS는 성공 시 rt_cd가 "0"입니다. (문서 확인 필요)
            if (!"0".equals(response.getRtCd())) {
                log.warn("KIS API 실패: {} (코드: {})", response.getMsg1(), response.getRtCd());
                return null;
            }

            return response;

        } catch (Exception e) {
            log.error("KIS API 호출 중 오류 발생: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 액세스 토큰 조회 (DB 저장 기반 + Double-Checked Locking)
     * - DB에 유효한 토큰이 있으면 재사용
     * - 없거나 만료되었으면 KIS API에서 새로 발급 후 DB 저장
     */
    private String getAccessToken() {
        // 1. 먼저 DB 조회 (Lock 없이 빠르게 확인)
        ApiToken cachedToken = apiTokenMapper.selectByApiName(API_NAME_KIS);
        if (cachedToken != null && cachedToken.isValid()) {
            log.debug("DB에서 유효한 KIS 토큰 조회 완료, 만료 시각: {}", cachedToken.getExpiresAt());
            return cachedToken.getAccessToken();
        }

        // 2. 유효한 토큰이 없으면 동기화 블록 진입
        synchronized (this) {
            // Double-Checked Locking: 락 획득 후 다시 한번 DB 확인 (다른 스레드가 이미 갱신했을 수 있음)
            cachedToken = apiTokenMapper.selectByApiName(API_NAME_KIS);
            if (cachedToken != null && cachedToken.isValid()) {
                log.debug("락 획득 후 DB에서 유효한 토큰 발견, 만료 시각: {}", cachedToken.getExpiresAt());
                return cachedToken.getAccessToken();
            }

            return refreshAndSaveToken();
        }
    }

    /**
     * KIS API에서 토큰을 새로 발급받아 DB에 저장
     */
    private String refreshAndSaveToken() {
        log.info("KIS 액세스 토큰 만료 또는 없음. 새로 발급을 시작합니다.");
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
                    url,
                    entity,
                    KisTokenResponse.class);

            KisTokenResponse tokenResponse = response.getBody();
            if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
                throw new IllegalStateException("KIS 토큰 발급 응답이 유효하지 않습니다.");
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiresAt = resolveExpiry(now, tokenResponse);

            // DB에 토큰 저장 (UPSERT)
            ApiToken newToken = ApiToken.builder()
                    .apiName(API_NAME_KIS)
                    .accessToken(tokenResponse.getAccessToken())
                    .tokenType(tokenResponse.getTokenType() != null ? tokenResponse.getTokenType() : "Bearer")
                    .issuedAt(now)
                    .expiresAt(expiresAt)
                    .build();

            apiTokenMapper.upsertToken(newToken);
            log.info("KIS 액세스 토큰 발급 및 DB 저장 완료, 만료 시각: {}", expiresAt);

            return newToken.getAccessToken();

        } catch (Exception e) {
            log.error("KIS 토큰 갱신 중 치명적 오류: {}", e.getMessage(), e);
            throw new RuntimeException("API 통신 실패로 토큰을 갱신할 수 없습니다.", e);
        }
    }

    private LocalDateTime resolveExpiry(LocalDateTime now, KisTokenResponse tokenResponse) {
        // 우선 만료 시각 문자열(access_token_token_expired)을 사용하고, 실패 시 expires_in을 사용
        if (tokenResponse.getAccessTokenTokenExpired() != null) {
            try {
                return LocalDateTime.parse(tokenResponse.getAccessTokenTokenExpired(), TOKEN_EXPIRE_FORMAT);
            } catch (DateTimeParseException ex) {
                log.warn("토큰 만료일시 파싱 실패: {}", tokenResponse.getAccessTokenTokenExpired());
            }
        }

        long expiresInSeconds = 0L;
        try {
            if (tokenResponse.getExpiresIn() != null) {
                expiresInSeconds = Long.parseLong(tokenResponse.getExpiresIn());
            }
        } catch (NumberFormatException ex) {
            log.warn("expires_in 파싱 실패: {}", tokenResponse.getExpiresIn());
        }

        // 만료 정보가 없으면 기본 23시간으로 가정
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
