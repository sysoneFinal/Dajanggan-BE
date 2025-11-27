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
public class VacuumAgg5mDto {

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

    private Double avgCostDelayMs;

    // 대기 시간 정보 (추가됨!)
    private Double avgBlockedSeconds;



    // Worker 통계
    private Double workerUtilizationPct;
    private Integer maxWorkersConfigured;

    //  Bloat 관련 추가
    private Long avgBloatBytes;
    private Long maxBloatBytes;
    private Long totalBloatBytes;
    private Double avgBloatRatio;
    private Double maxBloatRatio;
    private Integer criticalBloatTables;
    private Long totalTableSizeBytes;

    // Index Bloat 통계
    private Long totalIndexBloatBytes;

    // Top 5 Bloat 테이블
    private String topBloatTable1;
    private Long topBloatTable1Bytes;
    private String topBloatTable2;
    private Long topBloatTable2Bytes;
    private String topBloatTable3;
    private Long topBloatTable3Bytes;
    private String topBloatTable4;
    private Long topBloatTable4Bytes;
    private String topBloatTable5;
    private Long topBloatTable5Bytes;


    // Top 5 테이블 (dead tuple 기준)
    private String topTable1;
    private Long topTable1DeadTuples;
    private String topTable2;
    private Long topTable2DeadTuples;
    private String topTable3;
    private Long topTable3DeadTuples;
    private String topTable4;
    private Long topTable4DeadTuples;
    private String topTable5;
    private Long topTable5DeadTuples;


    private String topBlockerTable1;
    private Integer topBlockerTable1Seconds;
    private String topBlockerTable2;
    private Integer topBlockerTable2Seconds;
    private String topBlockerTable3;
    private Integer topBlockerTable3Seconds;


    private Integer blockedVacuumCount;
    private Integer maxBlockedSeconds;



    private OffsetDateTime createdAt;
}