package com.dajanggan.domain.vacuum.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * vacuum_trend_metrics 테이블 도메인
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacuumTrendMetrics {

    // 기본 정보
    private Long vacuumTrendMetricsId;
    private Long databaseId;
    private OffsetDateTime collectedAt;
    private OffsetDateTime createdAt;
    private String tableName;

    // 버킷 정보
    private Integer bucketWidthSeconds;
    private BigDecimal avgProgress;

    // 평균 지연시간
    private BigDecimal avgDelaySeconds;
    private BigDecimal avgVacuumDurationSeconds;

    // 24시간 Vacuum 횟수
    private Integer vacuumCount24h;
    private Integer autovacuumCount24h;
    private Integer failedVacuumCount24h;

    // 최근 1시간 내 실행된 테이블 수
    private Integer recentExecutedTables1h;

    // Dead Tuple 처리 속도
    private Long deadTupleProcessingRatePm;
    private Long deadTupleIncreaseRatePm;
    private Long deadTupleTotal;

    // ANALYZE 이후 변경된 튜플 수
    private Long nModSinceAnalyze;

    // Bloat 정보
    private Long bloatBytes;
    private BigDecimal bloatRatio;

    // Vacuum 이력
    private OffsetDateTime lastVacuum;
    private OffsetDateTime lastAutovacuum;

    // Worker 활용률
    private BigDecimal workerUtilizationPct;

    // 평균 Cost 지연시간
    private BigDecimal avgCostDelayMs;

    // 시간당 차단 정보
    private Integer blockersPerHour;
    private Long blockedSecondsPerHour;

    // Wraparound 진행률
    private BigDecimal wraparoundProgressPct;
}