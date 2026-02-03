package com.monstersinc.stock101.common.service;

import com.monstersinc.stock101.common.event.DocumentProcessedEvent;
import com.monstersinc.stock101.disclosure.domain.DisclosureSource;
import com.monstersinc.stock101.disclosure.domain.ProcessStatus;
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
     * - 실패한 단계부터 재시작 지원
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

        // 현재 상태를 확인하여 재시작 지점 결정
        ProcessStatus currentStatus = ProcessStatus.fromString(source.getStatus());
        ProcessStatus resumeFromStatus = null;

        if (currentStatus.isFailed()) {
            resumeFromStatus = currentStatus;
            log.info("이전 실패 지점부터 재시작: sourceId={}, resumeFrom={}", sourceId, resumeFromStatus);
        }

        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            // PDF 유효성 검증
            log.debug("PDF 로드 성공: {} 페이지", document.getNumberOfPages());

            // 1. 문서 벡터화 (추출 → 청킹 → 임베딩)
            // 리포트 생성 실패가 아닌 경우 벡터화 수행
            if (resumeFromStatus == null || resumeFromStatus.isBefore(ProcessStatus.REPORT_FAILED)) {
                documentVectorizationService.vectorizeDocument(sourceId, filePath, resumeFromStatus);
            }

            // 2. 인사이트 리포트 생성 및 저장
            sourceRepository.updateStatus(sourceId, ProcessStatus.GENERATING_REPORT.name());
            insightReportService.generateAndSaveFullReport(sourceId, stockId);

            // 3. 상태 업데이트
            sourceRepository.updateStatus(sourceId, ProcessStatus.COMPLETED.name());
            log.info("문서 처리 완료: sourceId={}", sourceId);

            // 4. 문서 처리 완료 이벤트 발행
            publishDocumentProcessedEvent(source, ProcessStatus.COMPLETED.name());

        } catch (Exception e) {
            log.error("문서 비동기 처리 중 오류 발생: sourceId={}", sourceId, e);

            // 현재 상태를 확인하여 적절한 실패 상태 설정
            ProcessStatus failedStatus = getCurrentFailedStatus(sourceId);
            sourceRepository.updateStatus(sourceId, failedStatus.name());

            // 실패 이벤트 발행
            try {
                publishDocumentProcessedEvent(source, failedStatus.name());
            } catch (Exception eventException) {
                log.error("실패 이벤트 발행 중 오류: sourceId={}", sourceId, eventException);
            }
        }
    }

    /**
     * 현재 상태에 따라 적절한 실패 상태 반환
     */
    private ProcessStatus getCurrentFailedStatus(Long sourceId) {
        return sourceRepository.findById(sourceId)
                .map(source -> {
                    ProcessStatus status = ProcessStatus.fromString(source.getStatus());
                    // 리포트 생성 중이었다면 리포트 실패
                    if (status == ProcessStatus.GENERATING_REPORT) {
                        return ProcessStatus.REPORT_FAILED;
                    }
                    // 이미 구체적인 실패 상태라면 그대로 반환
                    if (status.isFailed()) {
                        return status;
                    }
                    // 그 외는 일반 실패
                    return ProcessStatus.FAILED;
                })
                .orElse(ProcessStatus.FAILED);
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

