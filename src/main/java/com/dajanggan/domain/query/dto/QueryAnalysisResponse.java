package com.dajanggan.domain.query.dto;

import com.dajanggan.domain.query.domain.QuerySuggestion;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * AI 쿼리 분석 응답 DTO
 *
 * 기능:
 * - EXPLAIN ANALYZE 결과와 AI 최적화 제안을 함께 반환하는 객체
 * - explainResult: 쿼리 실행 계획 분석 결과
 * - suggestions: AI 또는 규칙 기반 최적화 제안 리스트
 *
 * 작성자: 이해든
 */
@Data
@Builder
public class QueryAnalysisResponse {

    private ExplainAnalyzeResult explainResult;
    private List<QuerySuggestion> suggestions;
}