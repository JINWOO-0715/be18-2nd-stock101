package com.monstersinc.stock101.kis.controller;

import com.monstersinc.stock101.kis.dto.UpdateResponse;
import com.monstersinc.stock101.kis.queue.RequestStatus;
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
     * [수정] 주식 시세 데이터 동기화
     * - 1-2회 API 호출: 동기 처리 (즉시 완료)
     * - 3회 이상 API 호출: 비동기 처리 (requestId 반환)
     *
     * @param stockCode 종목코드 (6자리)
     * @return 업데이트 응답 (동기/비동기 정보 포함)
     */
    @Operation(
        summary = "주식 시세 데이터 동기화",
        description = "KIS API에서 종목의 일봉 데이터를 조회하여 DB에 저장합니다. " +
                     "대량 조회(3회 이상 API 호출)는 비동기 Queue로 처리됩니다."
    )
    @PostMapping("/stock-prices/sync/{stockCode}")
    public ResponseEntity<UpdateResponse> syncStockPrices(@PathVariable String stockCode) {
        try {
            UpdateResponse response = kisStockPriceService.updateStockPrices(stockCode);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("시세 동기화 실패: stockCode={}, error={}", stockCode, e.getMessage());
            throw new RuntimeException("시세 동기화 실패: " + e.getMessage(), e);
        }
    }

    /**
     * [신규] Queue 상태 조회
     * 비동기 처리된 요청의 진행 상황을 확인합니다.
     *
     * @param requestId 요청 ID
     * @return 처리 상태
     */
    @Operation(
        summary = "Queue 처리 상태 조회",
        description = "비동기로 처리 중인 시세 업데이트 요청의 상태를 조회합니다."
    )
    @GetMapping("/stock-prices/status/{requestId}")
    public ResponseEntity<Map<String, Object>> getRequestStatus(@PathVariable String requestId) {
        RequestStatus status = kisStockPriceService.getRequestStatus(requestId);

        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);
        response.put("status", status.name());
        response.put("description", getStatusDescription(status));

        return ResponseEntity.ok(response);
    }

    private String getStatusDescription(RequestStatus status) {
        return switch (status) {
            case QUEUED -> "대기 중";
            case PROCESSING -> "처리 중";
            case COMPLETED -> "완료";
            case FAILED -> "실패";
            case NOT_FOUND -> "요청을 찾을 수 없습니다 (만료되었거나 존재하지 않음)";
        };
    }
}
