package com.monstersinc.stock101.common.util;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.IntStream;

@Slf4j
public class TextChunkUtil {

    public static List<TextSegment> chunkText(String text, Integer pageNumber, int chunkSize, int overlap) {
        // 1. 스플리터 설정
        DocumentSplitter splitter = new DocumentBySentenceSplitter(chunkSize, overlap);

        // 2. 메타데이터 생성 및 도큐먼트 결합
        // 최신 방식: Metadata 객체를 먼저 만들고 put()으로 넣습니다.
        Metadata metadata = new Metadata();
        metadata.put("page_number", pageNumber);
        // 참고: metadata.add() 대신 metadata.put() 또는 metadata.set()을 사용합니다.

        // 3. Document 생성 시 메타데이터 함께 주입
        Document document = Document.from(text, metadata);

        // 4. 분할 실행 (결과는 List<TextSegment>)
        // 이때 생성된 모든 TextSegment에는 위의 page_number 메타데이터가 복사되어 들어갑니다.
        return splitter.split(document);
    }

    public static List<TextSegment> chunkText(String text, String sectionTitle, int chunkSize, int overlap) {
        // 1. 스플리터 설정 (문장 단위 분할)
        DocumentSplitter splitter = new DocumentBySentenceSplitter(chunkSize, overlap);

        // 2. 메타데이터 생성 및 섹션 제목 주입
        Metadata metadata = new Metadata();
        // 페이지 번호 대신 혹은 함께 섹션 제목을 저장합니다.
        metadata.put("section_title", sectionTitle);

        // 3. Document 생성
        Document document = Document.from(text, metadata);

        List<TextSegment> segments = splitter.split(document);

        for (int i = 0; i < segments.size(); i++) {
            String segmentText = segments.get(i).text();
            if (segmentText.length() > chunkSize * 1.5) { // 설정값보다 너무 큰 청크가 생겼는지 확인
                log.warn("🚨 [과대 청크 경고] 섹션: {}, 인덱스: {}, 길이: {}", sectionTitle, i, segmentText.length());
            }
            if (segmentText.isBlank()) {
                log.warn("🚨 [빈 청크 경고] 섹션: {}, 인덱스: {}", sectionTitle, i);
            }
        }

        return segments;
    }
}