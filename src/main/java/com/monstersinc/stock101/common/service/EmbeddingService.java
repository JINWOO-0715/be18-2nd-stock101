package com.monstersinc.stock101.common.service;

import com.monstersinc.stock101.common.util.TextChunkUtil;
import com.monstersinc.stock101.common.util.MarkdownChunker;
import com.monstersinc.stock101.disclosure.config.DisclosureProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 임베딩 생성 및 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final MarkdownChunker markdownChunker;
    private final DisclosureProperties disclosureProperties;

    /**
     * Docling 마크다운 청크를 분석하여 계층 정보를 추출하고,
     * 의미 단위(TextChunkUtil)로 정밀하게 쪼개어 임베딩 저장소에 저장합니다.
     */
    public void processMarkdownChunk(Long sourceId, String chunk) {
        if (chunk == null || chunk.isBlank()) return;

        try {
            // 1. 마크다운 헤더로부터 계층 정보 추출
            String parentTitle = markdownChunker.extractParent(chunk);
            String subTopic = markdownChunker.extractSub(chunk);

            // Docling 마크다운에서는 sectionTitle과 subTopic이 유사할 수 있으므로
            // 하위 제목이 없으면 대제목을 중복 사용하거나 "본문"으로 처리
            String sectionTitle = parentTitle;

            log.info("임베딩 저장 실행 -> 주제: [{} > {}], 크기: {}자", parentTitle, subTopic, chunk.length());

            // 2. 컨텍스트 보강 (Enrichment)
            // 검색 시 LLM이 맥락을 파악할 수 있도록 청크 최상단에 계층 정보 삽입
            String contextHeader = String.format("[%s > %s]\n", parentTitle, subTopic);
            String fullContent = contextHeader + chunk;

            // 3. 정밀 청킹 (TextChunkUtil 활용)
            // 마크다운 청크 하나가 설정된 사이즈(예: 1000자)보다 클 수 있으므로 최종 분할
            List<TextSegment> segments = TextChunkUtil.chunkText(
                    fullContent,
                    subTopic,
                    disclosureProperties.getChunk().getSize(),
                    disclosureProperties.getChunk().getOverlap()
            );

            // 4. 메타데이터 주입
            for (TextSegment segment : segments) {
                var metadata = segment.metadata();
                metadata.put("source_id", String.valueOf(sourceId));
                metadata.put("parent_title", parentTitle);
                metadata.put("section_title", sectionTitle);
                metadata.put("sub_title", subTopic);
                // 필요 시 파일명이나 페이지 번호 추정치 추가 가능
            }

            // 5. 벡터 DB 저장
            if (!segments.isEmpty()) {
                embedAndSaveSegments(sourceId, segments);
            }

        } catch (Exception e) {
            log.error("임베딩 중 오류 발생! 내용 일부: {}..., 사유: {}",
                    chunk.substring(0, Math.min(chunk.length(), 50)), e.getMessage());
        }
    }
    public List<String> searchByTheme(Long sourceId, String themeQuery, int maxResults) {
        // 1. 테마에 최적화된 임베딩 생성
        Embedding queryEmbedding = embeddingModel.embed(themeQuery).content();

        // 2. 메타데이터 필터 생성 (현재 sourceId에 해당하는 청크만 검색)
        // LangChain4j의 Metadata Filter를 사용하여 다른 문서와 섞이지 않게 합니다.
        Filter sourceFilter = MetadataFilterBuilder.metadataKey("source_id").isEqualTo(sourceId.toString());

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .filter(sourceFilter)  // 특정 문서로 범위 제한
                .maxResults(maxResults)
                .minScore(0.5)         // DART 요약의 질을 위해 임계값을 상향 조정 (0.1 -> 0.5)
                .build();

        try {
            EmbeddingSearchResult<TextSegment> result = embeddingStore.search(searchRequest);

            // 3. 검색된 결과에서 텍스트만 추출하여 반환
            return result.matches().stream()
                    .map(match -> match.embedded().text())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("테마 검색 중 오류 발생 [Query: {}]: {}", themeQuery, e.getMessage());
            return Collections.emptyList();
        }
    }


    /**
     * 특정 문서 내 '사업의 내용' 섹션에 대한 핀포인트 검색
     */
    public List<EmbeddingMatch<TextSegment>> searchPinpoint(Long sourceId, Filter pinpointFilter) {

        // 2. 검색 요청 생성
        String defaultQuery = "기업의 주요 사업 내용 및 재무 실적 현황";

        Embedding queryEmbedding = embeddingModel.embed(defaultQuery).content();

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                //.filter(pinpointFilter)    // 필터 적용
                .maxResults(7)             // 상위 7개 청크
                .minScore(0.1)             // 연관성이 낮은 데이터는 배제
                .build();

        // 3. 검색 실행
        try {
            EmbeddingSearchResult<TextSegment> result = embeddingStore.search(searchRequest);
            log.info("Embedding search result count: {}", result.matches().size());
            return result.matches();
        } catch (Exception e) {
            log.error("Embedding Store 검색 중 오류 발생: {}", e.getMessage());
            throw e; // 호출부인 DisclosureService:77 로 에러가 전파됨
        }
    }

    public List<EmbeddingMatch<TextSegment>> searchPinpoint(Long sourceId, String userQuery) {

        userQuery = "주요 제품 및 서비스";
        // 1. 사용자 질문을 벡터로 변환 (사용 중인 임베딩 모델 필요)
        Embedding queryEmbedding = embeddingModel.embed(userQuery).content();

        //documentID가 같은것만.
        Filter pinpointFilter = Filter.and(
                new IsEqualTo("documentId", String.valueOf(sourceId)),
                new IsEqualTo("section_title", "2. 주요 제품 및 서비스")
                );

        // 2. 검색 요청 생성
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .filter(pinpointFilter)    // 필터 적용
                        .maxResults(7)             // 상위 7개 청크
                        .minScore(0.1)             // 연관성이 낮은 데이터는 배제
                        .build();

        // 3. 검색 실행
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(searchRequest);

        log.info("Embedding search result: {}", result.toString());

        return result.matches();
    }

    /**
     * 청크에 대한 임베딩 생성 및 저장
     */
    public void embedAndSaveSegments(Long documentId, List<TextSegment> segments) {
        // 1. 유효한 세그먼트만 필터링 (빈 문자열이나 공백만 있는 경우 제외)
        List<TextSegment> validSegments = segments.stream()
                .filter(segment -> segment.text() != null && !segment.text().trim().isEmpty())
                .filter(segment -> segment.text().length() > 2) // 너무 짧은 노이즈(예: ".", "1") 제거 추가
                .toList();

        if (validSegments.isEmpty()) {
            log.warn("임베딩할 유효한 텍스트가 없습니다. documentId={}", documentId);
            return;
        }

        // 2. 각 세그먼트에 documentId 메타데이터 추가 (이미 되어 있다면 생략 가능)
        // 2. 메타데이터 주입 (필터링된 결과에만 적용)
        validSegments.forEach(segment ->
                segment.metadata().put("documentId", String.valueOf(documentId))
        );
        // 3. 라이브러리 활용: embedAll과 addAll을 결합한 형태
        // 내부적으로 병렬 처리 및 최적화가 수행됩니다.
        // 임베딩 및 저장 (이 부분에서 Ollama와 통신함)
        try {
            // [중요] 반드시 validSegments를 인자로 넘겨야 합니다!
            embeddingStore.addAll(embeddingModel.embedAll(validSegments).content(), validSegments);
            log.info("Qdrant 저장 완료");
        } catch (Exception e) {
            log.error("임베딩 중 오류 발생: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 필터링 검색 (Qdrant 전용 필터 활용)
     */
    public List<TextSegment> findSimilar(Long documentId, String query, int topK) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // MetadataFilter를 사용하여 특정 문서 내에서만 검색 (RAG의 핵심)
        Filter filter = MetadataFilterBuilder.metadataKey("documentId").isEqualTo(String.valueOf(documentId));

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .filter(filter)
                .maxResults(topK)
                .build();

        return embeddingStore.search(searchRequest).matches().stream()
                .map(EmbeddingMatch::embedded)
                .toList();
    }

}
