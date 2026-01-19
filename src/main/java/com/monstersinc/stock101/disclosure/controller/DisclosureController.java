package com.monstersinc.stock101.disclosure.controller;

import com.monstersinc.stock101.disclosure.domain.DisclosureDocument;
import com.monstersinc.stock101.disclosure.dto.DisclosureAnalysisRequest;
import com.monstersinc.stock101.disclosure.dto.DisclosureAnalysisResponse;
import com.monstersinc.stock101.disclosure.service.DisclosureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 공시보고서 관리 및 분석 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/disclosure")
@RequiredArgsConstructor
@Tag(name = "Disclosure API", description = "공시보고서 업로드 및 AI 분석 API")
public class DisclosureController {

    private final DisclosureService disclosureService;

    @Operation(summary = "공시보고서 업로드", description = "PDF 공시보고서를 업로드하고 비동기로 처리를 시작합니다.")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DisclosureDocument> uploadDocument(
            @Parameter(description = "PDF 파일") @RequestParam("file") MultipartFile file,
            @Parameter(description = "종목 ID") @RequestParam("stockId") Long stockId,
            @Parameter(description = "업로더 ID") @RequestParam("userId") Long userId) {

        try {
            DisclosureDocument document = disclosureService.uploadDocument(file, stockId, userId);
            return ResponseEntity.ok(document);
        } catch (IOException e) {
            log.error("Failed to upload document", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(summary = "문서 상세 조회", description = "문서 ID로 문서 상태 및 정보를 조회합니다.")
    @GetMapping("/{documentId}")
    public ResponseEntity<DisclosureDocument> getDocument(@PathVariable Long documentId) {
        return ResponseEntity.ok(disclosureService.getDocument(documentId));
    }

    @Operation(summary = "종목별 문서 목록", description = "특정 종목의 공시보고서 목록을 조회합니다.")
    @GetMapping("/stock/{stockId}")
    public ResponseEntity<List<DisclosureDocument>> getDocumentsByStockId(@PathVariable Long stockId) {
        return ResponseEntity.ok(disclosureService.getDocumentsByStockId(stockId));
    }

    @Operation(summary = "문서 AI 분석", description = "공시보고서에 대해 AI에게 질문하고 분석 결과를 받습니다.")
    @PostMapping("/{stockId}/analyze")
    public ResponseEntity<DisclosureAnalysisResponse> analyzeDocument(
            @PathVariable Long stockId,
            @RequestBody DisclosureAnalysisRequest request) {

        // request에 documentId가 없으면 stockId 기준으로 전체 검색하거나 특정 로직 필요
        // 현재는 stockId를 기준으로 해당 종목의 모든 문서(청크)를 대상으로 검색하도록 구현됨
        return ResponseEntity.ok(disclosureService.analyzeDocument(stockId, request));
    }

    @Operation(summary = "문서 삭제", description = "공시보고서 및 관련 데이터를 삭제합니다.")
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long documentId) {
        try {
            disclosureService.deleteDocument(documentId);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            log.error("Failed to delete document", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
