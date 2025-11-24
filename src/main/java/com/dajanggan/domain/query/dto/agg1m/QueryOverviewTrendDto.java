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

    /** 데이터베이스 식별 정보 */
    private Long instanceId;
    private Long databaseId;

    /** 시간대별 트렌드 데이터 */
    private List<TrendDataPoint> trendData;

    /** 통계 요약 정보 */
    private Integer totalDataPoints;
    private Double avgTps;
    private Double avgQps;
    private Double avgExecutionTimeMs;

    /**
     * 시간대별 데이터 포인트
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendDataPoint {

        /** 데이터 수집 시각 */
        private OffsetDateTime timestamp;

        /** TPS/QPS 통계 */
        private Integer tps;
        private Integer qps;

        /** 실행 시간 통계 */
        private Double avgExecutionTimeMs;

        /** 쿼리 개수 통계 */
        private Integer totalQueries;
        private Integer slowQueryCount;
    }
}