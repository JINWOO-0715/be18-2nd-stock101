package com.monstersinc.stock101.stock.controller;

import com.monstersinc.stock101.stock.model.dto.StockPriceResponseDto;
import com.monstersinc.stock101.stock.service.StockPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * 주식 일봉 데이터 조회 API
 */
@Slf4j
@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Tag(name = "Stock Price", description = "주식 일봉 데이터 조회 API")
public class StockPriceController {

    private final StockPriceService stockPriceService;

    /**
     * 종목의 최근 N일 일봉 데이터 조회
     * - Redis 캐시 확인 후 필요시 KIS API에서 최신 데이터 업데이트
     */
    @GetMapping("/{stockCode}/prices")
    @Operation(summary = "일봉 데이터 조회", description = "종목의 최근 N일 일봉 데이터를 조회합니다. 오늘 처음 조회 시 KIS API에서 최신 데이터를 가져옵니다.")
    public ResponseEntity<StockPriceResponseDto> getDailyPrices(
            @Parameter(description = "종목코드 (6자리)", example = "005930")
            @PathVariable String stockCode,
            @Parameter(description = "조회할 일수 (기본 30일)", example = "30")
            @RequestParam(defaultValue = "30") int days) {

        log.info("📊 일봉 조회 요청: stockCode={}, days={}", stockCode, days);
        StockPriceResponseDto response = stockPriceService.getDailyPrices(stockCode, days);
        return ResponseEntity.ok(response);
    }

    /**
     * 종목의 기간별 일봉 데이터 조회
     */
    @GetMapping("/{stockCode}/prices/period")
    @Operation(summary = "기간별 일봉 데이터 조회", description = "종목의 특정 기간 일봉 데이터를 조회합니다.")
    public ResponseEntity<StockPriceResponseDto> getDailyPricesByPeriod(
            @Parameter(description = "종목코드 (6자리)", example = "005930")
            @PathVariable String stockCode,
            @Parameter(description = "시작일 (yyyy-MM-dd)", example = "2025-01-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "종료일 (yyyy-MM-dd)", example = "2026-01-22")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("📊 기간별 일봉 조회 요청: stockCode={}, {} ~ {}", stockCode, startDate, endDate);
        StockPriceResponseDto response = stockPriceService.getDailyPrices(stockCode, startDate, endDate);
        return ResponseEntity.ok(response);
    }

    /**
     * 캐시 상태 확인
     */
    @GetMapping("/{stockCode}/prices/cache-status")
    @Operation(summary = "캐시 상태 확인", description = "종목의 일봉 데이터 캐시 상태를 확인합니다.")
    public ResponseEntity<Map<String, Object>> getCacheStatus(
            @Parameter(description = "종목코드 (6자리)", example = "005930")
            @PathVariable String stockCode) {

        LocalDate lastUpdate = stockPriceService.getLastUpdateDate(stockCode);
        return ResponseEntity.ok(Map.of(
                "stockCode", stockCode,
                "lastUpdate", lastUpdate != null ? lastUpdate.toString() : "never",
                "needsUpdate", lastUpdate == null || lastUpdate.isBefore(LocalDate.now())
        ));
    }

    /**
     * 캐시 강제 무효화 (관리자용)
     */
    @DeleteMapping("/{stockCode}/prices/cache")
    @Operation(summary = "캐시 무효화", description = "종목의 일봉 데이터 캐시를 강제로 무효화합니다. (관리자용)")
    public ResponseEntity<Map<String, String>> invalidateCache(
            @Parameter(description = "종목코드 (6자리)", example = "005930")
            @PathVariable String stockCode) {

        log.info("🗑️ 캐시 무효화 요청: stockCode={}", stockCode);
        stockPriceService.invalidateCache(stockCode);
        return ResponseEntity.ok(Map.of(
                "message", "캐시 무효화 완료",
                "stockCode", stockCode
        ));
    }
}
