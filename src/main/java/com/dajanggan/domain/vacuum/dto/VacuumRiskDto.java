package com.dajanggan.domain.vacuum.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

public class VacuumRiskDto {

    // ========== Raw DTOs ==========
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BlockersPerHourRaw {
        private String hourLabel;
        private Integer blockersCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WraparoundProgressRaw {
        private Long databaseId;
        private Double wraparoundProgressPct;
    }

    // VacuumRiskDto.java에 추가
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopBloatRaw {
        private Long databaseId;
        private String tableName;
        private Long bloatBytes;
        private Double bloatRatio;
        private Long deadTuples;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VacuumBlockerDetailRaw {
        private Long databaseId;
        private String tableName;  // 추가
        private Integer pid;
        private String lockType;
        private Long transactionAge;
        private Long blockDuration;
        private String queryState;
    }

    // ========== Response DTOs ==========
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Chart blockers;
        private Chart autovacuum;
        private Chart wraparound;
        private List<TopBloatTable> bloat;
        private List<VacuumBlocker> vacuumblockers;
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
    public static class TopBloatTable {
        private String table;
        private String bloat;
        private String deadTuple;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VacuumBlocker {
        private String table;
        private String pid;
        private String lockType;
        private String txAge;
        private String blocked_seconds;
        private String status;
    }
}