package com.monstersinc.stock101.disclosure.util;

import com.monstersinc.stock101.disclosure.config.DisclosureProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 텍스트 청킹 유틸리티
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TextChunker {

    private final DisclosureProperties disclosureProperties;

    /**
     * 텍스트를 청크로 분할
     * 
     * @param text       원본 텍스트
     * @param pageNumber 페이지 번호 (선택적)
     * @return 청크 리스트
     */
    public List<TextChunk> chunkText(String text, Integer pageNumber) {
        List<TextChunk> chunks = new ArrayList<>();

        int chunkSize = disclosureProperties.getChunk().getSize();
        int overlap = disclosureProperties.getChunk().getOverlap();

        // 간단한 문자 기반 청킹 (실제로는 토큰 기반으로 개선 필요)
        int charPerToken = 4; // 평균적으로 1 토큰 = 4 문자
        int chunkCharSize = chunkSize * charPerToken;
        int overlapCharSize = overlap * charPerToken;

        int startPos = 0;
        int chunkIndex = 0;

        while (startPos < text.length()) {
            int endPos = Math.min(startPos + chunkCharSize, text.length());

            // 문장 경계에서 자르기 (마침표, 느낌표, 물음표)
            if (endPos < text.length()) {
                int lastPeriod = text.lastIndexOf('.', endPos);
                int lastExclamation = text.lastIndexOf('!', endPos);
                int lastQuestion = text.lastIndexOf('?', endPos);

                int sentenceBoundary = Math.max(lastPeriod, Math.max(lastExclamation, lastQuestion));
                if (sentenceBoundary > startPos) {
                    endPos = sentenceBoundary + 1;
                }
            }

            String chunkText = text.substring(startPos, endPos).trim();

            if (!chunkText.isEmpty()) {
                chunks.add(TextChunk.builder()
                        .chunkIndex(chunkIndex)
                        .text(chunkText)
                        .startChar(startPos)
                        .endChar(endPos)
                        .pageNumber(pageNumber)
                        .tokenCount(estimateTokenCount(chunkText))
                        .build());

                chunkIndex++;
            }

            // 다음 청크 시작 위치 (오버랩 고려)
            startPos = endPos - overlapCharSize;
            if (startPos >= text.length()) {
                break;
            }
        }

        log.info("Created {} chunks from text of length {}", chunks.size(), text.length());
        return chunks;
    }

    /**
     * 페이지별 텍스트를 청크로 분할
     */
    public List<TextChunk> chunkTextByPages(java.util.Map<Integer, String> pageTexts) {
        List<TextChunk> allChunks = new ArrayList<>();
        int globalChunkIndex = 0;

        for (java.util.Map.Entry<Integer, String> entry : pageTexts.entrySet()) {
            Integer pageNumber = entry.getKey();
            String pageText = entry.getValue();

            List<TextChunk> pageChunks = chunkText(pageText, pageNumber);

            // 전역 청크 인덱스 재설정
            for (TextChunk chunk : pageChunks) {
                chunk.setChunkIndex(globalChunkIndex++);
                allChunks.add(chunk);
            }
        }

        return allChunks;
    }

    /**
     * 토큰 수 추정 (간단한 휴리스틱)
     */
    private int estimateTokenCount(String text) {
        // 간단한 추정: 평균 4 문자 = 1 토큰
        return text.length() / 4;
    }

    /**
     * 텍스트 청크 데이터 클래스
     */
    @Data
    @Builder
    @AllArgsConstructor
    public static class TextChunk {
        private Integer chunkIndex;
        private String text;
        private Integer startChar;
        private Integer endChar;
        private Integer pageNumber;
        private Integer tokenCount;
    }
}
