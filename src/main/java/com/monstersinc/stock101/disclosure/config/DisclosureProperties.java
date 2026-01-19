package com.monstersinc.stock101.disclosure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 공시보고서 관련 설정 프로퍼티
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "disclosure")
public class DisclosureProperties {

    /**
     * 파일 업로드 디렉토리
     */
    private String uploadDir = "./uploads/disclosures";

    /**
     * 허용된 파일 확장자
     */
    private String allowedExtensions = "pdf";

    /**
     * 최대 파일 크기 (bytes)
     */
    private Long maxFileSize = 52428800L; // 50MB

    /**
     * 청킹 설정
     */
    private ChunkConfig chunk = new ChunkConfig();

    /**
     * 검색 설정
     */
    private RetrievalConfig retrieval = new RetrievalConfig();

    /**
     * 처리 설정
     */
    private ProcessingConfig processing = new ProcessingConfig();

    @Data
    public static class ChunkConfig {
        /**
         * 청크 크기 (토큰 수)
         */
        private Integer size = 500;

        /**
         * 청크 오버랩 (토큰 수)
         */
        private Integer overlap = 100;
    }

    @Data
    public static class RetrievalConfig {
        /**
         * 검색할 상위 청크 수
         */
        private Integer topK = 10;

        /**
         * 유사도 임계값
         */
        private Double similarityThreshold = 0.7;
    }

    @Data
    public static class ProcessingConfig {
        /**
         * 비동기 처리 여부
         */
        private Boolean async = true;

        /**
         * 스레드 풀 크기
         */
        private Integer threadPoolSize = 5;
    }
}
