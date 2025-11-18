package com.dajanggan.domain.query.dto.agg1m;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 쿼리 트렌드 응답 DTO
 * - QueryOverview의 TPS/QPS 차트용
 * - 시간대별 TPS/QPS 추이 데이터 제공
 *
 * @author 이해든
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryOverviewTrendDto {

    private Long instanceId;
    private Long databaseId;

    // 시간대별 데이터
    private List<TrendDataPoint> trendData;

    // 통계 정보
    private Integer totalDataPoints;       // 전체 데이터 포인트 수
    private Double avgTps;                 // 평균 TPS
    private Double avgQps;                 // 평균 QPS
    private Double avgExecutionTimeMs;     // 평균 실행 시간

    /**
     * 시간대별 데이터 포인트
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendDataPoint {
        private OffsetDateTime timestamp;      // 시각
        private Integer tps;                   // TPS (해당 1분간 쿼리 / 60초)
        private Integer qps;                   // QPS (해당 1분간 쿼리 / 60초)
        private Double avgExecutionTimeMs;     // 평균 실행 시간
        private Integer totalQueries;          // 전체 쿼리 수
        private Integer slowQueryCount;        // 슬로우 쿼리 수
    }
}