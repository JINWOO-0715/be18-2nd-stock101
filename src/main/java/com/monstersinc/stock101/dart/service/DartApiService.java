package com.monstersinc.stock101.dart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monstersinc.stock101.common.ratelimit.RateLimitException;
import com.monstersinc.stock101.common.ratelimit.RateLimiter;
import com.monstersinc.stock101.dart.dto.DartDisclosureRequest;
import com.monstersinc.stock101.dart.dto.DartDisclosureResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.checkerframework.common.reflection.qual.GetClass;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DartApiService {

    private static final String DART_LIST_URL = "https://opendart.fss.or.kr/api/list.json";
    private static final DateTimeFormatter DART_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Value("${apikey.dart-api-key:#{null}}")
    private String dartApiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RateLimiter rateLimiter;
    private final DartApiCacheService cacheService;

    /**
     * [수정] DART API 호출 - Rate Limiter 및 Fallback 캐시 적용
     */
    @Cacheable(value = "disclosure", key = "'dart_' + #request.corpCode + '_' + #request.reportType + '_' + #request.beginDate")
    public DartDisclosureResponse searchDisclosures(DartDisclosureRequest request) {
        if (!StringUtils.hasText(dartApiKey)) {
            return createErrorResponse("DART API Key가 설정되지 않았습니다.");
        }

        try {
            boolean acquired = rateLimiter.acquire("DART_API", 3000);
            if (!acquired) {
                log.warn("DART API Rate Limit 대기 시간 초과, Fallback 캐시 시도");
                return cacheService.getFallbackCache(request.getCorpCode(), request.getReportType())
                        .orElseThrow(() -> new RateLimitException("DART_API", 1000));
            }

            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(DART_LIST_URL)
                    .queryParam("crtfc_key", dartApiKey);

            addQueryParameters(builder, request);

            ResponseEntity<String> response = restTemplate.exchange(
                    builder.toUriString(), HttpMethod.GET, null, String.class);

            DartDisclosureResponse result = objectMapper.readValue(response.getBody(), DartDisclosureResponse.class);

            if (result.getList() != null && StringUtils.hasText(request.getReportType())) {
                result.getList().forEach(item -> item.setPblntfTy(request.getReportType()));
            }

            // ⭐ 성공 시 Fallback 캐시 저장
            if ("000".equals(result.getStatus())) {
                cacheService.saveFallbackCache(request.getCorpCode(), request.getReportType(), result);
            }

            return result;

        } catch (RateLimitException e) {
            throw e; // Rate Limit은 그대로 전파
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("DART API Rate Limiter 인터럽트 발생");
            return cacheService.getFallbackCache(request.getCorpCode(), request.getReportType())
                    .orElse(createErrorResponse("Rate Limiter 인터럽트 발생"));
        } catch (Exception e) {
            log.error("DART API 호출 중 오류 발생: ", e);

            // ⭐ 예외 발생 시 Fallback 캐시 시도
            return cacheService.getFallbackCache(request.getCorpCode(), request.getReportType())
                    .orElse(createErrorResponse("공시정보 조회 중 오류: " + e.getMessage()));
        }
    }

    /**
     * 기업 고유번호로 1년치 특정 타입 공시 조회
     */
    public DartDisclosureResponse getDisclosuresByTypeAndCorp(String corpCode, String reportType) {
        DartDisclosureRequest request = DartDisclosureRequest.builder()
                .corpCode(corpCode)
                .reportType(reportType)
                .beginDate(LocalDate.now().minusYears(1).format(DART_DATE_FORMAT))
                .endDate(LocalDate.now().format(DART_DATE_FORMAT))
                .pageNo(1)
                .pageCount(100)
                .build();

        return searchDisclosures(request);
    }

    /**
     * [수정] 주요 3대 공시 타입(A, B, C) 통합 조회 - 부분 실패 처리
     */
    public DartDisclosureResponse getDisclosuresByStandardTypes(String corpCode) {
        String[] targetTypes = {"A", "B", "C"};
        List<DartDisclosureResponse.DartDisclosure> combinedList = new ArrayList<>();
        List<String> failedTypes = new ArrayList<>();

        for (String type : targetTypes) {
            try {
                DartDisclosureResponse response = getDisclosuresByTypeAndCorp(corpCode, type);

                if ("000".equals(response.getStatus()) && response.getList() != null) {
                    combinedList.addAll(response.getList());
                } else {
                    failedTypes.add(type);
                    log.warn("DART API 타입 {} 조회 실패: {}", type, response.getMessage());
                }

                // ⭐ Thread.sleep 제거: Rate Limiter가 이미 제어하므로 불필요

            } catch (RateLimitException e) {
                // Rate Limit 도달 시 나머지 타입은 캐시에서 조회
                log.warn("Rate Limit 도달, 타입 {} Fallback 캐시 사용", type);
                failedTypes.add(type);

                cacheService.getFallbackCache(corpCode, type).ifPresent(cached -> {
                    if (cached.getList() != null) {
                        combinedList.addAll(cached.getList());
                    }
                });

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failedTypes.add(type);
                log.error("타입 {} 조회 중 인터럽트 발생", type);
            } catch (Exception e) {
                failedTypes.add(type);
                log.error("타입 {} 조회 중 오류: {}", type, e.getMessage());
            }
        }

        // 부분 실패 메시지 생성
        String message = failedTypes.isEmpty()
                ? "정상"
                : "일부 타입 실패 (Fallback 캐시 사용): " + String.join(", ", failedTypes);

        return DartDisclosureResponse.builder()
                .status(combinedList.isEmpty() ? "error" : "000")
                .message(message)
                .list(combinedList)
                .totalCount(combinedList.size())
                .build();
    }

    // --- 내부 헬퍼 메서드 ---

    private void addQueryParameters(UriComponentsBuilder builder, DartDisclosureRequest request) {
        if (request.getCorpCode() != null) builder.queryParam("corp_code", request.getCorpCode());
        if (request.getBeginDate() != null) builder.queryParam("bgn_de", request.getBeginDate());
        if (request.getEndDate() != null) builder.queryParam("end_de", request.getEndDate());
        if (request.getReportType() != null) builder.queryParam("pblntf_ty", request.getReportType());
        if (request.getLastReportNumber() != null) builder.queryParam("last_reprt_at", request.getLastReportNumber());
        
        builder.queryParam("page_no", request.getPageNo() != null ? request.getPageNo() : 1);
        builder.queryParam("page_count", request.getPageCount() != null ? request.getPageCount() : 10);
    }

    private DartDisclosureResponse createErrorResponse(String message) {
        return DartDisclosureResponse.builder().status("error").message(message).build();
    }



}