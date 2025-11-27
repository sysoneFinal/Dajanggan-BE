package com.dajanggan.domain.query.dto.agg1m;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 쿼리 요약 DTO (1분 집계 기반)
 * - QueryOverview 페이지의 요약 카드용
 * - 최근 5분간의 1분 집계 데이터를 SUM/AVG하여 계산
 *
 * 작성자: 이해든
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuerySummaryDto {

    /** 데이터베이스 식별 정보 */
    private Long instanceId;
    private Long databaseId;

    /** 기본 쿼리 통계 */
    private Integer totalQueries;
    private Double avgExecutionTimeMs;
    private Integer slowQueryCount;

    /** TPS/QPS 통계 */
    private Integer currentTps;
    private Integer currentQps;
    private Integer activeSessions;

    /** 쿼리 타입별 통계 */
    private Integer selectCount;
    private Integer insertCount;
    private Integer updateCount;
    private Integer deleteCount;

    /** 리소스 사용률 (현재값, 백분율) */
    private Double currentCpuUsagePercent;
    private Double currentMemoryUsagePercent;
    private Double currentDiskIoUsagePercent;

    /** 시간 정보 */
    private OffsetDateTime createdAt;
    private String timeRange;
}