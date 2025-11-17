package com.dajanggan.domain.vacuum.dto.agg;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Vacuum 5분 집계 DTO
 */
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

    // 진행률 통계
    private Double avgProgress;

    // 테이블 통계
    private Integer tablesWithDeadTuples;
    private Integer tablesBeingVacuumed;

    // 시간 통계
    private Double avgElapsedSeconds;
    private Double maxElapsedSeconds;

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

    private OffsetDateTime createdAt;
}