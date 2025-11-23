package com.dajanggan.domain.query.dto.agg5m;

import lombok.*;

import java.time.OffsetDateTime;

/**
 * 슬로우 쿼리 리스트 DTO
 *
 * 기능:
 * - 실행 시간이 1000ms를 초과하는 슬로우 쿼리 목록
 * - query_hash 기준으로 그룹화된 집계 정보 포함
 * - 대표 쿼리 정보 + 개별 실행 메트릭 + 그룹 집계 메트릭
 * - SlowQuery 화면에서 쿼리별 성능 분석용
 *
 * @author 이해든
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class SlowQueryListDto {

    // 대표 쿼리 정보 (가장 최근 실행된 쿼리)
    private Long queryMetricId;
    private OffsetDateTime collectedAt;
    private String queryText;
    private String shortQuery;
    private String queryHash;
    private String username;
    private String queryType;

    // 개별 실행 메트릭 (대표 쿼리의 실행 정보)
    private Double executionTimeMs;
    private Double cpuUsagePercent;
    private Double memoryUsageMb;
    private Integer ioBlocks;

    // 그룹화 집계 메트릭 (query_hash 기준 통계)
    private Integer executionCount;
    private Double avgExecutionTimeMs;
    private Double maxExecutionTimeMs;
    private Double minExecutionTimeMs;
    private Long totalIoBlocks;
    private OffsetDateTime firstSeenAt;
    private OffsetDateTime lastSeenAt;
}