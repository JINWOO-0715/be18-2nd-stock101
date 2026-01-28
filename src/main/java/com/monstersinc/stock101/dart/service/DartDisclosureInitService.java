package com.monstersinc.stock101.dart.service;

import com.monstersinc.stock101.dart.domain.DartDisclosureEntry;
import com.monstersinc.stock101.dart.dto.DartDisclosureResponse;
import com.monstersinc.stock101.dart.dto.DisclosureInitResult;
import com.monstersinc.stock101.dart.dto.InternalDisclosureResponse;
import com.monstersinc.stock101.dart.model.mapper.DartDisclosureMapper;
import com.monstersinc.stock101.stock.model.mapper.StockMapper;
import com.monstersinc.stock101.stock.model.vo.Stock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * DART 공시정보 초기화 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DartDisclosureInitService {

    private final DartApiService dartApiService;
    private final DartDisclosureMapper dartDisclosureMapper;
    private final StockMapper stockMapper;

    private static final DateTimeFormatter DART_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 특정 회사의 공시정보 초기화 (1년치 A, B, C 타입 통합 저장)
     */
    @Transactional
    public DisclosureInitResult initializeDisclosures(String stockCode) {

        try {
            // 1. Stock 테이블에서 corpCode 조회
            Stock stock = stockMapper.selectStockByCode(stockCode);
            if (stock == null) {
                return DisclosureInitResult.fail("종목을 찾을 수 없습니다: " + stockCode);
            }

            String corpCode = stock.getCorpCode();
            if (!StringUtils.hasText(corpCode)) {
                return DisclosureInitResult.fail("회사 고유번호가 설정되지 않았습니다");
            }

            // 2. DART에서 1년치 통합 공시정보 조회 (A: 정기, B: 주요사항, C: 발행)
            DartDisclosureResponse dartResponse = dartApiService.getDisclosuresByStandardTypes(corpCode);

            if ("error".equals(dartResponse.getStatus())) {
                return DisclosureInitResult.fail("DART API 조회 실패: " + dartResponse.getMessage());
            }

            if (dartResponse.getList() == null || dartResponse.getList().isEmpty()) {
                return DisclosureInitResult.fail("저장할 공시정보가 없습니다.");
            }

            // 3. 공시정보 변환 (DTO -> Domain)
            List<DartDisclosureEntry> entries = new ArrayList<>();
            for (DartDisclosureResponse.DartDisclosure dto : dartResponse.getList()) {
                try {
                    DartDisclosureEntry entry = DartDisclosureEntry.builder()
                            .rceptNo(dto.getRceptNo())
                            .corpCode(dto.getCorpCode())
                            .corpName(dto.getCorpName())
                            .reportNm(dto.getReportNm())
                            .reportType(dto.getPblntfTy()) // 수동 세팅된 타입 정보 저장
                            .receptionDate(LocalDate.parse(dto.getRceptDt(), DART_DATE_FORMAT))
                            .createdAt(java.time.LocalDateTime.now())
                            .build();
                    
                    entries.add(entry);
                } catch (Exception e) {
                    log.warn("공시 데이터 변환 중 스킵: {} - {}", dto.getReportNm(), e.getMessage());
                }
            }

            // 4. DB에 대량 저장 (MyBatis insertDisclosures 호출)
            int insertedCount = 0;
            if (!entries.isEmpty()) {
                insertedCount = dartDisclosureMapper.insertDisclosures(entries);
            }
            
            return DisclosureInitResult.success(
                    "공시정보 초기화 완료",
                    stock.getName(),
                    corpCode,
                    entries.size(),
                    insertedCount
            );

        } catch (Exception e) {
            log.error("공시정보 초기화 중 치명적 예외 발생", e);
            return DisclosureInitResult.fail("초기화 실패: " + e.getMessage());
        }
    }

    /**
     * 공시정보 조회 (없으면 초기화)
     */
    @Transactional
    public InternalDisclosureResponse getOrInitializeDisclosures(String stockCode) {
        try {
            // 1. Stock 정보 조회
            Stock stock = stockMapper.selectStockByCode(stockCode);
            if (stock == null) {
                return InternalDisclosureResponse.fail("종목을 찾을 수 없습니다: " + stockCode);
            }

            String corpCode = stock.getCorpCode();
            if (!StringUtils.hasText(corpCode)) {
                return InternalDisclosureResponse.fail("회사 고유번호가 설정되지 않았습니다");
            }

            // 2. DB에서 공시정보 조회
            List<DartDisclosureEntry> entries = dartDisclosureMapper.selectByCorpCode(corpCode);

            // 3. 데이터가 없으면 초기화
            if (entries == null || entries.isEmpty()) {
                log.info("📋 공시정보가 없어 초기화를 시작합니다: {}", stockCode);
                DisclosureInitResult initResult = initializeDisclosures(stockCode);
                
                if (!initResult.isSuccess()) {
                    return InternalDisclosureResponse.fail(initResult.getMessage());
                }
                
                // 초기화 후 다시 조회
                entries = dartDisclosureMapper.selectByCorpCode(corpCode);
            }

            return InternalDisclosureResponse.success(
                    stock.getName(),
                    corpCode,
                    entries
            );

        } catch (Exception e) {
            log.error("공시정보 조회 중 예외 발생", e);
            return InternalDisclosureResponse.fail("조회 실패: " + e.getMessage());
        }
    }
}