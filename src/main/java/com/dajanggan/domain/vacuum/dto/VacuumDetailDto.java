// 작성자: 김민서
package com.dajanggan.domain.vacuum.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public class VacuumDetailDto {

    // ========== Raw DTOs ==========
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProgressRaw {
        private OffsetDateTime collectedAt;
        private Long heapBlksScanned;
        private Long heapBlksVacuumed;
        private Long tuplesDeleted;
        private Double elapsedSeconds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionInfoRaw {
        private OffsetDateTime sessionStartedAt;
        private OffsetDateTime collectedAt;
        private Boolean autovacuum;
        private String sessionPhase;
        private Long heapBlksTotal;
        private Long tuplesDeleted;
        private Long tuplesDeadButNotRemovable;
        private Long nDeadTup;
        private Long nLiveTup;
        private Long relsizeTotalBytes;
        private Double elapsedSeconds;
    }

    // ========== Response DTOs ==========
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String tableName;
        private String schema;
        private String startTime;
        private String endTime;
        private String duration;
        private Boolean autovacuum;
        private String role;
        private String heapBlocksTotal;
        private String deadTuplesPerPhase;
        private Progress progress;
        private Map<String, String> summary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Progress {
        private List<String> labels;
        private List<Double> scanned;
        private List<Double> vacuumed;
        private List<Double> deadRows;
    }
}