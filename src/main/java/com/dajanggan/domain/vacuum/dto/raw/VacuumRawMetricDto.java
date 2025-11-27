// 작성자: 김민서
package com.dajanggan.domain.vacuum.dto.raw;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacuumRawMetricDto {
    private Long vacuumRawMetricsId;

    // 기본 정보
    private Long databaseId;
    private Long instanceId;
    private OffsetDateTime collectedAt;
    private String tableName;
    private String schemaName;

    // Vacuum 진행 정보 (pg_stat_progress_vacuum)
    private Long backendPid;
    private String sessionPhase;
    private Double sessionProgress;
    private Boolean autovacuum;
    private Long heapBlksTotal;
    private Long heapBlksScanned;
    private Long heapBlksVacuumed;
    private Long indexVacuumCount;

    // Wait Event 정보
    private String waitEventType;
    private String waitEvent;

    // Progress 상세 정보
    private Boolean progressInitializing;
    private Boolean progressScanningHeap;
    private Boolean progressVacuumingHeap;
    private Boolean progressVacuumingCleanup;
    private Boolean progressTruncatingHeap;

    // 페이지 정보
    private Long pagesRemoved;
    private Long pagesSkippedDueToPin;
    private Long pagesSkippedFrozen;

    // Tuple 정보 (pg_stat_all_tables)
    private Long nDeadTup;
    private Long nLiveTup;
    private Long nModSinceAnalyze;
    private Long tuplesDeleted;
    private Long tuplesDeadButNotRemovable;

    // Vacuum 세션 정보
    private OffsetDateTime sessionStartedAt;
    private Double elapsedSeconds;
    private String sessionTrigger;

    // 테이블 크기
    private Long relsizeTotalBytes;
    private Long relsizeHeapBytes;
    private Long relsizeToastBytes;
    private Long relsizeIndexesBytes;

    // Vacuum 실행 카운트
    private Integer runningVacuumCount;

    // Bloat 정보
    private Long bloatBytes;
    private Double bloatRatio;
    private String indexBloatInfo;  // JSON

    // Xmin Horizon & Blocker 정보
    private Long blockerXminHorizon;
    private Integer blockedSeconds;
    private Integer blockerPid;
    private String blockerLockMode;
    private String blockerTransactionState;
    private String blockerQuery;
    private Boolean isBlocked;
    private String queryState;
    private Long transactionAge;

    // Autovacuum 설정
    private Integer autovacuumCostDelayMs;
    private Integer autovacuumCostLimit;
    private Integer maxWorkers;
    private Integer activeWorkers;

    // Wraparound 정보
    private Long ageCurrentXid;
    private Long ageMaxFreeze;
    private Double wraparoundProgress;
    private String wraparoundRiskLevel;

    // Vacuum 이력
    private OffsetDateTime lastVacuum;
    private OffsetDateTime lastAutovacuum;

    // 생성 시간
    private OffsetDateTime createdAt;
}