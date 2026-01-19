package com.monstersinc.stock101.disclosure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 공시보고서 분석 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisclosureAnalysisRequest {

    /**
     * 문서 ID
     */
    private Long documentId;

    /**
     * 질문/프롬프트
     */
    private String query;

    /**
     * 분석 타입
     */
    private AnalysisType analysisType;

    /**
     * 사용자 ID (선택적)
     */
    private Long userId;

    /**
     * 최대 응답 길이 (토큰 수)
     */
    private Integer maxTokens;

    /**
     * 분석 타입 열거형
     */
    public enum AnalysisType {
        SUMMARY, // 요약
        QA, // 질의응답
        FINANCIAL_ANALYSIS, // 재무 분석
        RISK_ANALYSIS, // 리스크 분석
        CUSTOM // 사용자 정의
    }
}
