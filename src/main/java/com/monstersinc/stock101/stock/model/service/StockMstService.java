package com.monstersinc.stock101.stock.model.service;

import com.monstersinc.stock101.stock.model.dto.StockMstDto;
import com.monstersinc.stock101.stock.model.mapper.StockMapper;
import com.monstersinc.stock101.stock.model.vo.Stock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * MST 파일을 파싱하고 Stock 데이터를 업데이트하는 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockMstService {
    private final StockMapper stockMapper;
    
    /**
     * MST 파일을 읽어서 Stock 데이터 업데이트
     * All-or-nothing: 하나라도 실패하면 전체 롤백
     * 
     * @param file 업로드된 mst 파일
     * @return 처리된 종목 수
     */
    @Transactional
    public int updateStocksFromMst(MultipartFile file) {
        List<StockMstDto> mstList = parseMstFile(file);
        
        int updatedCount = 0;
        
        for (StockMstDto mst : mstList) {
            // 기존 종목이 있는지 확인
            Stock existingStock = stockMapper.selectStockByCode(mst.getMkscShrnIscd());
            
            if (existingStock != null) {
                // 업데이트: MST 정보로 기존 종목 정보 갱신
                Stock updateStock = Stock.builder()
                        .stockId(existingStock.getStockId())
                        .name(mst.getHtsKorIsnm())
                        .stockCode(mst.getMkscShrnIscd())
                        .stdCode(mst.getStndIscd())
                        .securityType(mst.getScrtGrpClsCode())
                        .marketType(parseMarketType(mst.getKospiIssuYn()))
                        .largeIndustryCode(mst.getBstpLargDivCode())
                        .mediumIndustryCode(mst.getBstpMedmDivCode())
                        .smallIndustryCode(mst.getBstpSmalDivCode())
                        .kospi200SectorCode(mst.getKospi200ApntClsCode())
                        .listingDate(mst.getStckLstnDate())
                        .capitalAmount(parseLong(mst.getCpfn()))
                        .faceValue(parseLong(mst.getStckFcam()))
                        .ipoPrice(parseLong(mst.getPoPrc()))
                        .preferredStockCode(mst.getPrstClsCode())
                        .krx300Yn(mst.getKrx300IssuYn())
                        .kospiYn(mst.getKospiIssuYn())
                        .kospi100Yn(mst.getKospi100IssuYn())
                        .kospi50Yn(mst.getKospi50IssuYn())
                        .isManaged(parseYN(mst.getMangIssuYn()))
                        .isSuspended(parseYN(mst.getTrhtYn()))
                        .salesAmount(parseLong(mst.getSaleAccount()))
                        .operatingProfit(parseLong(mst.getBsopPrfi()))
                        .netIncome(parseLong(mst.getThtrNtin()))
                        .roe(parseDouble(mst.getRoe()))
                        .baseDate(mst.getBaseDate())
                        .marketCap(parseLong(mst.getPrdyAvlsScal()))
                        // 기존 지표는 유지
                        .individualIndicator(existingStock.getIndividualIndicator())
                        .analystIndicator(existingStock.getAnalystIndicator())
                        .newsIndicator(existingStock.getNewsIndicator())
                        .build();
                
                stockMapper.updateStockBasicInfo(updateStock);
                log.debug("Updated stock: {}", mst.getMkscShrnIscd());
            } else {
                // 신규 삽입 (MST 정보로 새 종목 생성)
                Stock newStock = Stock.builder()
                        .name(mst.getHtsKorIsnm())
                        .stockCode(mst.getMkscShrnIscd())
                        .stdCode(mst.getStndIscd())
                        .securityType(mst.getScrtGrpClsCode())
                        .marketType(parseMarketType(mst.getKospiIssuYn()))
                        .largeIndustryCode(mst.getBstpLargDivCode())
                        .mediumIndustryCode(mst.getBstpMedmDivCode())
                        .smallIndustryCode(mst.getBstpSmalDivCode())
                        .kospi200SectorCode(mst.getKospi200ApntClsCode())
                        .listingDate(mst.getStckLstnDate())
                        .capitalAmount(parseLong(mst.getCpfn()))
                        .faceValue(parseLong(mst.getStckFcam()))
                        .ipoPrice(parseLong(mst.getPoPrc()))
                        .preferredStockCode(mst.getPrstClsCode())
                        .krx300Yn(mst.getKrx300IssuYn())
                        .kospiYn(mst.getKospiIssuYn())
                        .kospi100Yn(mst.getKospi100IssuYn())
                        .kospi50Yn(mst.getKospi50IssuYn())
                        .isManaged(parseYN(mst.getMangIssuYn()))
                        .isSuspended(parseYN(mst.getTrhtYn()))
                        .salesAmount(parseLong(mst.getSaleAccount()))
                        .operatingProfit(parseLong(mst.getBsopPrfi()))
                        .netIncome(parseLong(mst.getThtrNtin()))
                        .roe(parseDouble(mst.getRoe()))
                        .baseDate(mst.getBaseDate())
                        .marketCap(parseLong(mst.getPrdyAvlsScal()))
                        // 기본값 설정
                        .individualIndicator("HOLD")
                        .analystIndicator("HOLD")
                        .newsIndicator("NEUTRAL")
                        .build();
                
                stockMapper.insertStock(newStock);
                log.debug("Inserted new stock: {}", mst.getMkscShrnIscd());
            }
            updatedCount++;
        }
        
        log.info("MST file processing completed: {} stocks processed successfully", updatedCount);
        return updatedCount;
    }
    
    /**
     * MST 파일 파싱
     * 
     * @param file mst 파일
     * @return 파싱된 종목 리스트
     */
    private List<StockMstDto> parseMstFile(MultipartFile file) {
        List<StockMstDto> result = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), Charset.forName("EUC-KR")))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    StockMstDto dto = StockMstDto.parseLine(line);
                    if (dto != null && dto.getMkscShrnIscd() != null && !dto.getMkscShrnIscd().isEmpty()) {
                        result.add(dto);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse line: {}", line, e);
                }
            }
            
            log.info("Parsed {} stocks from mst file", result.size());
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to read mst file", e);
        }
        
        return result;
    }
    
    /**
     * 문자열을 Long으로 안전하게 변환
     */
    private Long parseLong(String value) {
        if (value == null || value.trim().isEmpty() || value.equals("0")) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse long value: {}", value);
            return null;
        }
    }
    
    /**
     * 문자열을 Double로 안전하게 변환
     */
    private Double parseDouble(String value) {
        if (value == null || value.trim().isEmpty() || value.equals("0")) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse double value: {}", value);
            return null;
        }
    }
    
    /**
     * KOSPI 여부로 시장 구분 판단
     * Y -> KOSPI, N -> KOSDAQ, 그 외 -> null (DB에서 NULL 허용)
     */
    private String parseMarketType(String kospiIssuYn) {
        if ("Y".equalsIgnoreCase(kospiIssuYn)) {
            return "KOSPI";
        } else if ("N".equalsIgnoreCase(kospiIssuYn)) {
            return "KOSDAQ";
        }
        return null; // ENUM에 없는 값 대신 NULL 반환
    }
    
    /**
     * Y/N 값을 정규화 (빈 문자열이나 null은 'N'으로 변환)
     */
    private String parseYN(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "N";
        }
        return "Y".equalsIgnoreCase(value.trim()) ? "Y" : "N";
    }
}