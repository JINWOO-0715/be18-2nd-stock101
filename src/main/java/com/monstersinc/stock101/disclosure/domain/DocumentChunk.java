package com.monstersinc.stock101.disclosure.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 문서 청크 엔티티 (벡터 임베딩 포함)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {

    private Long chunkId;
    private Long documentId;
    private Integer chunkIndex;
    private String chunkText;
    private Integer pageNumber;
    private Integer startChar;
    private Integer endChar;
    private Integer tokenCount;
    private String embeddingVector; // JSON 배열로 저장된 벡터
    private String metadata; // JSON 형식
    private LocalDateTime createdAt;

    /**
     * 벡터를 float 배열로 변환
     */
    public float[] getEmbeddingAsFloatArray() {
        if (embeddingVector == null || embeddingVector.isEmpty()) {
            return new float[0];
        }
        // JSON 배열 파싱 로직 (예: [0.1, 0.2, 0.3, ...])
        String[] values = embeddingVector
                .replace("[", "")
                .replace("]", "")
                .split(",");

        float[] result = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = Float.parseFloat(values[i].trim());
        }
        return result;
    }

    /**
     * float 배열을 JSON 문자열로 변환
     */
    public void setEmbeddingFromFloatArray(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            this.embeddingVector = null;
            return;
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0)
                sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        this.embeddingVector = sb.toString();
    }
}
