package com.dajanggan.domain.vacuum.domain;

import lombok.*;

import java.time.OffsetDateTime;

/**
 * VacuumRawMetrics Entity
 *
 * 테이블: vacuum_raw_metrics
 *
 * 주요 책임:
 * - Vacuum 메트릭 원본 데이터 관리
 * - Bloat, Dead Tuple, Progress 정보 포함
 * - Wraparound 위험도 계산
 *
 *
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-06  김민서    1. 최초작성
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VacuumRawMetrics {

    // ========== 기본 정보 ==========
    private Long vacuumRawMetricsId;
    private Long databaseId;
    private Long instanceId;
    private OffsetDateTime collectedAt;
    private String tableName;
    private String schemaName;

    // ========== Vacuum 진행 정보 (pg_stat_progress_vacuum) ==========
    private Long backendPid;
    private String sessionPhase;
    @Builder.Default
    private Double sessionProgress = 0.0;
    @Builder.Default
    private Boolean autovacuum = false;
    private Long heapBlksTotal;
    private Long heapBlksScanned;
    private Long heapBlksVacuumed;
    private Long indexVacuumCount;

    // ========== Wait Event 정보 ==========
    private String waitEventType;
    private String waitEvent;

    // ========== Progress 상세 정보 ==========
    @Builder.Default
    private Boolean progressInitializing = false;
    @Builder.Default
    private Boolean progressScanningHeap = false;
    @Builder.Default
    private Boolean progressVacuumingHeap = false;
    @Builder.Default
    private Boolean progressVacuumingCleanup = false;
    @Builder.Default
    private Boolean progressTruncatingHeap = false;

    // ========== 페이지 정보 ==========
    private Long pagesRemoved;
    private Long pagesSkippedDueToPin;
    private Long pagesSkippedFrozen;

    // ========== Tuple 정보 (pg_stat_all_tables) ==========
    private Long nDeadTup;
    private Long nLiveTup;
    private Long nModSinceAnalyze;
    private Long tuplesDeleted;
    private Long tuplesDeadButNotRemovable;

    // ========== Vacuum 세션 정보 ==========
    private OffsetDateTime sessionStartedAt;
    private Double elapsedSeconds;
    private String sessionTrigger;

    // ========== 테이블 크기 ==========
    private Long relsizeTotalBytes;
    private Long relsizeHeapBytes;
    private Long relsizeToastBytes;
    private Long relsizeIndexesBytes;

    // ========== Vacuum 실행 카운트 ==========
    @Builder.Default
    private Integer runningVacuumCount = 0;

    // ========== Bloat 정보 ==========
    private Long bloatBytes;
    private Double bloatRatio;
    private String indexBloatInfo;  // JSON

    // ========== Xmin Horizon & Blocker 정보 ==========
    private Long blockerXminHorizon;
    private Integer blockedSeconds;
    private Integer blockerPid;
    private String blockerLockMode;
    private String blockerTransactionState;
    private String blockerQuery;
    @Builder.Default
    private Boolean isBlocked = false;
    private String queryState;
    private Long transactionAge;

    // ========== Autovacuum 설정 ==========
    private Integer autovacuumCostDelayMs;
    private Integer autovacuumCostLimit;
    private Integer maxWorkers;
    @Builder.Default
    private Integer activeWorkers = 0;

    // ========== Wraparound 정보 ==========
    private Long ageCurrentXid;
    private Long ageMaxFreeze;
    private Double wraparoundProgress;
    private String wraparoundRiskLevel;

    // ========== Vacuum 이력 ==========
    private OffsetDateTime lastVacuum;
    private OffsetDateTime lastAutovacuum;

    // ========== 생성 시간 ==========
    private OffsetDateTime createdAt;

    // ========================================================================
    // 비즈니스 로직
    // ========================================================================

    /**
     * Vacuum 진행 중 여부 확인
     */
    public boolean isVacuumRunning() {
        return sessionPhase != null && !sessionPhase.isEmpty();
    }

    /**
     * Autovacuum 여부 확인
     */
    public boolean isAutovacuum() {
        return Boolean.TRUE.equals(this.autovacuum);
    }

    /**
     * Bloat 위험 수준 확인
     */
    public boolean isHighBloat() {
        return bloatRatio != null && bloatRatio > 0.3; // 30% 이상
    }

    /**
     * Critical Bloat 여부
     */
    public boolean isCriticalBloat() {
        return bloatRatio != null && bloatRatio > 0.5; // 50% 이상
    }

    /**
     * Wraparound 위험 수준 확인
     */
    public boolean isWraparoundRisk() {
        return wraparoundProgress != null && wraparoundProgress > 0.75; // 75% 이상
    }

    /**
     * Critical Wraparound 여부
     */
    public boolean isCriticalWraparound() {
        return wraparoundProgress != null && wraparoundProgress > 0.9; // 90% 이상
    }

    /**
     * Blocker 존재 여부
     */
    public boolean hasBlocker() {
        return Boolean.TRUE.equals(this.isBlocked) && blockerPid != null;
    }

    /**
     * Dead Tuple 비율 계산
     */
    public double getDeadTupleRatio() {
        if (nLiveTup == null || nLiveTup == 0) return 0.0;
        if (nDeadTup == null) return 0.0;
        return (double) nDeadTup / (nLiveTup + nDeadTup);
    }

    /**
     * Vacuum 진행률 계산 (블록 기준)
     */
    public double calculateVacuumProgress() {
        if (heapBlksTotal == null || heapBlksTotal == 0) return 0.0;
        if (heapBlksVacuumed == null) return 0.0;
        return ((double) heapBlksVacuumed / heapBlksTotal) * 100.0;
    }

    /**
     * Bloat 크기를 MB 단위로 반환
     */
    public double getBloatSizeMB() {
        if (bloatBytes == null) return 0.0;
        return bloatBytes / (1024.0 * 1024.0);
    }

    /**
     * 테이블 크기를 MB 단위로 반환
     */
    public double getTableSizeMB() {
        if (relsizeTotalBytes == null) return 0.0;
        return relsizeTotalBytes / (1024.0 * 1024.0);
    }

    /**
     * Vacuum 세션 지속 시간 (초)
     */
    public long getSessionDurationSeconds() {
        if (sessionStartedAt == null || collectedAt == null) return 0L;
        return java.time.Duration.between(sessionStartedAt, collectedAt).getSeconds();
    }
}
