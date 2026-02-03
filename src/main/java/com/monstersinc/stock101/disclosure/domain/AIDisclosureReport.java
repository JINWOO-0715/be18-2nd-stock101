package com.monstersinc.stock101.disclosure.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 생성 공시보고서 분석 리포트 엔티티
 * 테이블: ai_disclosure_report
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIDisclosureReport {

    /**
     * AI 리포트 ID (PK, AUTO_INCREMENT)
     */
    private Long reportId;

    /**
     * 파일 마스터 ID (외래키 - documents 테이블)
     */
    private Long sourceId;

    /**
     * 주식 ID (외래키 - stock 테이블)
     */
    private String stockId;

    /**
     * 리포트 제목
     */
    private String title;

    /**
     * AI 생성 요약 본문 (Part 1)
     */
    private String summaryContent;

    /**
     * AI 생성 전망 본문 (Part 2)
     */
    private String prospectContent;

    /**
     * 구조화된 수치 데이터 (표) - JSON 형식
     */
    private String metricsData;

    /**
     * 핵심 포인트 리스트 - JSON 형식
     */
    private String keyPoints;

    /**
     * 긍정/부정 점수 (-1.00 ~ 1.00)
     */
    private BigDecimal sentimentScore;

    /**
     * 투자 등급 (매수, 보유, 주의)
     */
    private String investmentGrade;

    /**
     * 기타 분석 내용 (AI 한 줄 평)
     */
    private String content;

    /**
     * 생성 일시
     */
    private LocalDateTime createdAt;

    private String companyName;
}
