package com.dajanggan.domain.query.dto.agg1m;

import lombok.*;

import java.time.OffsetDateTime;

/**
 * 쿼리 타입별 트렌드 DTO
 * - 시간대별 쿼리 타입(SELECT, INSERT, UPDATE, DELETE) 실행 건수 추이
 * - ExecutionStatus 차트용
 *
 * @author 이해든
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class QueryTypeTrendDto {

    /** 데이터 수집 시각 */
    private OffsetDateTime collectedAt;

    /** SELECT 쿼리 실행 건수 */
    private Integer selectQueries;

    /** INSERT 쿼리 실행 건수 */
    private Integer insertQueries;

    /** UPDATE 쿼리 실행 건수 */
    private Integer updateQueries;

    /** DELETE 쿼리 실행 건수 */
    private Integer deleteQueries;
}