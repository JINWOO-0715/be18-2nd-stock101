package com.monstersinc.stock101.disclosure.service;

import com.monstersinc.stock101.disclosure.domain.DocumentChunk;
import com.monstersinc.stock101.disclosure.repository.DocumentChunkRepository;
import com.monstersinc.stock101.disclosure.util.TextChunker;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Value("${langchain4j.ollama.embedding-model.base-url}")
    private String ollamaBaseUrl;

    @Value("${langchain4j.ollama.embedding-model.model-name}")
    private String modelName;

    @Value("${langchain4j.qdrant.host}")
    private String qdrantHost;

    @Value("${langchain4j.qdrant.port}")
    private int qdrantPort;

    @Value("${langchain4j.qdrant.collection-name}")
    private String collectionName;

    @PostConstruct
    public void init() {
        this.embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(modelName)
                .timeout(java.time.Duration.ofMinutes(5))
                .build();

        this.embeddingStore = QdrantEmbeddingStore.builder()
                .host(qdrantHost)
                .port(qdrantPort)
                .collectionName(collectionName)
                .build();
    }

    /**
     * 청크에 대한 임베딩 생성 및 저장
     */
    @Transactional
    public void embedAndSaveChunks(Long documentId, List<TextChunker.TextChunk> textChunks) {
        log.info("Generating embeddings for {} chunks of document {}", textChunks.size(), documentId);

        List<DocumentChunk> documentChunks = new ArrayList<>();
        List<TextSegment> segments = new ArrayList<>();

        // LangChain4j 세그먼트 생성 (Metadata 포함)
        for (TextChunker.TextChunk textChunk : textChunks) {
            Metadata metadata = new Metadata();
            metadata.add("documentId", String.valueOf(documentId));
            metadata.add("pageNumber", String.valueOf(textChunk.getPageNumber()));
            metadata.add("fileSource", "stock_disclosure"); // 예시 메타데이터

            segments.add(TextSegment.from(textChunk.getText(), metadata));
        }

        // 임베딩 생성 및 Qdrant 저장
        log.info("Embedding and pushing to Qdrant...");
        embeddingStore.addAll(embeddingModel.embedAll(segments).content(), segments);
        
        log.info("Saved {} embedded chunks for document {} to Qdrant", segments.size(), documentId);
    }

    /**
     * 유사한 청크 검색 (메모리 내 코사인 유사도 계산)
     * 주의: 데이터가 많아지면 벡터 DB 전용 솔루션이나 pgvector 등으로 마이그레이션 필수
     */
    /**
     * 유사한 청크 검색 (Qdrant 사용)
     */
    public List<TextSegment> findSimilarChunks(Long stockId, String query, int topK) {
        // Qdrant에서 검색 (Cosine Similarity 기본)
        // Filter를 사용하여 특정 문서(documentId) 범위 내에서만 검색할 수도 있음.
        // 여기서는 stockId로 필터링하려면 Metadata에 stockId가 있어야 함. 현재는 documentId만 있음.
        // TODO: Metadata에 stockId 추가 필요. 우선은 전체 검색.
        
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(queryEmbedding, topK);

        return matches.stream()
                .map(EmbeddingMatch::embedded)
                .collect(Collectors.toList());
    }

    /**
     * 코사인 유사도 계산
     */


    @lombok.Value
    private static class ScoredChunk {
        DocumentChunk chunk;
        double score;
    }
}
