package com.dajanggan.domain.system.memory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Memory 대시보드 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryDashboardResponse {
    // 실시간 위젯 (5개)
    private OsMemoryUsageWidget osMemoryUsage;
    private SwapUsageWidget swapUsage;
    private SharedBufferHitWidget sharedBufferHit;
    private BufferUsageWidget bufferUsage;
    private TempFileUsageWidget tempFileUsage;
    
    // 1시간 차트 (3개)
    private OsMemoryUsageChart1h osMemoryChart1h;
    private BufferCacheHitChart1h bufferCacheChart1h;
    private BufferUtilizationChart1h bufferUtilChart1h;
    
    // 6시간 차트 (2개)
    private TempFileChart6h tempFileChart6h;
    private IoWaitTimeChart6h ioWaitTimeChart6h;
    
    // 24시간 차트 (4개)
    private OsMemoryTrendChart24h osMemoryTrend24h;
    private SwapUsageTrendChart24h swapTrend24h;
    private BufferReuseScoreChart24h bufferReuseChart24h;
    private TopTablesByBufferChart24h topTablesChart24h;

    // ========================================
    // Widget DTOs
    // ========================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OsMemoryUsageWidget {
        private Double usagePercent;
        private String trend;
        private String status;
        private Long totalGB;
        private Long usedGB;
        private Long availableGB;
        private Long cacheGB;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SwapUsageWidget {
        private Double swapUsagePercent;
        private String status;
        private Long totalSwapGB;
        private Long usedSwapGB;
        private Long swapInPerSec;
        private Long swapOutPerSec;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SharedBufferHitWidget {
        private Double hitRatio;
        private String status;
        private Long cacheHits;
        private Long physicalReads;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BufferUsageWidget {
        private Double bufferUsagePercent;
        private Double dirtyRatio;
        private Double pinnedRatio;
        private String status;
        private Long usedBuffers;
        private Long totalBuffers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TempFileUsageWidget {
        private Double tempFileRate;
        private String status;
        private Long totalTempFiles;
        private Long totalTempMB;
        private String message;
    }

    // ========================================
    // Chart DTOs
    // ========================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OsMemoryUsageChart1h {
        private List<String> categories;
        private List<Double> usedGB;
        private List<Double> cacheGB;
        private List<Double> bufferGB;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BufferCacheHitChart1h {
        private List<String> categories;
        private List<Double> hitRatio;
        private Double warningThreshold;
        private Double normalThreshold;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BufferUtilizationChart1h {
        private List<String> categories;
        private List<Long> dirtyBuffers;
        private List<Long> pinnedBuffers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TempFileChart6h {
        private List<String> categories;
        private List<Long> tempFileCount;
        private List<Double> tempFileSizeMB;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IoWaitTimeChart6h {
        private List<String> categories;
        private List<Double> readWaitMs;
        private List<Double> writeWaitMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OsMemoryTrendChart24h {
        private List<String> categories;
        private List<Double> usagePercent;
        private Double warningThreshold;
        private Double dangerThreshold;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SwapUsageTrendChart24h {
        private List<String> categories;
        private List<Double> swapUsagePercent;
        private List<Long> swapInRate;
        private List<Long> swapOutRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BufferReuseScoreChart24h {
        private List<String> categories;
        private List<Double> reuseScore;
        private List<Double> avgUsagecount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopTablesByBufferChart24h {
        private List<String> tableNames;
        private List<Long> bufferCounts;
        private List<Double> usagePercent;
    }
}

