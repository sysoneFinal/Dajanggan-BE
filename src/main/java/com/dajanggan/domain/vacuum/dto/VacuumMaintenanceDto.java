package com.dajanggan.domain.vacuum.dto;

import lombok.*;
import java.util.List;

public class VacuumMaintenanceDto {

    // ========== Raw DTOs ==========
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VacuumSessionRaw {
        private Long databaseId;
        private String tableName;
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
        private Integer blockedSessions;
        private Double avgRunningTime;
        private Long totalDeadTuples;
        private String activeWorkers;
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
        private Long databaseId;
        private String tableName;
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