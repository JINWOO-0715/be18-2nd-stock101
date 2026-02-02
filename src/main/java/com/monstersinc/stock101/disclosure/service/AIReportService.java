package com.monstersinc.stock101.disclosure.service;

import com.monstersinc.stock101.disclosure.domain.AIDisclosureReport;
import com.monstersinc.stock101.disclosure.repository.AIDisclosureReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 리포트 조회 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIReportService {

    private final AIDisclosureReportRepository aiReportRepository;

    /**
     * stock_id로 AI 리포트 목록 조회
     * @param stockId 주식 ID
     * @return AI 리포트 목록
     */
    public List<AIDisclosureReport> getReportsByStockId(String stockId) {
        log.info("AI 리포트 조회 시작: stockId={}", stockId);
        List<AIDisclosureReport> reports = aiReportRepository.findByStockId(stockId);
        log.info("AI 리포트 조회 완료: stockId={}, 개수={}", stockId, reports.size());
        return reports;
    }

    /**
     * report_id로 AI 리포트 단건 조회
     * @param reportId 리포트 ID
     * @return AI 리포트
     */
    public AIDisclosureReport getReportById(Long reportId) {
        log.info("AI 리포트 단건 조회: reportId={}", reportId);
        return aiReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("리포트를 찾을 수 없습니다: reportId=" + reportId));
    }

    /**
     * 최신 AI 리포트 목록 조회
     * @param limit 조회할 개수
     * @return 최신순으로 정렬된 AI 리포트 목록
     */
    public List<AIDisclosureReport> getRecentReports(int limit) {
        log.info("최신 AI 리포트 조회 시작: limit={}", limit);
        List<AIDisclosureReport> reports = aiReportRepository.findRecentReports(limit);
        log.info("최신 AI 리포트 조회 완료: 개수={}", reports.size());
        return reports;
    }
}
