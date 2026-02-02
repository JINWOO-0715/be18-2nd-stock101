package com.monstersinc.stock101.disclosure.controller;

import com.monstersinc.stock101.disclosure.domain.AIDisclosureReport;
import com.monstersinc.stock101.disclosure.dto.DisclosureUploadResponse;
import com.monstersinc.stock101.disclosure.service.AIReportService;
import com.monstersinc.stock101.disclosure.service.DisclosureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;


/**
 * 공시보고서 관리 및 분석 컨트롤러
 * 테이블: disclosure_source
*/
@Slf4j
@RestController
@RequestMapping("/api/disclosure")
@RequiredArgsConstructor
@Tag(name = "Disclosure API", description = "공시보고서 업로드 및 AI 분석 API")
public class DisclosureController {

    private final DisclosureService disclosureService;
    private final AIReportService aiReportService;

    /**
     * 공시보고서 업로드
     */
    @Operation(summary = "공시보고서 업로드", description = "PDF 공시보고서를 업로드합니다. 중복 파일은 해시로 체크됩니다.")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DisclosureUploadResponse> uploadDocument(
            @Parameter(description = "PDF 파일") @RequestParam("file") MultipartFile file,
            @Parameter(description = "주식 ID (선택)") @RequestParam(value = "stockId", required = false) String stockId,
            @AuthenticationPrincipal Long userId) {

        try {
            DisclosureUploadResponse response = disclosureService.uploadDocument(file, userId, stockId);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Failed to upload document", e);

            return ResponseEntity.internalServerError()
                    .body(DisclosureUploadResponse.builder()
                            .message("업로드 실패: " + e.getMessage())
                            .build());
        }
    }

    /**
     * 최신 AI 리포트 목록 조회
     */
    @Operation(summary = "최신 AI 리포트 목록 조회", description = "최신 AI 분석 리포트 목록을 최신순으로 조회합니다. 기본 5개, 최대 50개까지 조회 가능합니다.")
    @GetMapping("/reports/recent")
    public ResponseEntity<List<AIDisclosureReport>> getRecentReports(
            @Parameter(description = "조회할 개수 (기본값: 5, 최대: 50)")
            @RequestParam(defaultValue = "5") int limit) {

        // 유효성 검사: 최대 50개, 최소 1개로 제한
        if (limit > 50) {
            limit = 50;
        }
        if (limit < 1) {
            limit = 5;
        }

        log.info("최신 AI 리포트 목록 조회 요청: limit={}", limit);
        List<AIDisclosureReport> reports = aiReportService.getRecentReports(limit);
        return ResponseEntity.ok(reports);
    }

    /**
     * stock_id로 AI 리포트 목록 조회
     */
    @Operation(summary = "AI 리포트 목록 조회", description = "특정 주식의 AI 분석 리포트 목록을 조회합니다.")
    @GetMapping("/reports/stock/{stockId}")
    public ResponseEntity<List<AIDisclosureReport>> getReportsByStockId(
            @Parameter(description = "주식 ID") @PathVariable String stockId) {

        log.info("AI 리포트 목록 조회 요청: stockId={}", stockId);
        List<AIDisclosureReport> reports = aiReportService.getReportsByStockId(stockId);
        return ResponseEntity.ok(reports);
    }

    /**
     * report_id로 AI 리포트 단건 조회
     */
    @Operation(summary = "AI 리포트 단건 조회", description = "리포트 ID로 AI 분석 리포트를 조회합니다.")
    @GetMapping("/reports/{reportId}")
    public ResponseEntity<AIDisclosureReport> getReportById(
            @Parameter(description = "리포트 ID") @PathVariable Long reportId) {

        log.info("AI 리포트 단건 조회 요청: reportId={}", reportId);
        AIDisclosureReport report = aiReportService.getReportById(reportId);
        return ResponseEntity.ok(report);
    }

}
