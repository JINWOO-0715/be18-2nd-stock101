package com.monstersinc.stock101.disclosure.repository;


import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface AIDisclosureReportRepository {

    /**
     * AI 분석 리포트 삽입
     */
    void insertAiReport(@Param("source_id") Long sourceId,
                        @Param("summary_content") String summaryContent
                        );


}
