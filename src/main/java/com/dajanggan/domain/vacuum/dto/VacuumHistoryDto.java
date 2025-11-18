package com.dajanggan.domain.vacuum.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.sql.Timestamp;

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
        private String lastVacuum;
        private String lastAutovacuum;
        private String deadTuples;
        private String modSinceAnalyze;
        private String bloatRatio;
        private String tableSize;
        private String frequency;
        private String status;  // "주의" or "정상"
    }
}
