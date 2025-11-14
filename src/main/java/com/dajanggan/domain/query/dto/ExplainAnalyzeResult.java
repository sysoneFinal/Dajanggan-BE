package com.dajanggan.domain.query.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * EXPLAIN ANALYZE 결과 DTO
 *
 * @author 이해든
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExplainAnalyzeResult {

    /**
     * 실행 모드
     * - "실제 실행": EXPLAIN ANALYZE 수행 (SELECT 쿼리)
     * - "안전 모드": EXPLAIN만 수행 (DML 쿼리)
     */
    private String executionMode;

    /**
     * EXPLAIN ANALYZE 결과 (전체 텍스트)
     */
    private String explainPlan;

    /**
     * 실행 시간 (밀리초)
     * EXPLAIN ANALYZE에서만 제공됨
     */
    private Double executionTimeMs;

    /**
     * 계획 시간 (밀리초)
     * EXPLAIN ANALYZE에서만 제공됨
     */
    private Double planningTimeMs;

    /**
     * 반환된 행 수
     * (향후 확장 가능)
     */
    private Integer rowsReturned;
}