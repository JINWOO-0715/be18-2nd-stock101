package com.monstersinc.stock101.stock.service;

import com.monstersinc.stock101.common.util.ZipUtil;
import com.monstersinc.stock101.stock.model.dto.StockMstDto;
import com.monstersinc.stock101.stock.model.mapper.StockMapper;
import com.monstersinc.stock101.stock.model.vo.Stock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.net.URLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 한국투자증권 MST 파일 자동 다운로드 서비스
 * KOSPI/KOSDAQ 마스터 파일을 주기적으로 다운로드하여 DB에 동기화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockMstDownloadService {

    private final StockMapper stockMapper;

    private static final String KOSPI_URL = "https://new.real.download.dws.co.kr/common/master/kospi_code.mst.zip";
    private static final String KOSDAQ_URL = "https://new.real.download.dws.co.kr/common/master/kosdaq_code.mst.zip";
    private static final String DOWNLOAD_DIR = "temp_mst";
    private static final int TIMEOUT_MS = 30000; // 30초

    /**
     * KOSPI 종목 다운로드 및 업데이트
     */
    @Transactional
    public StockMstUpdateResult downloadAndUpdateKospi() {
        log.info("Starting KOSPI MST file download and update");
        try {
            return downloadAndUpdateMst(KOSPI_URL, "KOSPI");
        } catch (Exception e) {
            log.error("Failed to download and update KOSPI", e);
            return new StockMstUpdateResult(false, "KOSPI 다운로드 실패: " + e.getMessage(), 0);
        }
    }

    /**
     * KOSDAQ 종목 다운로드 및 업데이트
     */
    @Transactional
    public StockMstUpdateResult downloadAndUpdateKosdaq() {
        log.info("Starting KOSDAQ MST file download and update");
        try {
            return downloadAndUpdateMst(KOSDAQ_URL, "KOSDAQ");
        } catch (Exception e) {
            log.error("Failed to download and update KOSDAQ", e);
            return new StockMstUpdateResult(false, "KOSDAQ 다운로드 실패: " + e.getMessage(), 0);
        }
    }

    /**
     * 공통 다운로드 및 업데이트 로직
     */
    @Transactional
    private StockMstUpdateResult downloadAndUpdateMst(String urlStr, String market) throws Exception {
        // 다운로드 디렉토리 생성
        Path downloadPath = Paths.get(DOWNLOAD_DIR);
        if (!Files.exists(downloadPath)) {
            Files.createDirectories(downloadPath);
        }

        // ZIP 파일 다운로드 (공통 유틸 사용)
        File zipFile = new File(downloadPath.toFile(), market + "_code.mst.zip");
        ZipUtil.downloadFile(urlStr, zipFile, TIMEOUT_MS);
        log.info("{} MST ZIP 파일 다운로드 완료: {}", market, zipFile.getAbsolutePath());

        // ZIP 파일에서 MST 파일 추출 (공통 유틸 사용)
        File mstFile = ZipUtil.extractFirstByExtension(zipFile, ".mst", downloadPath);
        if (mstFile == null) {
            throw new Exception("ZIP 파일에서 .mst 파일을 찾을 수 없습니다");
        }
        log.info("{} MST 파일 추출 완료: {}", market, mstFile.getAbsolutePath());

        // MST 파일 파싱 및 DB 업데이트
        int updatedCount = updateStocksFromMst(mstFile, market);
        log.info("{} 종목 데이터 업데이트 완료: {} 개", market, updatedCount);

        // 임시 파일 정리
        zipFile.delete();
        mstFile.delete();

        return new StockMstUpdateResult(true, market + " 마스터 파일 업데이트 완료", updatedCount);
    }

    // ZIP 다운로드/해제 로직은 공통 유틸로 이동 (ZipUtil)

    /**
     * MST 파일 파싱 및 DB 업데이트
     */
    @Transactional
    private int updateStocksFromMst(File mstFile, String market) throws Exception {
        int updatedCount = 0;

        try (FileInputStream fis = new FileInputStream(mstFile);
                InputStreamReader isr = new InputStreamReader(fis, Charset.forName("EUC-KR"));
                BufferedReader reader = new BufferedReader(isr)) {

            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    StockMstDto stockData = StockMstDto.parseLine(line);
                    if (stockData != null && "ST".equals(trimToNull(stockData.getScrtGrpClsCode()))
                            && !stockData.getHtsKorIsnm().contains("스팩")) {

                        // 기존 데이터 확인 후 INSERT 또는 UPDATE
                        var existingStock = stockMapper.selectStockByCode(stockData.getMkscShrnIscd());

                        Stock stock = convertDtoToEntity(stockData);

                        if (existingStock == null) {
                            // 새로운 종목 추가
                            stockMapper.insertStock(stock);
                        } else {
                            // 기존 종목 업데이트 (stockId 유지)
                            stock.setStockId(existingStock.getStockId());
                            stockMapper.updateStockBasicInfo(stock);
                        }
                        updatedCount++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse line: {}", line, e);
                    // 단일 라인 실패는 무시하고 계속 진행
                }
            }
        }

        return updatedCount;
    }

    /**
     * StockMstDto를 Stock 엔티티로 변환
     */
    private Stock convertDtoToEntity(StockMstDto dto) {
        Stock stock = new Stock();

        // 기본 정보
        stock.setStockCode(trimToNull(dto.getMkscShrnIscd()));
        stock.setStdCode(trimToNull(dto.getStndIscd()));
        stock.setName(trimToNull(dto.getHtsKorIsnm()));
        stock.setCorpCode(null); // MST에는 DART 고유번호 없음
        stock.setMarketType(parseMarketType(dto.getKospiIssuYn()));
        stock.setSecurityType(trimToNull(dto.getScrtGrpClsCode()));

        // 업종 분류
        stock.setLargeIndustryCode(trimToNull(dto.getBstpLargDivCode()));
        stock.setMediumIndustryCode(trimToNull(dto.getBstpMedmDivCode()));
        stock.setSmallIndustryCode(trimToNull(dto.getBstpSmalDivCode()));
        stock.setKospi200SectorCode(trimToNull(dto.getKospi200ApntClsCode()));

        // 지수 포함 여부
        stock.setKospi100Yn(normalizeYn(dto.getKospi100IssuYn()));
        stock.setKospi50Yn(normalizeYn(dto.getKospi50IssuYn()));
        stock.setKrx300Yn(normalizeYn(dto.getKrx300IssuYn()));
        stock.setKospi300Yn(normalizeYn(dto.getKrx300IssuYn()));
        stock.setKospiYn(normalizeYn(dto.getKospiIssuYn()));

        // 상장 정보 (숫자 변환 안전 처리)
        stock.setFaceValue(parseLongSafe(dto.getStckFcam()));
        stock.setListingDate(formatDateSafe(dto.getStckLstnDate()));
        stock.setCapitalAmount(parseLongSafe(dto.getCpfn()));
        stock.setIpoPrice(parseLongSafe(dto.getPoPrc()));
        stock.setPreferredStockCode(trimToNull(dto.getPrstClsCode()));

        // 재무 정보
        stock.setSalesAmount(parseLongSafe(dto.getSaleAccount()));
        stock.setOperatingProfit(parseLongSafe(dto.getBsopPrfi()));
        stock.setNetIncome(parseLongSafe(dto.getThtrNtin()));
        stock.setRoe(parseDoubleSafe(dto.getRoe()));
        stock.setBaseDate(trimToNull(dto.getBaseDate()));
        stock.setMarketCap(parseLongSafe(dto.getPrdyAvlsScal()));

        // 상태 정보
        stock.setIsManaged(normalizeYn(dto.getMangIssuYn()));
        stock.setIsSuspended(normalizeYn(dto.getTrhtYn()));

        // 현재가 정보 (MST 파일에는 없으므로 기본값 설정)
        stock.setPrice(0.0);
        stock.setFluctuation(0.0);

        // 지표 정보 (MST 파일에는 없으므로 기본값 설정)
        stock.setIndividualIndicator("HOLD");
        stock.setAnalystIndicator("HOLD");
        stock.setNewsIndicator("NEUTRAL");

        return stock;
    }

    /**
     * 공백 문자열을 null로 정규화
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * KOSPI 여부로 시장 구분 판단
     * Y -> KOSPI, N -> KOSDAQ, 그 외 -> null
     */
    private String parseMarketType(String kospiIssuYn) {
        if ("Y".equalsIgnoreCase(trimToNull(kospiIssuYn))) {
            return "KOSPI";
        }
        if ("N".equalsIgnoreCase(trimToNull(kospiIssuYn))) {
            return "KOSDAQ";
        }
        return null;
    }

    /**
     * Y/N 값을 정규화 (null/공백은 N)
     */
    private String normalizeYn(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "N";
        }
        return "Y".equalsIgnoreCase(normalized) ? "Y" : "N";
    }

    /**
     * 문자열을 Long으로 안전하게 변환
     */
    private Long parseLongSafe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 문자열을 Double로 안전하게 변환
     */
    private Double parseDoubleSafe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 날짜 문자열을 YYYY-MM-DD 형식으로 안전하게 변환
     * YYYYMMDD (8자리) -> YYYY-MM-DD
     */
    private String formatDateSafe(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        String cleaned = dateStr.trim();

        // 8자리가 아니면 null
        if (cleaned.length() != 8) {
            return null;
        }

        try {
            int year = Integer.parseInt(cleaned.substring(0, 4));
            int month = Integer.parseInt(cleaned.substring(4, 6));
            int day = Integer.parseInt(cleaned.substring(6, 8));

            // 유효성 검사
            if (year < 1900 || year > 2100)
                return null;
            if (month < 1 || month > 12)
                return null;
            if (day < 1 || day > 31)
                return null;

            // YYYY-MM-DD 형식으로 변환
            return String.format("%04d-%02d-%02d", year, month, day);
        } catch (Exception e) {
            log.warn("Invalid date format: {}", dateStr);
            return null;
        }
    }

    /**
     * MST 파일 업데이트 결과 DTO
     */
    public static class StockMstUpdateResult {
        public boolean success;
        public String message;
        public int updatedCount;

        public StockMstUpdateResult(boolean success, String message, int updatedCount) {
            this.success = success;
            this.message = message;
            this.updatedCount = updatedCount;
        }

        // Getters
        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public int getUpdatedCount() {
            return updatedCount;
        }
    }
}
