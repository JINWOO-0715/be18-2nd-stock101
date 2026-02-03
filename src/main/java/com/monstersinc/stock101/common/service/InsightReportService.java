package com.monstersinc.stock101.common.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monstersinc.stock101.disclosure.repository.AIDisclosureReportRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 인사이트 리포트 생성 서비스
 * 컨텍스트 수집부터 리포트 생성 및 저장까지의 전체 프로세스를 캡슐화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightReportService {

    private final EmbeddingService embeddingService;
    private final ChatLanguageModel chatModel;
    private final AIDisclosureReportRepository aidisclosureReportRepository;
    private final ObjectMapper objectMapper;

    /**
     * 인사이트 리포트 생성 및 저장 (외부에서 한 줄로 호출 가능)
     *
     * @param sourceId 문서 소스 ID
     * @param stockId 주식 ID (DB 조회 최적화를 위해 외부에서 전달)
     */
    public void generateAndSaveFullReport(Long sourceId, String stockId) {
        log.info("인사이트 리포트 생성 시작: sourceId={}, stockId={}", sourceId, stockId);

        try {
            // 1. 요약/전망에 필요한 핵심 컨텍스트 수집 (Vector Search)
            String relevantContext = collectContextForReport(sourceId);
            log.debug("컨텍스트 수집 완료: {} 문자", relevantContext.length());

            // 2. LLM을 통한 리포트 생성
            String finalReport = generateFullInsightReport(relevantContext);
            log.debug("리포트 생성 완료: {} 문자", finalReport.length());

            // 3. 리포트 저장 (stockId를 전달하여 중복 DB 조회 방지)
            saveInsightReport(sourceId, stockId, finalReport);
            log.info("인사이트 리포트 저장 완료: sourceId={}, stockId={}", sourceId, stockId);

        } catch (Exception e) {
            log.error("인사이트 리포트 생성 실패: sourceId={}", sourceId, e);
            throw new RuntimeException("인사이트 리포트 생성 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 요약/전망 리포트 생성을 위한 컨텍스트 수집 로직
     */
    private String collectContextForReport(Long sourceId) {
        Map<String, String> themes = new LinkedHashMap<>();

        themes.put("기업 정보",
                "종목 이름, 반기 공시 문서 종류, 공시 날짜, 산업군, 주요 제품 및 서비스, 최근 주가 동향");


        // 1. 수익 모델 (Top-line): '매출의 질'을 본다.
        // P(가격) * Q(물량) 구조, 믹스 개선 등을 포괄
        themes.put("매출 성장 요인 및 수익 구조",
                "매출 증감 원인, 주요 제품 및 서비스 판매 추이, 판매 가격(ASP) 변동, 판매량(Q) 및 가동률, 지역별/부문별 매출 비중");

        // 2. 비용 및 이익 (Bottom-line): '효율성'을 본다.
        // 원가율, 판관비, R&D 비용 등 모든 비용 이슈
        themes.put("이익률 분석 및 비용 효율성",
                "영업이익률 변동 사유, 매출원가율 추이, 판매관리비 구조, 고정비 및 변동비 효과, 손익분기점(BEP), 마진율 개선 요인");

        // 3. 시장 경쟁력 (Moat): '해자'를 본다.
        // 점유율, 브랜드, 기술력, 독과점 여부
        themes.put("시장 지배력 및 경쟁 우위",
                "시장 점유율(M/S) 추이, 경쟁사 대비 차별점, 산업 내 진입 장벽, 핵심 경쟁력 및 기술적 우위, 브랜드 파워");

        // 4. 외부 환경 및 리스크 (Macro & Risk): '통제 불가능 변수'를 본다.
        // 규제, 환율, 금리, 원자재 등
        themes.put("거시 경제 및 대외 리스크",
                "환율 및 금리 영향, 원재료 가격 변동, 글로벌 경기 및 산업 사이클, 정부 정책 및 법적 규제, 지정학적 리스크");

        // 5. 미래 가치 (Future): '성장 잠재력'을 본다.
        // CAPEX, 가이던스, 신사업
        themes.put("미래 성장 전략 및 투자",
                "신규 사업 진출 계획, 설비 투자(CAPEX) 및 R&D 로드맵, 경영진 실적 가이던스, 중장기 성장 목표, 주주 환원 정책");

        return themes.entrySet().stream()
                .map(entry -> {
                    // 검색 결과 수를 테마별로 조정 (미래 전략이나 리스크는 더 깊게 검색)
                    int searchCount = entry.getKey().contains("미래") || entry.getKey().contains("리스크") ? 6 : 4;
                    List<String> results = embeddingService.searchByTheme(sourceId, entry.getValue(), searchCount);

                    return String.format("### [%s 관련 데이터]\n%s",
                            entry.getKey(),
                            results.isEmpty() ? "관련 정보 없음" : String.join("\n\n", results));
                })
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    /**
     * 최종 리포트 생성 (2단계 API 호출로 토큰 분산)
     * 1단계: 기본 정보 + 요약 + 메트릭스 생성
     * 2단계: 전망 + 핵심 포인트 + 투자 등급 생성
     */
    public String generateFullInsightReport(String context) {
        try {
            // 1단계: 기본 정보와 요약, 메트릭스 생성
            String summaryResponse = generateSummaryAndMetrics(context);
            log.debug("1단계 생성 완료: {} 문자", summaryResponse.length());

            // 2단계: 전망 및 종합 분석 생성 (추가 필드만)
            String prospectResponse = generateProspectAndAnalysis(context);
            log.debug("2단계 생성 완료: {} 문자", prospectResponse.length());

            // 3단계: 두 JSON 병합
            String mergedResponse = mergeJsonResponses(summaryResponse, prospectResponse);
            log.debug("병합 완료: {} 문자", mergedResponse.length());

            return mergedResponse;

        } catch (Exception e) {
            log.error("리포트 생성 중 오류 발생", e);
            throw new RuntimeException("리포트 생성 실패", e);
        }
    }

    /**
     * 1단계: 기본 정보, 요약, 메트릭스 생성 (과거/현재 분석)
     */
    private String generateSummaryAndMetrics(String context) {
        String prompt = String.format(
                "당신은 글로벌 IB(투자은행) 출신의 수석 애널리스트입니다. 다음 [데이터]를 바탕으로 기업 분석 리포트의 **1단계: 기본 정보 및 실적 요약**을 JSON 형식으로 작성하세요.\n\n" +
                        "[데이터]\n%s\n\n" +
                        "### ⚠️ 핵심 분석 원칙:\n" +
                        "1. **재무적 중대성(Materiality) 중심:** '기업의 주가'와 '미래 현금 흐름'에 영향을 미치는 요소에 집중\n" +
                        "   - 단순 운영 활동(안전 교육, 의례적 행사, 미미한 규제)은 배제\n" +
                        "   - 막대한 비용이나 매출 차질을 유발하는 이슈는 핵심으로 다룸\n" +
                        "2. **구조적 원인 분석:** 현상이 아닌 근본 원인 분석\n" +
                        "   - '매출 증가' (X) → '고가 라인업 비중 확대로 인한 ASP 상승' (O)\n" +
                        "   - '비용 감소' (X) → '원자재 가격 하락 및 공정 효율화에 따른 마진 개선' (O)\n" +
                        "3. **투자자 관점의 언어:** 해당 산업의 핵심성과지표(KPI) 활용\n\n" +

                        "### 작성 지침:\n" +
                        "1. **title**: ' [종목명]: YYYY-반기/분기 보고서 [핵심 투자 포인트]' 형식\n" +
                        "   - 실제 공시 제출 날짜 기반으로 반기/분기 선택\n" +
                        "   - 핵심 투자 포인트는 전문적 문구 사용 (60자 내외)\n" +
                        "   - 예: 'ASP 상승에 따른 마진 스프레드 확대', 'HBM 공급망 진입에 따른 리레이팅 가시화'\n" +
                        "2. **content**: 해당 문서를 100자 내외로 간략 요약\n" +
                        "3. **summary_content**: 실적의 드라이버(가격, 물량, 비용, 환율 등) 중심으로 200자 요약\n" +
                        "4. **metrics_data**: 핵심 데이터 표 (3-4개 항목 )\n" +
                        "   - 매출, 이익 외에 해당 산업의 KPI 포함 (영업이익률, 가동률, 부채비율 등)\n" +
                        "   - [수치 표기]: 금액은 '억' 단위로 가독성 있게 요약 (예: 1,100억원).이때 금액 앞 뒤 괄호 사용하지 말것. \n" +
                        "   - [증감(variance) 계산 규칙]:\n" +

                        "### JSON Schema (3500토큰 이내):\n" +
                        "{\n" +
                        "  \"title\": \"string\",\n" +
                        "  \"content\": \"string\",\n" +
                        "  \"summary_content\": \"string\",\n" +
                        "  \"metrics_data\": [\n" +
                        "    {\"item\": \"항목명\", \"current\": \"수치\", \"previous\": \"수치\", \"variance\": \"증감\"}\n" +
                        "  ]\n" +
                        "}",
                context
        );

        return chatModel.generate(prompt);
    }

    /**
     * 2단계: 전망, 핵심 포인트, 투자 등급 생성 (미래 분석, 새로운 필드만)
     */
    private String generateProspectAndAnalysis(String context) {
        String prompt = String.format(
                "당신은 글로벌 IB(투자은행) 출신의 수석 애널리스트입니다. [원본 데이터]를 바탕으로 **전망 및 투자 분석**을 JSON 형식으로 작성하세요.\n\n" +
                        "[원본 데이터]\n%s\n\n" +

                        "### ⚠️ 핵심 분석 원칙:\n" +
                        "1. **재무적 중대성** 중심: 주가와 현금 흐름에 영향을 미치는 요소만 다룸\n" +
                        "2. **구조적 원인 분석**: 현상이 아닌 근본 원인 파악\n" +
                        "3. **투자자 관점의 언어**: 산업별 KPI 활용\n\n" +

                        "### 작성 지침:\n" +
                        "1. **prospect_content**: 팩트 기반 미래 전망 (200자 내외)\n" +
                        "   - 확정된 투자 계획(CAPEX), 수주 잔고, 시장 성장률 등 구체적 근거 제시\n" +
                        "   - 단순 낙관론 배제\n" +
                        "2. **key_points**: 투자 매력도와 리스크의 균형 (3-4개 항목, 각 150자 내외)\n" +
                        "   - 투자 포인트 2개: Upside Potential\n" +
                        "   - 리스크 포인트 1~2개: 회사의 헤지 전략 또는 통제 불가능 여부 평가\n" +
                        "3. **investment_grade**: 데이터 근거하에 '매수(BUY)', '보유(HOLD)', '주의(CAUTION)' 선택\n" +
                        "4. **sentiment_score**: -1.00 ~ 1.00 사이 수치\n\n" +

                        "### JSON Schema (추가 필드만 생성, 3500토큰 이내):\n" +
                        "{\n" +
                        "  \"prospect_content\": \"string\",\n" +
                        "  \"key_points\": [\"string\"],\n" +
                        "  \"investment_grade\": \"string\",\n" +
                        "  \"sentiment_score\": 0.00\n" +
                        "}",
                context
        );

        return chatModel.generate(prompt);
    }

    /**
     * 1단계와 2단계 JSON 응답 병합
     */
    private String mergeJsonResponses(String summaryJson, String prospectJson) {
        try {
            // JSON 정제
            String cleanedSummary = cleanJsonResponse(summaryJson);
            String cleanedProspect = cleanJsonResponse(prospectJson);

            // Map으로 파싱
            Map<String, Object> summaryMap = objectMapper.readValue(cleanedSummary, new TypeReference<Map<String, Object>>() {});
            Map<String, Object> prospectMap = objectMapper.readValue(cleanedProspect, new TypeReference<Map<String, Object>>() {});

            // 병합 (2단계 내용을 1단계에 추가)
            summaryMap.putAll(prospectMap);

            // 다시 JSON 문자열로 변환
            return objectMapper.writeValueAsString(summaryMap);

        } catch (JsonProcessingException e) {
            log.error("JSON 병합 실패", e);
            throw new RuntimeException("JSON 병합 중 오류 발생", e);
        }
    }

    /**
     * 리포트 저장 - JSON 파싱 후 각 필드별로 저장
     *
     * @param sourceId 문서 소스 ID
     * @param stockId 주식 ID (외부에서 전달받아 중복 조회 방지)
     * @param reportContent LLM이 생성한 리포트 JSON 문자열
     */
    private void saveInsightReport(Long sourceId, String stockId, String reportContent) {
        try {
            // 1. JSON 응답 정제 (코드 블록이나 불필요한 텍스트 제거)
            String cleanedJson = cleanJsonResponse(reportContent);

            // 2. JSON 파싱
            Map<String, Object> reportMap = objectMapper.readValue(cleanedJson, new TypeReference<Map<String, Object>>() {});

            // 3. 각 필드 추출
            String title = (String) reportMap.get("title");
            String summaryContent = (String) reportMap.get("summary_content");
            String prospectContent = (String) reportMap.get("prospect_content");

            // metrics_data를 JSON 문자열로 변환
            String metricsData = reportMap.get("metrics_data") != null
                    ? objectMapper.writeValueAsString(reportMap.get("metrics_data"))
                    : null;

            // key_points를 JSON 문자열로 변환
            String keyPoints = reportMap.get("key_points") != null
                    ? objectMapper.writeValueAsString(reportMap.get("key_points"))
                    : null;

            // sentiment_score 추출 및 변환
            BigDecimal sentimentScore = null;
            Object sentimentObj = reportMap.get("sentiment_score");
            if (sentimentObj != null) {
                if (sentimentObj instanceof Number) {
                    sentimentScore = BigDecimal.valueOf(((Number) sentimentObj).doubleValue());
                } else if (sentimentObj instanceof String) {
                    try {
                        sentimentScore = new BigDecimal((String) sentimentObj);
                    } catch (NumberFormatException e) {
                        log.warn("sentiment_score 변환 실패: {}", sentimentObj);
                    }
                }
            }

            String investmentGrade = (String) reportMap.get("investment_grade");
            String content = (String) reportMap.get("content");

            // 4. Repository를 통해 저장 (stockId는 파라미터로 전달받음)
            aidisclosureReportRepository.insertAiReport(
                sourceId,
                stockId,
                title,
                summaryContent,
                prospectContent,
                metricsData,
                keyPoints,
                sentimentScore,
                investmentGrade,
                content
            );

            log.info("리포트 파싱 및 저장 완료: sourceId={}, stockId={}, title={}", sourceId, stockId, title);

        } catch (JsonProcessingException e) {
            log.error("JSON 파싱 실패: sourceId={}", sourceId, e);
            log.debug("원본 응답: {}", reportContent);
            throw new RuntimeException("리포트 JSON 파싱 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * LLM 응답에서 순수 JSON만 추출
     */
    private String cleanJsonResponse(String response) {
        // ```json 블록이나 마크다운 코드 블록 제거
        String cleaned = response.trim();

        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }
}
