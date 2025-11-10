package com.dajanggan.domain.vacuum.dto;

import lombok.*;
import java.util.List;

public class VacuumMaintenanceDto {

    // ========== Raw DTOs ==========
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VacuumTrendRaw {
        private String hourLabel;
        private Double deadTupleIncreaseRate;
        private Double avgProgress;
        private Double avgCostDelayMs;
        private Integer activeWorkers;
        private Double avgDelaySeconds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VacuumSessionRaw {
        private String databaseId;
        private String sessionPhase;
        private Double sessionProgress;
        private Boolean autovacuum;
        private Long elapsedSeconds;
        private Long heapBlksTotal;
        private Long heapBlksScanned;
        private Long heapBlksVacuumed;
        private Long deadTupleTotal; //  trend dto
    }

    // ========== Response DTOs ==========
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Kpi {
        private Double avgDelay;           // 평균 지연시간
        private Double avgDuration;        // 평균 Duration
        private Double deadTupleTotal;     // 총 Dead Tuple (M)
        private Integer autovacuumWorker;  // Worker 활동률 (%)
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Chart {
        private List<? extends List<? extends Number>> data;
        private List<String> labels;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Session {
        private String table;
        private String phase;
        private String deadTuples;
        private String trigger;
        private String elapsed;
        private List<Integer> progressSeries;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Kpi kpi;
        private Chart deadtuple;
        private Chart autovacuum;
        private Chart latency;
        private List<Session> sessions;
    }
}