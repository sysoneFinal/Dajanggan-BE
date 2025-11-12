package com.dajanggan.domain.engine.hottable.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class HotTableDto {

    /**
     * HotTable 대시보드 전체 응답
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardResponse {
        private TopTables topTables;
        private TableActivity tableActivity;
        private CacheHitRatio cacheHitRatio;
        private BloatStatus bloatStatus;
        private VacuumStatus vacuumStatus;
        private RecentStats recentStats;
    }

    /**
     * Top 테이블 (크기별, 스캔별)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopTables {
        private List<TableSummary> topBySize;      // 크기 Top 5
        private List<TableSummary> topByScan;      // 스캔 Top 5
        private List<TableSummary> topByBloat;     // Bloat Top 5
    }

    /**
     * 테이블 요약 정보
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableSummary {
        private String schemaName;
        private String tableName;
        private Long value;            // 크기, 스캔 횟수 등
        private Double percentage;     // 비율 (선택)
        private String status;         // 상태
    }

    /**
     * 테이블 활동 (시계열)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableActivity {
        private List<String> categories;       // 시간 카테고리
        private List<Long> seqScans;          // Sequential Scan
        private List<Long> idxScans;          // Index Scan
        private List<Long> inserts;           // Insert
        private List<Long> updates;           // Update
        private List<Long> deletes;           // Delete
    }

    /**
     * 캐시 히트율
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheHitRatio {
        private List<String> categories;       // 시간 카테고리
        private List<Double> data;             // 캐시 히트율 데이터
        private Double average;                // 평균
        private Double max;                    // 최대값
        private Double min;                    // 최소값
    }

    /**
     * Bloat 상태
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BloatStatus {
        private List<String> categories;       // 테이블명
        private List<Double> data;             // Bloat 비율
        private Long normalCount;              // 정상 테이블 수
        private Long warningCount;             // 주의 테이블 수
        private Long criticalCount;            // 위험 테이블 수
    }

    /**
     * Vacuum 상태
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VacuumStatus {
        private List<String> categories;       // 테이블명
        private List<Long> delaySeconds;       // Vacuum 지연 시간(초)
        private Long avgDelaySeconds;          // 평균 지연 시간
        private Long maxDelaySeconds;          // 최대 지연 시간
    }

    /**
     * 최근 통계 (Summary Cards)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentStats {
        private Long totalTables;              // 전체 테이블 수
        private Long activeTables;             // 활성 테이블 수
        private Double avgCacheHitRatio;       // 평균 캐시 히트율
        private Long totalSeqScans;            // 총 Sequential Scan
        private Long totalIdxScans;            // 총 Index Scan
        private Long highBloatTables;          // High Bloat 테이블 수 (>=15%)
    }

    /**
     * HotTable 리스트 응답
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListResponse {
        private List<ListItem> data;
        private Long total;
    }

    /**
     * HotTable 리스트 아이템
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListItem {
        private String id;
        private String tableName;           // 테이블명
        private String schemaName;          // 스키마명
        private String size;                // 크기 (포맷된 문자열)
        private Long seqScan;               // Seq Scan
        private Long seqTupRead;            // Seq Tup Read
        private Long idxScan;               // Idx Scan
        private Long idxTupFetch;           // Idx Tup Fetch
        private Long nTupIns;               // Insert
        private Long nTupUpd;               // Update
        private Long nTupDel;               // Delete
        private Long nTupHotUpd;            // HOT Update
        private Long nLiveTup;              // Live 튜플
        private Long nDeadTup;              // Dead 튜플
        private Double bloatPercent;        // Bloat(%)
        private String lastVacuum;          // 마지막 VACUUM
        private String lastAutoVacuum;      // 마지막 Auto VACUUM
        private Double cacheHit;            // 캐시 Hit(%)
        private String status;              // 상태 (정상, 주의, 위험)
    }
}