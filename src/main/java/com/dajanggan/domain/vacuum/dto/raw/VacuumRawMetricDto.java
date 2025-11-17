package com.dajanggan.domain.vacuum.dto.raw;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Vacuum Raw 메트릭 DTO
 * pg_stat_progress_vacuum 및 pg_stat_all_tables에서 수집
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacuumRawMetricDto {

    // 기본 정보
    private Long databaseId;
    private Long instanceId;
    private OffsetDateTime collectedAt;
    private String tableName;
    private String schemaName;

    // Vacuum 진행 정보 (pg_stat_progress_vacuum)
    private String sessionPhase;
    private Double sessionProgress;
    private Boolean autovacuum;
    private Long heapBlksTotal;
    private Long heapBlksScanned;
    private Long heapBlksVacuumed;
    private Long indexVacuumCount;

    // Tuple 정보 (pg_stat_all_tables)
    private Long nDeadTup;
    private Long nLiveTup;
    private Long nModSinceAnalyze;
    private Long tuplesDeleted;

    // Vacuum 세션 정보
    private OffsetDateTime sessionStartedAt;
    private Double elapsedSeconds;
    private String sessionTrigger;  // 'manual' or 'autovacuum'

    // 테이블 크기
    private Long relsizeTotalBytes;

    // 생성 시간
    private OffsetDateTime createdAt;
}