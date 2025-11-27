package com.dajanggan.domain.query.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * EXPLAIN ANALYZE 요청 DTO
 *
 * 기능:
 * - 클라이언트로부터 쿼리 분석 요청을 받는 데이터 객체
 * - databaseId: 분석할 데이터베이스 식별자
 * - query: 분석 대상 SQL 쿼리문
 *
 * 작성자: 이해든
 * 작성자: 이해든
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExplainAnalyzeRequest {

    private Long databaseId;
    private String query;
}