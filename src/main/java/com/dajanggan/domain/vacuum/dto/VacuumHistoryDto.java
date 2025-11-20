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
        private Integer hours;        // 시간 필터 (1, 6, 24, 168)
        private String status;        // 상태 필터 ("정상", "주의", null=전체)
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
        private String vacuumType;      // "vacuum" or "autovacuum"
        private Integer durationSeconds;
        private String deadTuples;
        private String bloatRatio;
        private String status;          // "정상" or "주의"
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
