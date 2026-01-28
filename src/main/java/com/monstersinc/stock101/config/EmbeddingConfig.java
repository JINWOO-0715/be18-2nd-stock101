package com.monstersinc.stock101.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class EmbeddingConfig {

    @Value("${langchain4j.ollama.embedding-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.ollama.embedding-model.model-name}")
    private String modelName;

    // 1. 추가할 내용: Qdrant 관련 설정값들
    @Value("${langchain4j.qdrant.host}")
    private String qdrantHost;

    @Value("${langchain4j.qdrant.port}")
    private int qdrantPort;

    @Value("${langchain4j.qdrant.collection-name}")
    private String collectionName;

    @Bean
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofMinutes(10)) // 공시 문서 대량 처리용
                .build();
    }

    // 2. 추가할 내용: EmbeddingStore(Qdrant) Bean 등록
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return QdrantEmbeddingStore.builder()
                .host(qdrantHost)
                .port(qdrantPort)    // 보통 6334 (gRPC)
                .collectionName(collectionName)
                .build();
    }
}