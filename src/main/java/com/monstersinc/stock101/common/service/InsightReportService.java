package com.monstersinc.stock101.common.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monstersinc.stock101.disclosure.domain.DisclosureSource;
import com.monstersinc.stock101.disclosure.repository.AIDisclosureReportRepository;
import com.monstersinc.stock101.disclosure.repository.DisclosureSourceRepository;
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
    private final DisclosureSourceRepository disclosureSourceRepository;
    private final ObjectMapper objectMapper;

    /**
     * 인사이트 리포트 생성 및 저장 (외부에서 한 줄로 호출 가능)
     *
     * @param sourceId 문서 소스 ID
     */
    public void generateAndSaveFullReport(Long sourceId) {
        log.info("인사이트 리포트 생성 시작: sourceId={}", sourceId);

        try {
            // 1. 요약/전망에 필요한 핵심 컨텍스트 수집 (Vector Search)
            String relevantContext = collectContextForReport(sourceId);
            log.debug("컨텍스트 수집 완료: {} 문자", relevantContext.length());

            // 2. LLM을 통한 리포트 생성
            String finalReport = generateFullInsightReport(relevantContext);
            log.debug("리포트 생성 완료: {} 문자", finalReport.length());

            // 3. 리포트 저장
            saveInsightReport(sourceId, finalReport);
            log.info("인사이트 리포트 저장 완료: sourceId={}", sourceId);

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

        // 1. 재무: 단순 수치뿐 아니라 수익성 지표(ROE, 영업이익률)와 비용 구조 포함
        themes.put("재무 실적 및 수익성", "매출액 영업이익 당기순이익 수익성지표 ROE 영업이익률 비용구조 판관비");

        // 2. 사업: 가동률 외에 전방 산업의 수요 및 제품 포트폴리오 변화
        themes.put("사업 부문별 성과", "주요 제품별 매출 비중 가동률 생산능력 신제품 출시 판매 경로");

        // 3. 경쟁력: 단순 점유율 외에 진입 장벽과 핵심 기술력
        themes.put("시장 지배력 및 경쟁 우위", "시장점유율 진입장벽 독점적 지위 경쟁사 대비 우위 핵심 기술력");

        // 4. 리스크: 리포트의 신뢰도를 높여줄 부정적 요인 수집 (중요)
        themes.put("잠재적 리스크 요인", "우발부채 소송 현황 원재료 가격 변동 리스크 규제 환경 거시경제 위협");

        // 5. 미래 가치: 이사의 진단 외에 구체적인 신규 투자 계획(CAPEX)
        themes.put("미래 성장 동력 및 전략", "이사의 경영진단 분석의견 신규사업 진출 투자계획 CAPEX R&D 투자");

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
     * 최종 리포트 생성 (요약 + 전망 체이닝)
     */
    private String generateFullInsightReport(String context) {
        String prompt = String.format(
                "당신은 글로벌 IB(투자은행) 출신의 수석 애널리스트입니다. 다음 [데이터]를 바탕으로 기관 투자자 수준의 '기업 분석 심층 리포트'를 JSON 형식으로 작성하세요.\n" +
                        "응답은 반드시 아래 제시된 JSON 스키마 구조를 엄수하며, 전문적인 금융 용어를 적절히 활용하세요.\n\n" +
                        "[데이터]\n%s\n\n" +
                        "### 작성 지침:\n" +
                        "1. **title**: 시장의 이목을 끌 수 있는 전문적인 제목 (예: [종목명]: 수익성 개선 가속화와 신성장 동력의 결합)\n" +
                        "2. **summary_content**: [경영 현황 요약] 단순히 수치를 나열하지 말고, 실적 변동의 근본 원인(Factor Analysis), 시장 점유율 변화, 사업부별 기여도를 심층 서술하세요.\n" +
                        "3. **prospect_content**: [향후 전망 및 전략] 업황 사이클 분석, 향후 1~2년 내 신규 사업의 기여도, 매크로 환경 변화에 따른 리스크 관리 전략을 서술하세요.\n" +
                        "4. **content**: 'AI 한 줄 평' 영역. 투자자가 의사결정을 내릴 수 있는 날카로운 결론 한 문장을 작성하세요.\n" +
                        "5. **metrics_data**: '핵심 데이터 상세' 표. 매출, 이익 외에 해당 산업의 KPI(예: 영업이익률, 가동률, 부채비율 등)를 포함하여 5~6개 항목으로 구성하세요.\n" +
                        "6. **key_points**: '핵심 포인트' 영역. 투자 포인트 2개와 리스크 포인트 1~2개를 포함하여 날카로운 통찰력을 제공하세요.\n" +
                        "7. **investment_grade**: 데이터 근거 하에 '매수(BUY)', '보유(HOLD)', '주의(CAUTION)' 중 하나를 선택하세요.\n" +
                        "8. **sentiment_score**: -1.00 ~ 1.00 사이의 수치.\n\n" +
                        "### JSON Schema:\n" +
                        "{\n" +
                        "  \"title\": \"string\",\n" +
                        "  \"content\": \"string\",\n" +
                        "  \"summary_content\": \"string\",\n" +
                        "  \"prospect_content\": \"string\",\n" +
                        "  \"metrics_data\": [\n" +
                        "    {\"item\": \"항목명\", \"current\": \"수치\", \"previous\": \"수치\", \"variance\": \"증감\"}\n" +
                        "  ],\n" +
                        "  \"key_points\": [\"string\"],\n" +
                        "  \"investment_grade\": \"string\",\n" +
                        "  \"sentiment_score\": 0.00\n" +
                        "}",
                context
        );

        return chatModel.generate(prompt);
    }

    /**
     * 리포트 저장 - JSON 파싱 후 각 필드별로 저장
     */
    private void saveInsightReport(Long sourceId, String reportContent) {
        try {
            // 1. sourceId로 DisclosureSource 조회하여 stockId 가져오기
            DisclosureSource source = disclosureSourceRepository.findById(sourceId)
                    .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다: sourceId=" + sourceId));
            String stockId = source.getStockId();

            // 2. JSON 응답 정제 (코드 블록이나 불필요한 텍스트 제거)
            String cleanedJson = cleanJsonResponse(reportContent);

            // 3. JSON 파싱
            Map<String, Object> reportMap = objectMapper.readValue(cleanedJson, new TypeReference<Map<String, Object>>() {});

            // 4. 각 필드 추출
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

            // 5. Repository를 통해 저장 (stockId 포함)
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
