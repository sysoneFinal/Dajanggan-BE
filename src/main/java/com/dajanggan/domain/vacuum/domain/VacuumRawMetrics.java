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

    private Long id;
    private String databaseId;
    private LocalDateTime collectedAt;
    private LocalDateTime createdAt;

    // Xmin Horizon 관련 (대시보드에서 사용)
    private Long blockerXminHorizon;

    // Vacuum 처리 속도 계산용
    private Long tuplesDeleted;
    private Integer elapsedSeconds;

    // Dead Tuple 관련
    private Long nDeadTup;
    private Long nLiveTup;
}