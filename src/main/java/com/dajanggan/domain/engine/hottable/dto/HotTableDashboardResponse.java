package com.dajanggan.domain.engine.hottable.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * HotTable 대시보드 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotTableDashboardResponse {
    private TopTables topTables;
    private TableActivity tableActivity;
    private CacheHitRatio cacheHitRatio;
    private BloatStatus bloatStatus;
    private VacuumStatus vacuumStatus;
    private RecentStats recentStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopTables {
        private List<TableSummary> topBySize;
        private List<TableSummary> topByScan;
        private List<TableSummary> topByBloat;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableSummary {
        private String schemaName;
        private String tableName;
        private Long value;
        private Double percentage;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableActivity {
        private List<String> categories;
        private List<Long> seqScans;
        private List<Long> idxScans;
        private List<Long> inserts;
        private List<Long> updates;
        private List<Long> deletes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheHitRatio {
        private List<String> categories;
        private List<Double> data;
        private Double average;
        private Double max;
        private Double min;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BloatStatus {
        private List<String> categories;
        private List<Double> data;
        private Long normalCount;
        private Long warningCount;
        private Long criticalCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VacuumStatus {
        private List<String> categories;
        private List<Long> delaySeconds;
        private Long avgDelaySeconds;
        private Long maxDelaySeconds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentStats {
        private Long totalTables;
        private Long activeTables;
        private Double avgCacheHitRatio;
        private Long totalSeqScans;
        private Long totalIdxScans;
        private Long highBloatTables;
    }
}



