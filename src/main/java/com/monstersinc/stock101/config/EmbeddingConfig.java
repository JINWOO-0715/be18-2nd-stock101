package com.monstersinc.stock101.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

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

    // 1. Qdrant 공식 클라이언트를 사용하여 컬렉션을 강제 생성하는 로직
    private void createCollectionIfNotExist() {
        try (QdrantClient client = new QdrantClient(
                QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort, false).build())) {

            // 컬렉션 리스트 확인
            java.util.List<String> collections = client.listCollectionsAsync().get();

            if (!collections.contains(collectionName)) {
                // 컬렉션이 없으면 5120차원으로 생성
                client.createCollectionAsync(collectionName,
                        Collections.VectorParams.newBuilder()
                                .setSize(1024) // 여기서 차원을 5120으로 강제 지정
                                .setDistance(Collections.Distance.Cosine)
                                .build()
                ).get();
                System.out.println(">>> Qdrant 컬렉션 생성 완료: " + collectionName + " (5120 dim)");
            }
        } catch (Exception e) {
            System.err.println(">>> Qdrant 컬렉션 확인 중 오류 발생: " + e.getMessage());
        }
    }

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
        createCollectionIfNotExist();  // 애플리케이션 시작 시 컬렉션 존재 여부 확인 및 생성
        return QdrantEmbeddingStore.builder()
                .host(qdrantHost)
                .port(qdrantPort)    // 보통 6334 (gRPC)
                .collectionName(collectionName)
                .build();
    }
}