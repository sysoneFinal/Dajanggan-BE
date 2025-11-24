package com.dajanggan.domain.query.dto.agg1m;

import lombok.*;
import java.time.OffsetDateTime;

/**
 * 1분 단위 쿼리 집계 DTO
 * - 실시간 모니터링용 데이터 (쿼리 성능, 실행 횟수 중심)
 * - QueryOverview 및 ExecutionStatus 페이지에서 사용
 *
 * @author 이해든
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryAgg1mDto {

    /** 데이터베이스 식별 정보 */
    private Long instanceId;
    private Long databaseId;
    private OffsetDateTime collectedAt;

    /** 쿼리 개수 통계 */
    private Integer totalQueries;
    private Integer selectQueries;
    private Integer insertQueries;
    private Integer updateQueries;
    private Integer deleteQueries;
    private Integer otherQueries;

    /** 실행 시간 통계 (밀리초) */
    private Double avgExecutionTimeMs;
    private Double maxExecutionTimeMs;
    private Double avgPlanningTimeMs;

    /** I/O 통계 */
    private Long totalIoBlocks;
    private Double avgIoBlocks;

    /** 슬로우 쿼리 통계 */
    private Integer slowQueryCount;

    /** 리소스 사용률 (현재값, 백분율) */
    private Double currentCpuUsagePercent;
    private Double currentMemoryUsagePercent;
    private Double currentDiskIoUsagePercent;

    /** Top Query 조회용 필드 */
    private Long queryMetricId;
    private String queryText;
    private String shortQuery;
    private String queryType;
    private Integer executionCount;
    private Double cpuUsagePercent;
    private Double memoryUsageMb;

    /** 평균 리소스 사용량 */
    private Double avgCpuUsagePercent;
    private Double avgMemoryUsageMb;

    /** 데이터 생성 시각 */
    private OffsetDateTime createdAt;
}