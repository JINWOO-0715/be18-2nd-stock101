package com.monstersinc.stock101.disclosure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 공시보고서 분석 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisclosureAnalysisResponse {

    /**
     * 분석 결과 텍스트
     */
    private String analysisResult;

    /**
     * 참조된 문서 청크 (출처)
     */
    private List<ChunkReference> sources;

    /**
     * 신뢰도 점수 (0.0 ~ 1.0)
     */
    private Double confidenceScore;

    /**
     * 처리 시간 (밀리초)
     */
    private Long processingTimeMs;

    /**
     * 사용된 토큰 수
     */
    private Integer tokensUsed;

    /**
     * 청크 참조 정보
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChunkReference {
        /**
         * 청크 ID
         */
        private Long chunkId;

        /**
         * 페이지 번호
         */
        private Integer pageNumber;

        /**
         * 청크 텍스트 (일부)
         */
        private String excerpt;

        /**
         * 유사도 점수
         */
        private Double similarityScore;
    }
}
