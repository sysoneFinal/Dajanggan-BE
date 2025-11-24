package com.dajanggan.domain.vacuum.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.sql.Timestamp;
import java.time.OffsetDateTime;

public class VacuumHistoryDto {

    // ========== Request DTOs ==========
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        private Long databaseId;
        private Integer hours;
        private String status;
    }

    // ========== Raw DTOs ==========
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Raw {
        private Long databaseId;
        private String tableName;
        private Timestamp lastVacuum;
        private Timestamp lastAutovacuum;
        private Long deadTuples;
        private Long modSinceAnalyze;
        private Long bloatBytes;
        private Double bloatRatio;
        private Integer vacuumCount24h;
        private Integer autovacuumCount24h;
    }

    // ========== Response DTOs ==========
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String table;
        private String executedAt;
        private String vacuumType;
        private Integer durationSeconds;
        private String deadTuples;
        private String bloatRatio;
        private String status;
    }

    // VacuumHistoryDto.java에 추가
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entity {
        private Long historyId;
        private Long databaseId;
        private Long instanceId;
        private String tableName;
        private String schemaName;
        private String vacuumType;
        private OffsetDateTime executedAt;
        private Integer durationSeconds;
        private Long deadTuplesBefore;
        private Double bloatRatioBefore;
        private String status;
    }
}
