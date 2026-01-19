package com.monstersinc.stock101.disclosure.service;

import com.monstersinc.stock101.common.service.FileStorageService;
import com.monstersinc.stock101.disclosure.domain.DisclosureDocument;
import com.monstersinc.stock101.disclosure.dto.DisclosureAnalysisRequest;
import com.monstersinc.stock101.disclosure.dto.DisclosureAnalysisResponse;
import com.monstersinc.stock101.disclosure.repository.DisclosureRepository;
import com.monstersinc.stock101.disclosure.util.TextChunker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 공시보고서 비즈니스 로직 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisclosureService {

    private final DisclosureRepository disclosureRepository;
    private final FileStorageService fileStorageService;
    private final PdfProcessingService pdfProcessingService;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;
    private final RagService ragService;

    /**
     * 공시보고서 업로드 및 처리 시작
     */
    @Transactional
    public DisclosureDocument uploadDocument(MultipartFile file, Long stockId, Long userId) throws IOException {
        // 1. 파일 저장
        String filePath = fileStorageService.storeFile(file, stockId);

        // 2. 문서 엔티티 생성 및 저장
        DisclosureDocument document = DisclosureDocument.builder()
                .stockId(stockId)
                .title(file.getOriginalFilename()) // 임시 제목
                .documentType("UNKNOWN") // 사용자가 입력하게 하거나 추후 분석
                .filePath(filePath)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .uploadedBy(userId)
                .processingStatus(DisclosureDocument.ProcessingStatus.UPLOADED)
                .build();

        disclosureRepository.save(document);

        // 3. 비동기 처리 시작
        processDocumentAsync(document.getDocumentId(), filePath);

        return document;
    }

    /**
     * 문서 처리 (텍스트 추출 -> 청킹 -> 임베딩)
     */
    @Async
    public void processDocumentAsync(Long documentId, String filePath) {
        try {
            // 상태 업데이트: PROCESSING
            disclosureRepository.updateStatus(documentId, DisclosureDocument.ProcessingStatus.PROCESSING, null);

            // 1. PDF 텍스트 추출 (페이지별)
            Map<Integer, String> pageTexts = pdfProcessingService.extractTextByPage(filePath);

            // 메타데이터 업데이트 (페이지 수 등)
            Map<String, String> metadata = pdfProcessingService.extractMetadata(filePath);
            String title = metadata.getOrDefault("title", "Untitled");

            // 2. 텍스트 청킹
            List<TextChunker.TextChunk> chunks = textChunker.chunkTextByPages(pageTexts);

            // 3. 임베딩 생성 및 저장
            embeddingService.embedAndSaveChunks(documentId, chunks);

            // 4. 상태 업데이트: COMPLETED
            disclosureRepository.updateMetadata(documentId,
                    pdfProcessingService.getPageCount(filePath),
                    chunks.size(),
                    convertMapToJson(metadata)); // JSON 변환 필요

            log.info("Document {} processed successfully with {} chunks", documentId, chunks.size());

        } catch (Exception e) {
            log.error("Error processing document {}", documentId, e);
            disclosureRepository.updateStatus(documentId, DisclosureDocument.ProcessingStatus.FAILED, e.getMessage());
        }
    }

    /**
     * 문서 분석 요청
     */
    public DisclosureAnalysisResponse analyzeDocument(Long stockId, DisclosureAnalysisRequest request) {
        // 간단한 검증: 해당 종목에 문서가 있는지 확인 등
        // 여기서는 바로 RAG 서비스 호출
        return ragService.analyzeDocument(stockId, request);
    }

    /**
     * 종목별 문서 목록 조회
     */
    public List<DisclosureDocument> getDocumentsByStockId(Long stockId) {
        return disclosureRepository.findByStockId(stockId);
    }

    /**
     * 문서 상세 조회
     */
    public DisclosureDocument getDocument(Long documentId) {
        return disclosureRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
    }

    /**
     * 문서 삭제
     */
    @Transactional
    public void deleteDocument(Long documentId) throws IOException {
        DisclosureDocument document = getDocument(documentId);

        // DB 삭제 (Cascade 설정에 의해 청크도 삭제됨 - MyBatis에서는 별도 처리 필요할 수 있음)
        // 여기서는 명시적으로 청크 리포지토리 호출 로직이 필요할 수 있으나, DB ON DELETE CASCADE가 있다면 생략 가능
        // 하지만 서비스 레이어에서 처리하는 것이 안전함. (DocumentChunkRepository.deleteByDocumentId 호출 필요할
        // 수 있음)
        disclosureRepository.deleteById(documentId);

        // 파일 삭제
        fileStorageService.deleteFile(document.getFilePath());
    }

    // 헬퍼 메서드: Map -> JSON String (간단 구현)
    private String convertMapToJson(Map<String, String> map) {
        return map.entrySet().stream()
                .map(e -> String.format("\"%s\":\"%s\"", e.getKey(), e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }
}
