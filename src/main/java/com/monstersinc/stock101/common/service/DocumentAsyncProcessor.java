package com.monstersinc.stock101.common.service;

import com.monstersinc.stock101.common.event.DocumentProcessedEvent;
import com.monstersinc.stock101.disclosure.domain.DisclosureSource;
import com.monstersinc.stock101.disclosure.repository.DisclosureSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;


/**
 * 문서 비동기 처리 서비스
 * 전체적인 처리 '흐름(What)'만 관리하는 오케스트레이터
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentAsyncProcessor {

    private final DocumentVectorizationService documentVectorizationService;
    private final InsightReportService insightReportService;
    private final DisclosureSourceRepository sourceRepository;
    private final ApplicationEventPublisher eventPublisher;


    /**
     * 문서 비동기 처리 메인 흐름
     * - 전체적인 처리 흐름만 관리
     * - 구체적인 처리 방법은 각 전문 서비스에 위임
     *
     * @param sourceId 문서 소스 ID
     * @param filePath PDF 파일 경로
     */
    @Async
    public void processDocumentAsync(Long sourceId, String filePath) {
        log.info("문서 비동기 처리 시작: sourceId={}", sourceId);

        DisclosureSource source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다: sourceId=" + sourceId));

        String stockId = source.getStockId();
        if (stockId == null || stockId.isEmpty()) {
            log.warn("stockId가 없는 문서입니다: sourceId={}", sourceId);
        }

        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            // PDF 유효성 검증
            log.debug("PDF 로드 성공: {} 페이지", document.getNumberOfPages());

            // 1. 문서 벡터화 (추출 → 청킹 → 임베딩)
            documentVectorizationService.vectorizeDocument(sourceId, filePath);

            // 2. 인사이트 리포트 생성 및 저장 (컨텍스트 수집 → LLM 리포트 생성 → 저장)
            // stockId를 전달하여 중복 조회 방지
            insightReportService.generateAndSaveFullReport(sourceId, stockId);

            // 3. 상태 업데이트
            sourceRepository.updateStatus(sourceId, "COMPLETED");
            log.info("문서 처리 완료: sourceId={}", sourceId);

            // 4. 문서 처리 완료 이벤트 발행 (이미 조회한 source 사용)
            publishDocumentProcessedEvent(source, "COMPLETED");

        } catch (Exception e) {
            log.error("문서 비동기 처리 중 오류 발생: sourceId={}", sourceId, e);
            sourceRepository.updateStatus(sourceId, "FAILED");

            // 실패 이벤트 발행 (이미 조회한 source 사용)
            try {
                publishDocumentProcessedEvent(source, "FAILED");
            } catch (Exception eventException) {
                log.error("실패 이벤트 발행 중 오류: sourceId={}", sourceId, eventException);
            }
        }
    }

    /**
     * 문서 처리 완료 이벤트 발행
     *
     * @param source 문서 소스
     * @param status 처리 상태
     */
    private void publishDocumentProcessedEvent(DisclosureSource source, String status) {
        DocumentProcessedEvent event = new DocumentProcessedEvent(
                this,
                source.getSourceId(),
                source.getUserId(),
                source.getCompanyName(),
                status
        );

        eventPublisher.publishEvent(event);
        log.info("문서 처리 이벤트 발행: sourceId={}, status={}", source.getSourceId(), status);
    }



}

