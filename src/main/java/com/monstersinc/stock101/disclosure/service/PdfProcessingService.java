package com.monstersinc.stock101.disclosure.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF 문서 처리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfProcessingService {

    /**
     * PDF에서 텍스트 추출
     * 
     * @param filePath PDF 파일 경로
     * @return 추출된 텍스트
     */
    public String extractText(String filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.info("Extracted {} characters from PDF: {}", text.length(), filePath);
            return text;
        }
    }

    /**
     * PDF에서 페이지별 텍스트 추출
     * 
     * @param filePath PDF 파일 경로
     * @return 페이지 번호와 텍스트의 맵
     */
    public Map<Integer, String> extractTextByPage(String filePath) throws IOException {
        Map<Integer, String> pageTexts = new HashMap<>();

        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = document.getNumberOfPages();

            for (int page = 1; page <= totalPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document);
                pageTexts.put(page, pageText);
            }

            log.info("Extracted text from {} pages", totalPages);
        }

        return pageTexts;
    }

    /**
     * PDF 페이지 수 가져오기
     */
    public int getPageCount(String filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            return document.getNumberOfPages();
        }
    }

    /**
     * PDF 메타데이터 추출
     */
    public Map<String, String> extractMetadata(String filePath) throws IOException {
        Map<String, String> metadata = new HashMap<>();

        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            var info = document.getDocumentInformation();

            if (info.getTitle() != null) {
                metadata.put("title", info.getTitle());
            }
            if (info.getAuthor() != null) {
                metadata.put("author", info.getAuthor());
            }
            if (info.getSubject() != null) {
                metadata.put("subject", info.getSubject());
            }
            if (info.getCreationDate() != null) {
                metadata.put("creationDate", info.getCreationDate().toString());
            }

            metadata.put("pageCount", String.valueOf(document.getNumberOfPages()));
        }

        return metadata;
    }

    /**
     * PDF 유효성 검증
     */
    public boolean isValidPdf(String filePath) {
        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            return document.getNumberOfPages() > 0;
        } catch (IOException e) {
            log.error("Invalid PDF file: {}", filePath, e);
            return false;
        }
    }
}
