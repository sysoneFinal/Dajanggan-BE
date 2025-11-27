// 작성자: 김민서
package com.dajanggan.domain.vacuum.dto.agg;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacuumAgg1mDto {

    private Long databaseId;
    private Long instanceId;
    private OffsetDateTime collectedAt;

    // Vacuum 세션 통계
    private Integer totalVacuumSessions;
    private Integer activeVacuumSessions;
    private Integer autovacuumSessions;
    private Integer manualVacuumSessions;

    // Dead Tuple 통계
    private Long avgDeadTuples;
    private Long maxDeadTuples;
    private Long totalDeadTuples;
    private Long deadTupleIncreaseRate;
    private Long deadTupleDecreaseRate;
    private Long netDeadTupleChange;


    // 진행률 통계
    private Double avgProgress;

    // 테이블 통계
    private Integer tablesWithDeadTuples;
    private Integer tablesBeingVacuumed;

    // 시간 통계
    private Double avgElapsedSeconds;
    private Double maxElapsedSeconds;

    // 대기 시간 정보 (추가됨!)
    private Double avgBlockedSeconds;
    private Double avgCostDelayMs;


    // Worker 통계
    private Double workerUtilizationPct;
    private Integer maxWorkersConfigured;


    //Bloat 통계
    private Long avgBloatBytes;
    private Long maxBloatBytes;
    private Long totalBloatBytes;
    private Double avgBloatRatio;
    private Double maxBloatRatio;
    private Integer criticalBloatTables;
    private Long totalTableSizeBytes;

    // Index Bloat 통계
    private Long totalIndexBloatBytes;

    private Integer blockedVacuumCount;
    private Integer maxBlockedSeconds;

    private OffsetDateTime createdAt;
}