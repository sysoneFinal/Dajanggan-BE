package com.dajanggan.domain.query.dto;

import com.dajanggan.domain.query.domain.QuerySuggestion;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * AI 쿼리 분석 응답 DTO
 *
 * @author 이해든
 */
@Data
@Builder
public class QueryAnalysisResponse {
    private ExplainAnalyzeResult explainResult;
    private List<QuerySuggestion> suggestions;
}