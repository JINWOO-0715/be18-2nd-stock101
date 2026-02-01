package com.monstersinc.stock101.disclosure.repository;


import com.monstersinc.stock101.disclosure.domain.AIDisclosureReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mapper
public interface AIDisclosureReportRepository {

    /**
     * AI 분석 리포트 삽입
     */
    void insertAiReport(@Param("source_id") Long sourceId,
                        @Param("stock_id") String stockId,
                        @Param("title") String title,
                        @Param("summary_content") String summaryContent,
                        @Param("prospect_content") String prospectContent,
                        @Param("metrics_data") String metricsData,
                        @Param("key_points") String keyPoints,
                        @Param("sentiment_score") BigDecimal sentimentScore,
                        @Param("investment_grade") String investmentGrade,
                        @Param("content") String content
                        );

    /**
     * stock_id로 AI 리포트 목록 조회
     */
    List<AIDisclosureReport> findByStockId(@Param("stockId") String stockId);

    /**
     * report_id로 AI 리포트 단건 조회
     */
    Optional<AIDisclosureReport> findById(@Param("reportId") Long reportId);

}
