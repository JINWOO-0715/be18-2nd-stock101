package com.monstersinc.stock101.common.service;

import com.monstersinc.stock101.common.util.MarkdownChunker;
import com.monstersinc.stock101.disclosure.domain.ProcessStatus;
import com.monstersinc.stock101.disclosure.repository.DisclosureSourceRepository;
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
    private final DisclosureSourceRepository sourceRepository;

    /**
     * PDF 파일을 벡터화하여 임베딩 스토어에 저장
     *
     * @param sourceId 문서 소스 ID
     * @param filePath PDF 파일 경로
     * @param resumeFromStatus 재시작할 단계 (null이면 처음부터 시작)
     */
    public void vectorizeDocument(Long sourceId, String filePath, ProcessStatus resumeFromStatus) {
        log.info("문서 벡터화 시작: sourceId={}, filePath={}, resumeFrom={}", sourceId, filePath, resumeFromStatus);

        String fullMarkdown = null;
        List<String> chunks = null;

        try {
            // 1. PDF를 마크다운으로 변환
            // resumeFromStatus가 null이거나 추출 단계 이하에서 실패한 경우
            if (resumeFromStatus == null || resumeFromStatus.isBefore(ProcessStatus.EXTRACTION_FAILED)) {
                sourceRepository.updateStatus(sourceId, ProcessStatus.EXTRACTING.name());
                fullMarkdown = doclingApiService.callAnalyze(filePath);
                log.debug("Docling 변환 완료: {} 문자", fullMarkdown.length());
            }

            // 2. 마크다운 청킹
            // resumeFromStatus가 null이거나 청킹 단계 이하에서 실패한 경우
            if (resumeFromStatus == null || resumeFromStatus.isBefore(ProcessStatus.CHUNKING_FAILED)) {
                sourceRepository.updateStatus(sourceId, ProcessStatus.CHUNKING.name());

                // 재시작인 경우 마크다운을 다시 로드
                if (fullMarkdown == null) {
                    fullMarkdown = doclingApiService.callAnalyze(filePath);
                }

                chunks = markdownChunker.splitByHierarchy(fullMarkdown);
                log.info("마크다운 청킹 완료: {} 개의 청크 생성", chunks.size());
            }

            // 3. 임베딩 및 벡터 DB 저장
            // resumeFromStatus가 null이거나 임베딩 단계 이하에서 실패한 경우
            if (resumeFromStatus == null || resumeFromStatus.isBefore(ProcessStatus.EMBEDDING_FAILED)) {
                sourceRepository.updateStatus(sourceId, ProcessStatus.EMBEDDING.name());

                // 재시작인 경우 청크를 다시 생성
                if (chunks == null) {
                    if (fullMarkdown == null) {
                        fullMarkdown = doclingApiService.callAnalyze(filePath);
                    }
                    chunks = markdownChunker.splitByHierarchy(fullMarkdown);
                }

                for (String chunk : chunks) {
                    embeddingService.processMarkdownChunk(sourceId, chunk);
                }
            }

            log.info("문서 벡터화 완료: sourceId={}, 처리된 청크 수={}", sourceId, chunks != null ? chunks.size() : 0);

        } catch (Exception e) {
            log.error("문서 벡터화 실패: sourceId={}, 현재 단계={}", sourceId, getCurrentStatus(sourceId), e);

            // 실패한 단계에 따라 구체적인 상태 저장
            ProcessStatus currentStatus = getCurrentStatus(sourceId);
            if (currentStatus == ProcessStatus.EXTRACTING) {
                sourceRepository.updateStatus(sourceId, ProcessStatus.EXTRACTION_FAILED.name());
            } else if (currentStatus == ProcessStatus.CHUNKING) {
                sourceRepository.updateStatus(sourceId, ProcessStatus.CHUNKING_FAILED.name());
            } else if (currentStatus == ProcessStatus.EMBEDDING) {
                sourceRepository.updateStatus(sourceId, ProcessStatus.EMBEDDING_FAILED.name());
            }

            throw new RuntimeException("문서 벡터화 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 현재 상태 조회
     */
    private ProcessStatus getCurrentStatus(Long sourceId) {
        return sourceRepository.findById(sourceId)
                .map(source -> ProcessStatus.fromString(source.getStatus()))
                .orElse(ProcessStatus.FAILED);
    }
}
