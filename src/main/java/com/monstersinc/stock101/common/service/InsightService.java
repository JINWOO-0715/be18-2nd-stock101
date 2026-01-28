package com.monstersinc.stock101.common.service;

import com.monstersinc.stock101.disclosure.repository.AIDisclosureReportRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsightService {

    private final EmbeddingService embeddingService;
    private final ChatLanguageModel chatModel;
    private final AIDisclosureReportRepository aidisclosureReportRepository;


    // 요약/전망 리포트 생성을 위한 컨텍스트 수집 로직
    public String collectContextForReport(Long sourceId) {
        // 각 테마별로 검색 쿼리를 정교화하여 마크다운 표와 분석 내용을 유도합니다.
        Map<String, String> themes = new LinkedHashMap<>();
        themes.put("재무 실적", "매출액 영업이익 당기순이익 요약재무정보");
        themes.put("사업 현황", "주요 제품 서비스 가동률 생산실적");
        themes.put("시장 및 경쟁", "시장점유율 경쟁우위 시장특성 전망");
        themes.put("미래 전략", "이사의 경영진단 분석의견 투자계획 신규사업");

        return themes.entrySet().stream()
                .map(entry -> {
                    // 각 테마별로 EmbeddingService에 검색 요청
                    List<String> results = embeddingService.searchByTheme(sourceId, entry.getValue(), 4);

                    return String.format("### [%s 관련 데이터]\n%s",
                            entry.getKey(),
                            results.isEmpty() ? "관련 정보 없음" : String.join("\n\n", results));
                })
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    /**
     * 최종 리포트 생성 (요약 + 전망 체이닝)
     */
    public String generateFullInsightReport(String context) {
        String prompt = String.format(
                "당신은 금융 분석가이자 전략 컨설턴트입니다. 다음 [데이터]를 분석하여 '현황 요약'과 '향후 전망'이 포함된 통합 리포트를 작성하세요.\n\n" +
                        "[데이터]\n%s\n\n" +
                        "### 작성 지침:\n" +
                        "1. [Part 1. 핵심 요약]: 데이터에 기반한 실적, 사업 구조, 점유율을 객관적으로 요약하세요. (표 형식 활용)\n" +
                        "2. [Part 2. 향후 전망]: 요약된 수치와 시장 상황을 바탕으로 기업의 미래 성장성, 리스크, 투자 가치를 전문적으로 분석하세요.\n" +
                        "3. 모든 내용은 한국어로 작성하며, 전문적인 톤을 유지하세요.\n\n" +
                        "### 리포트 형식:\n" +
                        "# 기업 분석 통합 리포트\n\n" +
                        "## 1. 경영 및 재무 현황 요약\n(Part 1 내용 작성)\n\n" +
                        "## 2. 향후 비즈니스 전망 및 전략 분석\n(Part 2 내용 작성)",
                context
        );

        return chatModel.generate(prompt);
    }

    public void saveInsightReport(Long sourceId, String reportContent) {
        aidisclosureReportRepository.insertAiReport(sourceId, reportContent);
    }
}
