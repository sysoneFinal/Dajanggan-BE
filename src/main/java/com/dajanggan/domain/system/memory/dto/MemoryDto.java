package com.dajanggan.domain.system.memory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class MemoryDto {

    /**
     * Memory 대시보드 전체 응답
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardResponse {
        private MemoryUtilization memoryUtilization;
        private BufferHitRatio bufferHitRatio;
        private SharedBufferUsage sharedBufferUsage;
        private EvictionRate evictionRate;
        private FsyncRate fsyncRate;  // 오타 수정: fsyncRasizeMbte -> fsyncRate
        private DirtyBufferTrend dirtyBufferTrend;
        private EvictionFlushRatio evictionFlushRatio;
        private TopBufferObjects topBufferObjects;
        private SummaryStats summaryStats;
    }

    /**
     * Memory 사용률
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemoryUtilization {
        private Double value;
        private Long usedBuffers;
        private Long totalBuffers;
    }

    /**
     * Buffer Hit 비율
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BufferHitRatio {
        private Double value;
        private Long hitCount;
        private Long totalCount;
    }

    /**
     * Shared Buffer 사용량
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SharedBufferUsage {
        private Double value;
        private Long activeBuffers;
        private Long totalBuffers;
    }

    /**
     * Eviction Rate (시계열)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvictionRate {
        private List<String> categories;
        private List<Long> data;
        private Double average;
        private Long max;
        private Long min;
    }

    /**
     * Fsync Rate (시계열)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FsyncRate {
        private List<String> categories;
        private List<Long> data;
        private Double average;
        private Long max;
        private Long backendFsync;
    }

    /**
     * Dirty Buffer 추세
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DirtyBufferTrend {
        private List<String> categories;
        private List<Long> data;
        private Double average;
        private Long max;
        private Long min;
    }

    /**
     * Eviction vs Flush 비교
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvictionFlushRatio {
        private List<String> categories;
        private List<Long> evictions;
        private List<Long> fsyncs;
    }

    /**
     * 상위 버퍼 사용 객체
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopBufferObjects {
        private List<String> labels;
        private List<Double> data;
        private List<String> types;
    }

    /**
     * 요약 통계
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryStats {
        private Double dirtyBufferRatio;
        private Double backendWaitTime;
        private Double workMemUsage;
        private Double tempFileUsage;
        private Double checkpointInterval;
    }

    /**
     * Memory 리스트 응답
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
     * Memory 리스트 아이템
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListItem {
        private String id;
        private String objectName;
        private String type;
        private Double sizeMb;
        private Long bufferCount;
        private Double usagePercent;
        private Long dirtyCount;
        private Double dirtyPercent;
        private Long pinnedBuffers;
        private Double hitPercent;
        private Long accessCount;
        private Long evictionCount;
        private Double avgAccessTime;
        private String status;
    }
}