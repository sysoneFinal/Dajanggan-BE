package com.dajanggan.domain.query.dto.agg1m;

import lombok.*;

import java.time.OffsetDateTime;

/**
 * 평균 실행 시간 트렌드 DTO
 * - 시간대별 평균 쿼리 실행 시간 추이 데이터
 * - ExecutionStatus 차트용
 *
 * 작성자: 이해든
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class AvgExecutionTimeTrendDto {

    /** 데이터 수집 시각 */
    private OffsetDateTime collectedAt;

    /** 평균 실행 시간 (밀리초) */
    private Double avgExecutionTimeMs;
}