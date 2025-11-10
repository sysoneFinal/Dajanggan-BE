package com.dajanggan.domain.engine.bgwriter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class BgWriterDto {

    /**
     * BGWriter 대시보드 전체 응답
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardResponse {
        private BackendFlushRatio backendFlushRatio;
        private CleanRate cleanRate;
        private BufferFlushRatio bufferFlushRatio;
        private MaxwrittenClean maxwrittenClean;
        private BgwriterVsCheckpoint bgwriterVsCheckpoint;
        private BufferReuseRate bufferReuseRate;
        private RecentStats recentStats;
    }

    /**
     * Backend Flush 비율
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackendFlushRatio {
        private Double value;           // Backend Flush 비율 (%)
        private Long buffersClean;      // BGWriter가 쓴 버퍼 수
        private Long buffersBackend;    // Backend가 직접 쓴 버퍼 수
    }

    /**
     * Clean Rate (시간대별 BGWriter 활동)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CleanRate {
        private List<String> categories;  // 시간 카테고리 (예: "0:00", "2:00", ...)
        private List<Double> data;        // 각 시간대별 clean rate
        private Double average;           // 평균
        private Double max;               // 최대값
        private Double min;               // 최소값
    }

    /**
     * Buffer Flush 비율 비교 (Backend vs Clean)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BufferFlushRatio {
        private List<String> categories;  // 시간 카테고리
        private List<Long> backend;       // Backend가 쓴 버퍼
        private List<Long> clean;         // BGWriter가 쓴 버퍼
        private Long backendTotal;        // Backend 총합
        private Long cleanTotal;          // BGWriter 총합
    }

    /**
     * Maxwritten Clean 발생 횟수
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MaxwrittenClean {
        private List<String> categories;  // 시간 카테고리
        private List<Long> data;          // 각 시간대별 maxwritten_clean 발생 횟수
        private Long average;             // 평균
        private Long total;               // 총 발생 횟수
    }

    /**
     * BGWriter vs Checkpoint 비교
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BgwriterVsCheckpoint {
        private List<String> categories;  // 시간 카테고리
        private List<Long> bgwriter;      // BGWriter가 쓴 버퍼
        private List<Long> checkpoint;    // Checkpoint가 쓴 버퍼
        private Long bgwriterTotal;       // BGWriter 총합
        private Long checkpointTotal;     // Checkpoint 총합
    }

    /**
     * Buffer 재사용률
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BufferReuseRate {
        private List<String> categories;  // 시간 카테고리
        private List<Double> data;        // 각 시간대별 재사용률 (%)
        private Double average;           // 평균
        private Double max;               // 최대값
        private Double min;               // 최소값
    }

    /**
     * 최근 통계 (Summary Cards)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentStats {
        private Double bgwriterActivityRate;          // BGWriter 활동률 (%)
        private Double cleanBufferReuseRate;          // Clean Buffer 재사용률 (%)
        private Long backendFsyncCount;               // Backend Fsync 발생 횟수
        private Double bufferPoolUsageRate;           // Buffer Pool 사용률 (%)
        private Long checkpointInterruptionCount;     // Checkpoint에 의한 BGWriter 중단 횟수
        private Double dirtyBufferAccumulationRate;   // Dirty Buffer 누적률 (%)
    }

    /**
     * BGWriter 리스트 응답
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
     * BGWriter 리스트 아이템
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListItem {
        private String id;
        private String timestamp;           // 시간
        private Long buffersAlloc;          // 할당 버퍼(개)
        private Double cleanRate;           // Clean(개/s)
        private Double backendRate;         // Backend(개/s)
        private Long checkpointBuffers;     // Checkpoint(개)
        private Double backendRatio;        // Backend 비율(%)
        private Double fsyncRate;           // Fsync(회/s)
        private Double maxWrittenRate;      // 상한 도달(회/분)
        private Double avgCycleTime;        // 평균 사이클(ms)
        private String status;              // 상태 (정상, 주의, 위험)
    }
}
