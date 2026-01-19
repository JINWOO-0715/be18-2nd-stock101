package com.monstersinc.stock101.disclosure.service;

import com.monstersinc.stock101.disclosure.domain.DocumentChunk;
import com.monstersinc.stock101.disclosure.dto.DisclosureAnalysisRequest;
import com.monstersinc.stock101.disclosure.dto.DisclosureAnalysisResponse;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 파이프라인 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    // 순환 참조 방지를 위해 EmbeddingService는 필요한 곳에서 주입받거나 구조 변경 고려
    // 하지만 여기선 RagService가 상위 레벨에서 EmbeddingService를 사용하여 검색 결과를 받아온다고 가정
    // 또는 EmbeddingService를 여기서 사용하여 검색
    private final EmbeddingService embeddingService;

    @Value("${langchain4j.ollama.chat-model.base-url}")
    private String ollamaBaseUrl;

    @Value("${langchain4j.ollama.chat-model.model-name}")
    private String modelName;

    @Value("${disclosure.retrieval.top-k:10}")
    private int topK;

    private ChatLanguageModel chatModel;

    @PostConstruct
    public void init() {
        this.chatModel = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(modelName)
                .temperature(0.1)
                .timeout(java.time.Duration.ofMinutes(5))
                .build();
    }

    /**
     * 공시보고서 분석 (RAG 수행)
     */
    public DisclosureAnalysisResponse analyzeDocument(Long stockId, DisclosureAnalysisRequest request) {
        long startTime = System.currentTimeMillis();

        // 1. 관련 청크 검색
        List<TextSegment> relevantSegments = embeddingService.findSimilarChunks(stockId, request.getQuery(), topK);

        // 2. 컨텍스트 구성
        String context = relevantSegments.stream()
                .map(segment -> String.format("[Page %s] %s", segment.metadata().getString("pageNumber"), segment.text()))
                .collect(Collectors.joining("\n\n"));

        // 3. 프롬프트 구성
        String prompt = buildPrompt(request, context);

        // 4. LLM 응답 생성
        String analysisResult = chatModel.generate(prompt);

        // 5. 응답 DTO 구성
        List<DisclosureAnalysisResponse.ChunkReference> references = relevantSegments.stream()
                .map(segment -> {
                    String pageStr = segment.metadata().getString("pageNumber");
                    int pageNum = (pageStr != null) ? Integer.parseInt(pageStr) : 0;
                    return DisclosureAnalysisResponse.ChunkReference.builder()
                            .chunkId(0L) // Qdrant 사용 시 ID 매핑 로직 필요
                            .pageNumber(pageNum)
                            .excerpt(segment.text().substring(0, Math.min(100, segment.text().length())) + "...")
                            .build();
                })
                .collect(Collectors.toList());

        return DisclosureAnalysisResponse.builder()
                .analysisResult(analysisResult)
                .sources(references)
                .confidenceScore(0.85) // 임시 값
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .tokensUsed(0) // 로컬 모델은 토큰 계산 정확치 않음
                .build();
    }

    private String buildPrompt(DisclosureAnalysisRequest request, String context) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are a financial analyst AI assistant designed to analyze disclosure documents.\n");
        sb.append("Please analyze the following context from a disclosure report to answer the user's question.\n");
        sb.append(
                "If the answer is not in the context, say 'The provided document does not contain information to answer this question.'\n");
        sb.append("Cite the page numbers when referencing specific information.\n\n");

        sb.append("Context:\n");
        sb.append("---------------------\n");
        sb.append(context);
        sb.append("\n---------------------\n\n");

        sb.append("User Question: ").append(request.getQuery()).append("\n");

        if (request.getAnalysisType() != null) {
            sb.append("Analysis Type: ").append(request.getAnalysisType()).append("\n");
            switch (request.getAnalysisType()) {
                case SUMMARY:
                    sb.append("Please provide a concise summary of the key points.\n");
                    break;
                case FINANCIAL_ANALYSIS:
                    sb.append("Focus on financial metrics, revenue, profit, and growth trends.\n");
                    break;
                case RISK_ANALYSIS:
                    sb.append("Focus on potential risks, uncertainties, and negative factors.\n");
                    break;
                default:
                    break;
            }
        }

        sb.append("\nAnswer:");

        return sb.toString();
    }
}
