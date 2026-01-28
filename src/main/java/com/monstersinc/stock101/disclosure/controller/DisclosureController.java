package com.monstersinc.stock101.disclosure.controller;

import com.monstersinc.stock101.disclosure.dto.DisclosureUploadResponse;
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

    /**
     * 공시보고서 업로드
     */
    @Operation(summary = "공시보고서 업로드", description = "PDF 공시보고서를 업로드합니다. 중복 파일은 해시로 체크됩니다.")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DisclosureUploadResponse> uploadDocument(
            @Parameter(description = "PDF 파일") @RequestParam("file") MultipartFile file,
            @Parameter(description = "userId") @RequestParam(required = false,value = "userId") Long userId) {

        try {
            DisclosureUploadResponse response = disclosureService.uploadDocument(file, userId);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Failed to upload document", e);
            return ResponseEntity.internalServerError()
                    .body(DisclosureUploadResponse.builder()
                            .message("업로드 실패: " + e.getMessage())
                            .build());
        }
    }

}
