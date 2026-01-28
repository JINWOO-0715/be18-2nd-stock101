package com.monstersinc.stock101.dart.service;

import com.monstersinc.stock101.common.util.ZipUtil;
import com.monstersinc.stock101.dart.domain.CorpCode;
import com.monstersinc.stock101.dart.dto.CorpCodeSyncResult;
import com.monstersinc.stock101.stock.model.mapper.StockMapper;
import com.monstersinc.stock101.stock.model.vo.Stock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DART 회사 고유번호 동기화 서비스
 * corpCode.zip을 다운로드하고 XML을 파싱하여 Stock 테이블의 corpCode를 업데이트
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DartCorpCodeService {

    private static final String CORP_CODE_URL = "https://opendart.fss.or.kr/api/corpCode.xml";
    private static final String DOWNLOAD_DIR = "temp_dart";
    private static final int TIMEOUT_MS = 60000; // 60초 (파일이 클 수 있음)

    @Value("${apikey.dart-api-key:#{null}}")
    private String dartApiKey;

    private final StockMapper stockMapper;

    /**
     * DART 고유번호 ZIP 다운로드 및 Stock 테이블 동기화
     * @return 동기화 결과
     */
    @Transactional
    public CorpCodeSyncResult syncCorpCodes() {

        if (dartApiKey == null || dartApiKey.isEmpty()) {
            log.warn("DART API Key가 설정되지 않았습니다.");
            return CorpCodeSyncResult.fail("DART API Key가 설정되지 않았습니다.");
        }

        try {
            // 1. 다운로드 디렉토리 생성
            Path downloadPath = Paths.get(DOWNLOAD_DIR);
            if (!Files.exists(downloadPath)) {
                Files.createDirectories(downloadPath);
            }

            // 2. ZIP 파일 다운로드
            String downloadUrl = CORP_CODE_URL + "?crtfc_key=" + dartApiKey;
            File zipFile = new File(downloadPath.toFile(), "corpCode.zip");
            ZipUtil.downloadFile(downloadUrl, zipFile, TIMEOUT_MS);

            // 3. ZIP 파일에서 XML 추출
            File xmlFile = ZipUtil.extractFirstByExtension(zipFile, ".xml", downloadPath);
            if (xmlFile == null) {
                throw new Exception("ZIP 파일에서 XML 파일을 찾을 수 없습니다");
            }

            // 4. XML 파싱
            List<CorpCode> corpCodes = parseCorpCodeXml(xmlFile);

            // 5. 상장회사만 필터링
            List<CorpCode> listedCorps = corpCodes.stream()
                    .filter(CorpCode::isListed)
                    .collect(Collectors.toList());

            // 6. Stock 테이블 업데이트
            int updatedCount = updateStockCorpCodes(listedCorps);

            // 7. 임시 파일 정리
            zipFile.delete();
            xmlFile.delete();

            return CorpCodeSyncResult.success(
                    "DART 고유번호 동기화 완료",
                    corpCodes.size(),
                    listedCorps.size(),
                    updatedCount
            );

        } catch (Exception e) {
            return CorpCodeSyncResult.fail("동기화 실패: " + e.getMessage());
        }
    }

    /**
     * corpCode.xml 파싱
     * @param xmlFile XML 파일
     * @return CorpCode 목록
     */
    private List<CorpCode> parseCorpCodeXml(File xmlFile) throws Exception {
        List<CorpCode> result = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(xmlFile);
        document.getDocumentElement().normalize();

        
        NodeList nodeList = document.getElementsByTagName("list");
        log.info("XML 파싱 중: {} 개 항목 발견", nodeList.getLength());

        for (int i = 0; i < nodeList.getLength(); i++) {
            Element element = (Element) nodeList.item(i);

            CorpCode corpCode = CorpCode.builder()
                    .corpCode(getTextContent(element, "corp_code"))
                    .corpName(getTextContent(element, "corp_name"))
                    .corpEngName(getTextContent(element, "corp_eng_name"))
                    .stockCode(getTextContent(element, "stock_code"))
                    .modifyDate(getTextContent(element, "modify_date"))
                    .build();

            result.add(corpCode);
        }

        return result;
    }

    /**
     * XML 요소에서 텍스트 내용 추출
     */
    private String getTextContent(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            String content = nodeList.item(0).getTextContent();
            return content != null ? content.trim() : null;
        }
        return null;
    }

    /**
     * Stock 테이블의 corpCode 필드 업데이트
     * @param listedCorps 상장회사 목록
     * @return 업데이트 건수
     */
    private int updateStockCorpCodes(List<CorpCode> listedCorps) {
        int updatedCount = 0;

        // stockCode -> corpCode 매핑 생성
        Map<String, String> stockToCorpMap = listedCorps.stream()
                .filter(c -> c.getStockCode() != null && !c.getStockCode().isEmpty())
                .collect(Collectors.toMap(
                        CorpCode::getStockCode,
                        CorpCode::getCorpCode,
                        (existing, replacement) -> existing // 중복 시 기존 값 유지
                ));

        // Stock 테이블의 모든 종목 조회
        List<Stock> stocks = stockMapper.selectStockList();

        for (Stock stock : stocks) {
            String stockCode = stock.getStockCode();
            if (stockCode != null && stockToCorpMap.containsKey(stockCode)) {
                String newCorpCode = stockToCorpMap.get(stockCode);
                
                // 기존 corpCode와 다른 경우에만 업데이트
                if (!newCorpCode.equals(stock.getCorpCode())) {
                    stock.setCorpCode(newCorpCode);
                    stockMapper.updateStockBasicInfo(stock);
                    updatedCount++;
                    
                    if (updatedCount <= 5) {
                        log.debug("corpCode 업데이트: {} ({}) -> {}", 
                                stock.getName(), stockCode, newCorpCode);
                    }
                }
            }
        }

        return updatedCount;
    }
}
