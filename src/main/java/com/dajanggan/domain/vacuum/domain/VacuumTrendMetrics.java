package com.dajanggan.domain.vacuum.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * vacuum_trend_metrics 테이블 도메인
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacuumTrendMetrics {

    private Long id;
    private String databaseId;
    private LocalDateTime collectedAt;
    private LocalDateTime createdAt;

    // Bloat 관련 (대시보드에서 주로 사용)
    private Long bloatBytes;
    private Double bloatRatio;

    // Wraparound 관련
    private Double wraparoundProgressPct;

    // 차단 관련
    private Integer blockersPerHour;
    private Long blockedSecondsPerHour;
}