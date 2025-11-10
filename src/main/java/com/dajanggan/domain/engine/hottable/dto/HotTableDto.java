package com.dajanggan.domain.engine.hottable.dto;


import lombok.Data;

import java.util.List;

public class HotTableDto {

    @Data
    public static class DashboardResponse {
        private CacheHitRatio cacheHitRatio;
        private VacuumDelayTrend vacuumDelayTrend;
        private DeadTupleTrend deadTupleTrend;
        private TotalDeadTuple totalDeadTuple;
        private TopQueryTables topQueryTables;
        private TopDmlTables topDmlTables;
        private RecentStats recentStats;
    }

    @Data
    public static class CacheHitRatio {
        private String tableName;
        private double value;
        private long bufferHits;
        private long diskReads;
    }

    @Data
    public static class NamedSeries {
        private String name;
        private List<Double> data;
    }

    @Data
    public static class VacuumDelayTrend {
        private List<String> categories;
        private List<NamedSeries> tables;
    }

    @Data
    public static class DeadTupleTrend {
        private List<String> categories;
        private List<NamedSeries> tables;
    }

    @Data
    public static class TotalDeadTuple {
        private List<String> categories;
        private List<Double> data;
        private double total;
        private double average;
        private double max;
    }

    @Data
    public static class TopQueryTables {
        private List<String> tableNames;
        private List<Double> seqScanCounts;
        private List<Double> indexScanCounts;
    }

    @Data
    public static class TopDmlTables {
        private List<String> tableNames;
        private List<Double> insertCounts;
        private List<Double> updateCounts;
        private List<Double> deleteCounts;
    }

    @Data
    public static class RecentStats {
        private double hotUpdateRatio;
        private String liveDeadTupleRatio;
        private long deadTupleCount;
        private double seqScanRatio;
        private double updateDeleteRatio;
        private double avgVacuumDelay;
        private double totalBloat;
    }
}
