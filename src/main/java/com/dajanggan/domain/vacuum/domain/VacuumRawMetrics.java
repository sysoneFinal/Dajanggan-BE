package com.dajanggan.domain.vacuum.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * vacuum_raw_metrics 테이블 도메인
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacuumRawMetrics {

    // 기본 정보
    private Long vacuumRawMetricsId;
    private Long databaseId;
    private LocalDateTime collectedAt;
    private LocalDateTime createdAt;

    // Vacuum 세션 정보
    private Long backendXid;
    private Boolean autovacuum;
    private String sessionTrigger;
    private String sessionPhase;
    private Double sessionProgress;
    private LocalDateTime sessionStartedAt;
    private Integer elapsedSeconds;

    // 대기 이벤트
    private String waitEventType;
    private String waitEvent;

    // Progress 단계별 정보
    private Integer progressInitializing;
    private Long progressScanningHeap;
    private Long progressVacuumingHeap;
    private Long progressVacuumingCleanup;
    private Long progressTruncatingHeap;

    // Heap 블록 통계
    private Long heapBlksTotal;
    private Long heapBlksScanned;
    private Long heapBlksVacuumed;

    // Index 관련
    private Long indexVacuumCount;
    private Long pagesRemoved;
    private Long pagesSkippedDueToPin;
    private Long pagesSkippedFrozen;

    // Tuple 통계
    private Long tuplesDeleted;
    private Long tuplesDeadButNotRemovable;
    private Long nDeadTup;
    private Long nLiveTup;

    // Vacuum 이력
    private LocalDateTime lastVacuum;
    private LocalDateTime lastAutovacuum;
    private Long nModSinceAnalyze;

    // 테이블 크기 정보
    private Long relsizeTotalBytes;
    private Long relsizeHeapBytes;
    private Long relsizeToastBytes;
    private Long relsizeIndexesBytes;

    // Vacuum 실행 횟수
    private Integer runningVacuumCount;
    private Integer activeWorkers;
    private Integer maxWorkers;

    // Cost 관련
    private Integer autovacuumCostDelayMs;
    private Integer autovacuumCostLimit;

    // Blocker 정보
    private Boolean isBlocked;
    private Integer blockerPid;
    private String blockerLockMode;
    private Integer blockedSeconds;
    private Long blockerXminHorizon;

    // 트랜잭션 상태
    private String blockerTransactionState;
    private Long ageCurrentXid;
    private Long ageMaxFreeze;

    // Wraparound 관련
    private Double wraparoundProgress;
    private String wraparoundRiskLevel;
}