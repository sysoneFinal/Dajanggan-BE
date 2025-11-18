package com.dajanggan.domain.system.disk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Disk I/O 대시보드 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiskIoDashboardResponse {
    // 실시간 위젯 (Redis 데이터)
    private OsDiskUsageWidget osDiskUsage;
    private DiskIoThroughputWidget diskIoThroughput;
    private BufferCacheHitWidget bufferCacheHit;
    private BackendFsyncWidget backendFsync;
    private DiskLatencyWidget diskLatency;

    // 1시간 차트 (1분 집계)
    private OsDiskIoChart1h osDiskIoChart1h;
    private BufferCacheChart1h bufferCacheChart1h;

    // 6시간 차트 (5분 집계)
    private IoLatencyChart6h ioLatencyChart6h;

    // 24시간 차트 (30분 집계)
    private DiskUsageChart24h diskUsageChart24h;
    private CheckpointVsBackendChart24h checkpointChart24h;
    private BackendFsyncChart24h backendFsyncChart24h;
    private PhysicalVsCacheChart24h physicalCacheChart24h;
    private ThroughputChart24h throughputChart24h;

    // ========================================
    // Widget DTOs
    // ========================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OsDiskUsageWidget {
        private Double usagePercent;
        private String trend;
        private String status;
        private Long totalGB;
        private Long usedGB;
        private Long availableGB;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiskIoThroughputWidget {
        private Double readMBps;
        private Double writeMBps;
        private Double totalMBps;
        private String readTrend;
        private String writeTrend;
        private Double readChangePct;
        private Double writeChangePct;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BufferCacheHitWidget {
        private Double hitRatio;
        private String status;
        private Long cacheHits;
        private Long physicalReads;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackendFsyncWidget {
        private Double fsyncRate;
        private String status;
        private Long totalFsyncs;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiskLatencyWidget {
        private Double avgReadLatency;
        private Double avgWriteLatency;
        private String status;
        private Double maxLatency;
    }

    // ========================================
    // Chart DTOs
    // ========================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OsDiskIoChart1h {
        private List<String> categories;
        private List<Double> readMBps;
        private List<Double> writeMBps;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BufferCacheChart1h {
        private List<String> categories;
        private List<Double> hitRatio;
        private Double warningThreshold;
        private Double normalThreshold;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IoLatencyChart6h {
        private List<String> categories;
        private List<Double> readLatency;
        private List<Double> writeLatency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiskUsageChart24h {
        private List<String> categories;
        private List<Double> usagePercent;
        private Double warningThreshold;
        private Double dangerThreshold;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckpointVsBackendChart24h {
        private List<String> categories;
        private List<Long> checkpointBuffers;
        private List<Long> cleanBuffers;
        private List<Long> backendBuffers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackendFsyncChart24h {
        private List<String> categories;
        private List<Double> fsyncRate;
        private Double warningThreshold;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PhysicalVsCacheChart24h {
        private List<String> categories;
        private List<Long> physicalReads;
        private List<Long> cacheHits;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThroughputChart24h {
        private List<String> categories;
        private List<Double> readMBps;
        private List<Double> writeMBps;
    }
}

