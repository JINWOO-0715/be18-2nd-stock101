package com.monstersinc.stock101.dart.controller;

import com.monstersinc.stock101.dart.dto.CorpCodeSyncResult;
import com.monstersinc.stock101.dart.dto.DartDisclosureRequest;
import com.monstersinc.stock101.dart.dto.DartDisclosureResponse;
import com.monstersinc.stock101.dart.dto.DisclosureInitResult;
import com.monstersinc.stock101.dart.dto.InternalDisclosureResponse;
import com.monstersinc.stock101.dart.service.DartApiService;
import com.monstersinc.stock101.dart.service.DartCorpCodeService;
import com.monstersinc.stock101.dart.service.DartDisclosureInitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


/**
 * DART(전자공시시스템) API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dart")
@RequiredArgsConstructor
@Tag(name = "DART API", description = "금융감독원 전자공시시스템(DART) 연동 API")
public class DartController {


    private final DartApiService dartApiService;
    private final DartCorpCodeService dartCorpCodeService;
    private final DartDisclosureInitService dartDisclosureInitService;

    /**
     * DART 회사 고유번호 동기화 (수동 실행)
     */
    @Operation(summary = "DART 고유번호 동기화", description = "DART에서 corpCode.zip을 다운로드하고 Stock 테이블의 고유번호를 업데이트합니다.")
    @PostMapping("/corp-code/sync")
    public ResponseEntity<CorpCodeSyncResult> syncCorpCodes() {
        CorpCodeSyncResult result = dartCorpCodeService.syncCorpCodes();
        return ResponseEntity.ok(result);
    }

    /**
     * 공시정보 초기화 (1년치 정기공시, 중요사항보고, 발행공시)
     */
    @Operation(summary = "공시정보 초기화", description = "특정 종목의 1년치 공시정보를 DART에서 조회하여 초기화합니다. (정기공시, 중요사항보고, 발행공시 3가지 타입)")
    @PostMapping("/disclosures/init/{stockCode}")
    public ResponseEntity<DisclosureInitResult> initializeDisclosures(
            @PathVariable String stockCode) {
        DisclosureInitResult result = dartDisclosureInitService.initializeDisclosures(stockCode);
        
        if (result.isSuccess()) {
            log.info("📋 초기화 성공: {} ({}) - {} 건", 
                    result.getStockName(), stockCode, result.getSavedDisclosures());
        } else {
            log.warn("📋 초기화 실패: {}", result.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 내부 공시 검색 : dart는 api호출 한계가 있으므로 아래를 사용한다. 
     */
    @Operation(summary = "내부 공시 검색", description = "저장된 공시 정보를 조회합니다. 데이터가 없으면 자동으로 초기화합니다.")
    @GetMapping("/disclosures/{stockCode}")
    public ResponseEntity<InternalDisclosureResponse> getInternalDisclosures(@PathVariable String stockCode) {
        InternalDisclosureResponse response = dartDisclosureInitService.getOrInitializeDisclosures(stockCode);
        return ResponseEntity.ok(response);
    }
    

    

}
