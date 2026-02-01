package com.monstersinc.stock101.common.service;

import com.monstersinc.stock101.common.util.MarkdownChunker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 문서 벡터화 서비스
 * 문서 추출부터 임베딩 저장까지의 '방법(How)'을 담당
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentVectorizationService {

    private final DoclingApiService doclingApiService;
    private final MarkdownChunker markdownChunker;
    private final EmbeddingService embeddingService;

    /**
     * PDF 파일을 벡터화하여 임베딩 스토어에 저장
     *
     * @param sourceId 문서 소스 ID
     * @param filePath PDF 파일 경로
     */
    public void vectorizeDocument(Long sourceId, String filePath) {
        log.info("문서 벡터화 시작: sourceId={}, filePath={}", sourceId, filePath);

        try {
            // 1. Docling API를 통해 PDF를 마크다운으로 변환
            String fullMarkdown = doclingApiService.callAnalyze(filePath);
            log.debug("Docling 변환 완료: {} 문자", fullMarkdown.length());

            // 2. 마크다운을 계층별로 분할
            List<String> chunks = markdownChunker.splitByHierarchy(fullMarkdown);
            log.info("마크다운 청킹 완료: {} 개의 청크 생성", chunks.size());

            // 3. 각 청크를 임베딩하여 벡터 DB에 저장
            for (String chunk : chunks) {
                embeddingService.processMarkdownChunk(sourceId, chunk);
            }

            log.info("문서 벡터화 완료: sourceId={}, 처리된 청크 수={}", sourceId, chunks.size());

        } catch (Exception e) {
            log.error("문서 벡터화 실패: sourceId={}", sourceId, e);
            throw new RuntimeException("문서 벡터화 중 오류가 발생했습니다.", e);
        }
    }
}
