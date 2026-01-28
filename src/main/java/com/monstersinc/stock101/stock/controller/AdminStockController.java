package com.monstersinc.stock101.stock.controller;

import com.monstersinc.stock101.stock.service.StockMstDownloadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 - 종목 데이터 관리 API
 * MST 파일 다운로드 및 동기화 기능 제공
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/stock")
@RequiredArgsConstructor
public class AdminStockController {

    private final StockMstDownloadService stockMstDownloadService;

    /**
     * KOSPI 마스터 파일 즉시 다운로드 및 업데이트
     * GET /api/v1/admin/stock/download-kospi
     */
    @GetMapping("/download-kospi")
    public ResponseEntity<StockMstDownloadService.StockMstUpdateResult> downloadKospi() {
        log.info("KOSPI 마스터 파일 수동 다운로드 요청");
        try {
            var result = stockMstDownloadService.downloadAndUpdateKospi();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("KOSPI 다운로드 실패", e);
            return ResponseEntity.internalServerError()
                    .body(new StockMstDownloadService.StockMstUpdateResult(
                            false,
                            "KOSPI 다운로드 실패: " + e.getMessage(),
                            0
                    ));
        }
    }

    /**
     * KOSDAQ 마스터 파일 즉시 다운로드 및 업데이트
     * GET /api/v1/admin/stock/download-kosdaq
     */
    @GetMapping("/download-kosdaq")
    public ResponseEntity<StockMstDownloadService.StockMstUpdateResult> downloadKosdaq() {
        log.info("KOSDAQ 마스터 파일 수동 다운로드 요청");
        try {
            var result = stockMstDownloadService.downloadAndUpdateKosdaq();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("KOSDAQ 다운로드 실패", e);
            return ResponseEntity.internalServerError()
                    .body(new StockMstDownloadService.StockMstUpdateResult(
                            false,
                            "KOSDAQ 다운로드 실패: " + e.getMessage(),
                            0
                    ));
        }
    }
}
