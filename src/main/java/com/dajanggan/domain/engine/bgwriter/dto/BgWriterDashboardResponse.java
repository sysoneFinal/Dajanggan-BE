package com.dajanggan.domain.engine.bgwriter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * BGWriter 대시보드 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BgWriterDashboardResponse {
    private CleanRate cleanRate;
    private MaxwrittenClean maxwrittenClean;
    private BgwriterVsCheckpoint bgwriterVsCheckpoint;
    private BufferReuseRate bufferReuseRate;
    private BufferFlushRatio bufferFlushRatio;
    private RecentStats recentStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CleanRate {
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
    public static class MaxwrittenClean {
        private List<String> categories;
        private List<Long> data;
        private Double average;  // Long → Double로 변경 (평균값은 소수점 필요)
        private Long total;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BgwriterVsCheckpoint {
        private List<String> categories;
        private List<Long> bgwriter;
        private List<Long> checkpoint;
        private Long bgwriterTotal;
        private Long checkpointTotal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BufferReuseRate {
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
    public static class BufferFlushRatio {
        private List<String> categories;
        private List<Long> backend;
        private List<Long> clean;
        private Long backendTotal;
        private Long cleanTotal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentStats {
        private Double bgwriterActivityRate;
        private Double cleanBufferReuseRate;
        private Long backendFsyncCount;
        private Long checkpointInterruptionCount;
    }
}
