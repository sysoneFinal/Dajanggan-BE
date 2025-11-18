package com.dajanggan.domain.engine.hotindex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * HotIndex 대시보드 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotIndexDashboardResponse {
    private UsageDistribution usageDistribution;
    private TopUsage topUsage;
    private InefficientIndexes inefficientIndexes;
    private CacheHitRatio cacheHitRatio;
    private Efficiency efficiency;
    private AccessTrend accessTrend;
    private ScanSpeed scanSpeed;
    private RecentStats recentStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsageDistribution {
        private List<String> categories;
        private List<Long> data;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopUsage {
        private List<String> categories;
        private List<Long> data;
        private Long total;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InefficientIndexes {
        private List<String> categories;
        private List<Double> data;
        private Long total;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheHitRatio {
        private List<String> categories;
        private List<Double> data;
        private Double average;
        private Double min;
        private Double max;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Efficiency {
        private List<String> categories;
        private List<IndexEfficiency> indexes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndexEfficiency {
        private Long x;
        private Double y;
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessTrend {
        private List<String> categories;
        private List<Long> reads;
        private List<Long> writes;
        private Long totalReads;
        private Long totalWrites;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScanSpeed {
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
    public static class RecentStats {
        private Double cacheHitRatio;
        private Double avgScanSpeed;
        private Long totalReads;
        private Long totalWrites;
        private Long inefficientCount;
    }
}

