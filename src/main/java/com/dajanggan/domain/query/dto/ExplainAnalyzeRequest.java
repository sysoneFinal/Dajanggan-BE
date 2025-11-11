package com.dajanggan.domain.query.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * EXPLAIN ANALYZE 요청 DTO
 *
 * @author 이해든
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExplainAnalyzeRequest {

    /**
     * 데이터베이스 ID
     */
    private Long databaseId;

    /**
     * 분석할 SQL 쿼리
     */
    private String query;
}