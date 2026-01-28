package com.monstersinc.stock101.dart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monstersinc.stock101.dart.dto.DartDisclosureRequest;
import com.monstersinc.stock101.dart.dto.DartDisclosureResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.checkerframework.common.reflection.qual.GetClass;
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * [공통] DART API 호출 및 데이터 파싱
     */
    public DartDisclosureResponse searchDisclosures(DartDisclosureRequest request) {
        if (!StringUtils.hasText(dartApiKey)) {
            return createErrorResponse("DART API Key가 설정되지 않았습니다.");
        }

        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(DART_LIST_URL)
                    .queryParam("crtfc_key", dartApiKey);

            addQueryParameters(builder, request);

            ResponseEntity<String> response = restTemplate.exchange(
                    builder.toUriString(), HttpMethod.GET, null, String.class);

            DartDisclosureResponse result = objectMapper.readValue(response.getBody(), DartDisclosureResponse.class);

            if (result.getList() != null && StringUtils.hasText(request.getReportType())) {
                result.getList().forEach(item -> item.setPblntfTy(request.getReportType()));
            }

            return result;

        } catch (Exception e) {
            log.error("DART API 호출 중 오류 발생: ", e);
            return createErrorResponse("공시정보 조회 중 오류: " + e.getMessage());
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
     * 주요 3대 공시 타입(A, B, C) 통합 조회
     */
    public DartDisclosureResponse getDisclosuresByStandardTypes(String corpCode) {
        String[] targetTypes = {"A", "B", "C"};
        List<DartDisclosureResponse.DartDisclosure> combinedList = new ArrayList<>();

        for (String type : targetTypes) {
            DartDisclosureResponse response = getDisclosuresByTypeAndCorp(corpCode, type);
            if ("000".equals(response.getStatus()) && response.getList() != null) {
                combinedList.addAll(response.getList());
            }
        }

        return DartDisclosureResponse.builder()
                .status("000")
                .message("정상")
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