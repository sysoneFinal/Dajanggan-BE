package com.dajanggan.domain.vacuum.domain;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VacuumMaintenance {

    private Long id;
    private String databaseId;
    private LocalDateTime collectedAt;

    // Raw Metrics
    private String sessionPhase;
    private Double sessionProgress;
    private Boolean autovacuum;
    private Long elapsedSeconds;

    // Trend Metrics
    private Double avgDelaySeconds;
    private Double avgVacuumDurationSeconds;
    private Long deadTupleTotal;
    private Double avgCostDelayMs;
    private Integer activeWorkers;

    // 비즈니스 메서드 (예시)

    /**
     * 세션이 실행 중인지 확인
     */
    public boolean isRunning() {
        return sessionPhase != null && !"completed".equals(sessionPhase);
    }

    /**
     * Autovacuum 여부
     */
    public boolean isAutovacuum() {
        return Boolean.TRUE.equals(autovacuum);
    }

    /**
     * 진행률 백분율 계산
     */
    public int getProgressPercentage() {
        return sessionProgress != null
                ? (int) Math.round(sessionProgress)
                : 0;
    }
}