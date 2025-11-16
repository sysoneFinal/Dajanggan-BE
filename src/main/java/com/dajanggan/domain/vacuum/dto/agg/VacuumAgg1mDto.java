package com.dajanggan.domain.vacuum.dto.agg;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Vacuum 1분 집계 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacuumAgg1mDto {

    private Long databaseId;
    private Long instanceId;
    private OffsetDateTime collectedAt;

    // Vacuum 세션 통계
    private Integer totalVacuumSessions;      // 총 vacuum 세션 수
    private Integer activeVacuumSessions;     // 활성 vacuum 세션 수
    private Integer autovacuumSessions;       // autovacuum 세션 수
    private Integer manualVacuumSessions;     // 수동 vacuum 세션 수

    // Dead Tuple 통계
    private Long avgDeadTuples;               // 평균 dead tuple 수
    private Long maxDeadTuples;               // 최대 dead tuple 수
    private Long totalDeadTuples;             // 총 dead tuple 수

    // 진행률 통계
    private Double avgProgress;               // 평균 진행률 (%)

    // 테이블 통계
    private Integer tablesWithDeadTuples;     // dead tuple이 있는 테이블 수
    private Integer tablesBeingVacuumed;      // vacuum 중인 테이블 수

    // 시간 통계
    private Double avgElapsedSeconds;         // 평균 경과 시간
    private Double maxElapsedSeconds;         // 최대 경과 시간

    private OffsetDateTime createdAt;
}