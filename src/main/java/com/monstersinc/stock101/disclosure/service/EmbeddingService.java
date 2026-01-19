package com.monstersinc.stock101.disclosure.service;

import com.monstersinc.stock101.disclosure.domain.DocumentChunk;
import com.monstersinc.stock101.disclosure.repository.DocumentChunkRepository;
import com.monstersinc.stock101.disclosure.util.TextChunker;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
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

    private final DocumentChunkRepository chunkRepository;

    @Value("${langchain4j.open-ai.embedding-model.api-key}")
    private String openAiApiKey;

    @Value("${langchain4j.open-ai.embedding-model.model-name}")
    private String modelName;

    private EmbeddingModel embeddingModel;

    @PostConstruct
    public void init() {
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(openAiApiKey)
                .modelName(modelName)
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

        // LangChain4j 세그먼트 생성
        for (TextChunker.TextChunk textChunk : textChunks) {
            segments.add(TextSegment.from(textChunk.getText()));
        }

        // 배치 처리로 임베딩 생성 (비용 및 성능 최적화)
        // OpenAI API 제한을 고려하여 작은 배치로 나누는 것이 좋을 수 있음
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        if (embeddings.size() != textChunks.size()) {
            throw new RuntimeException("Embedding count mismatch");
        }

        for (int i = 0; i < textChunks.size(); i++) {
            TextChunker.TextChunk textChunk = textChunks.get(i);
            Embedding embedding = embeddings.get(i);

            DocumentChunk documentChunk = DocumentChunk.builder()
                    .documentId(documentId)
                    .chunkIndex(textChunk.getChunkIndex())
                    .chunkText(textChunk.getText())
                    .pageNumber(textChunk.getPageNumber())
                    .startChar(textChunk.getStartChar())
                    .endChar(textChunk.getEndChar())
                    .tokenCount(textChunk.getTokenCount())
                    .build();

            // 벡터 변환 및 설정
            documentChunk.setEmbeddingFromFloatArray(embedding.vector());

            documentChunks.add(documentChunk);
        }

        // DB 저장 (Bulk Insert)
        // 대량 데이터의 경우 더 작은 배치로 나누어 저장하는 것이 좋음
        int batchSize = 100;
        for (int i = 0; i < documentChunks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, documentChunks.size());
            chunkRepository.saveAll(documentChunks.subList(i, end));
        }

        log.info("Saved {} embedded chunks for document {}", documentChunks.size(), documentId);
    }

    /**
     * 유사한 청크 검색 (메모리 내 코사인 유사도 계산)
     * 주의: 데이터가 많아지면 벡터 DB 전용 솔루션이나 pgvector 등으로 마이그레이션 필수
     */
    public List<DocumentChunk> findSimilarChunks(Long stockId, String query, int topK) {
        // 1. 쿼리 임베딩 생성
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // 2. 해당 종목의 모든 청크 로드 (성능 주의!)
        List<DocumentChunk> allChunks = chunkRepository.findAllEmbeddingsByStockId(stockId);

        // 3. 코사인 유사도 계산 및 정렬
        return allChunks.stream()
                .map(chunk -> {
                    double similarity = cosineSimilarity(queryEmbedding.vector(), chunk.getEmbeddingAsFloatArray());
                    // 유사도 점수를 임시로 저장할 방법이 필요함.
                    // 여기서는 DTO 변환 없이 로직 상에서만 처리하거나, DocumentChunk에 score 필드가 없으므로 Pair로 반환
                    return new ScoredChunk(chunk, similarity);
                })
                .sorted(Comparator.comparingDouble(ScoredChunk::getScore).reversed())
                .limit(topK)
                .map(ScoredChunk::getChunk)
                .collect(Collectors.toList());
    }

    /**
     * 코사인 유사도 계산
     */
    private double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA.length != vectorB.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }

        if (normA == 0 || normB == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    @lombok.Value
    private static class ScoredChunk {
        DocumentChunk chunk;
        double score;
    }
}
