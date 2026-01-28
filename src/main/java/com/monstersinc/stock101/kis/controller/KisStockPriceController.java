package com.monstersinc.stock101.kis.controller;

import com.monstersinc.stock101.kis.service.KisStockPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 한국투자증권(KIS) API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/kis")
@RequiredArgsConstructor
@Tag(name = "KIS API", description = "한국투자증권 시세 데이터 연동 API")
public class KisStockPriceController {

    private final KisStockPriceService kisStockPriceService;

    /**
     * 특정 종목의 3년치 일봉 데이터를 가져와 DB에 저장
     * 
     * @param stockCode 종목코드 (6자리)
     * @return 저장된 데이터 건수
     */
    @Operation(summary = "주식 시세 데이터 동기화", description = "KIS API에서 특정 종목의 1년치 일봉 데이터를 조회하여 DB에 저장합니다.")
    @PostMapping("/stock-prices/sync/{stockCode}")
    public ResponseEntity<Map<String, Object>> syncStockPrices(@PathVariable String stockCode) {
        try {

            int savedCount = kisStockPriceService.updateStockPrices(stockCode);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "시세 데이터 동기화 완료");
            response.put("stockCode", stockCode);
            response.put("savedCount", savedCount);

            return ResponseEntity.ok(response);

        } catch (Exception e) {

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "동기화 실패: " + e.getMessage());
            errorResponse.put("stockCode", stockCode);

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
