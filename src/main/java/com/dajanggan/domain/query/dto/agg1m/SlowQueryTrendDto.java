package com.dajanggan.domain.query.dto.agg1m;

import lombok.*;

import java.time.OffsetDateTime;

/**
 * 슬로우 쿼리 트렌드 DTO
 * - 시간대별 슬로우 쿼리(임계값 초과) 발생 건수 추이
 * - ExecutionStatus 차트용
 *
 * @author 이해든
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class SlowQueryTrendDto {

    /** 데이터 수집 시각 */
    private OffsetDateTime collectedAt;

    /** 슬로우 쿼리 발생 건수 */
    private Integer slowQueryCount;
}