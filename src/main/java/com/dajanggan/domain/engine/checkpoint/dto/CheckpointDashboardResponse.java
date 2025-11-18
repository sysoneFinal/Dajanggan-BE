package com.dajanggan.domain.engine.checkpoint.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Checkpoint 대시보드 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckpointDashboardResponse {
    private RequestRatio requestRatio;
    private AvgWriteTime avgWriteTime;
    private Occurrence occurrence;
    private WalGeneration walGeneration;
    private ProcessTime processTime;
    private Buffer buffer;
    private CheckpointInterval checkpointInterval;
    private RecentStats recentStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestRatio {
        private Double value;
        private Long requestedCount;
        private Long timedCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvgWriteTime {
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
    public static class Occurrence {
        private List<String> categories;
        private List<Long> requested;
        private List<Long> timed;
        private Long requestedTotal;
        private Long timedTotal;
        private Double ratio;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WalGeneration {
        private List<String> categories;
        private List<Long> data;
        private Long total;
        private Double average;
        private Long max;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessTime {
        private List<String> categories;
        private List<Double> syncTime;
        private List<Double> writeTime;
        private Double avgSync;
        private Double avgWrite;
        private Double avgTotal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Buffer {
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
    public static class CheckpointInterval {
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
        private Long buffersWritten;
        private Double avgTotalProcessTime;
        private Double checkpointDistance;
        private Double checkpointInterval;
        private Double avgWalGenerationSpeed;
    }
}

