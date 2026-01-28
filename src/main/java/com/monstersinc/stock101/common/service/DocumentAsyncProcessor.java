package com.monstersinc.stock101.common.service;

import com.monstersinc.stock101.common.util.MarkdownChunker;
import com.monstersinc.stock101.disclosure.repository.AIDisclosureReportRepository;
import com.monstersinc.stock101.disclosure.repository.DisclosureSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 문서 비동기 처리 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentAsyncProcessor {

    private final EmbeddingService embeddingService;
    private final DisclosureSourceRepository sourceRepository;
    private final AIDisclosureReportRepository aiDisclosureReportRepository;
    private final InsightService insightService;
    private final DoclingApiService doclingApiService;
    private final MarkdownChunker markdownChunker;


    @Async // 별도 설정한 쓰레드풀 이름
    public void processDocumentAsync(Long sourceId, String filePath) {
        try (PDDocument document = Loader.loadPDF(new File(filePath))) {

            //docling 호출
            String fullMarkdown = doclingApiService.callAnalyze(filePath);

            // 마크 다운 주제 및 계층별로 분류
            List<String> chunks = markdownChunker.splitByHierarchy(fullMarkdown);

            // 각 청크별로 처리
            for (String chunk : chunks) {
                // 임베딩 및 저장
                embeddingService.processMarkdownChunk(sourceId,  chunk);
            }

            //  요약/전망에 필요한 핵심 컨텍스트만 다시 수집 (Vector Search)
            String relevantContext = insightService.collectContextForReport(sourceId);

            // LLM report생성
            String finalReport = insightService.generateFullInsightReport(relevantContext);

            // 결과 저장 및 상태 업데이트
            insightService.saveInsightReport(sourceId, finalReport);

            sourceRepository.updateStatus(sourceId, "COMPLETED");

        } catch (Exception e) {
            log.error("비동기 처리 중 에러 발생: sourceId={}", sourceId, e);
            sourceRepository.updateStatus(sourceId, "FAILED");
        }
    }



}

