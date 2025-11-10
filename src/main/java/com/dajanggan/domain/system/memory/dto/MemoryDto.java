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
        private FsyncRate fsyncRate;
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
        private Double value;           // Memory 사용률 (%)
        private Long usedBuffers;       // 사용 중인 버퍼 수
        private Long totalBuffers;      // 전체 버퍼 수
    }

    /**
     * Buffer Hit 비율
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BufferHitRatio {
        private Double value;           // Hit 비율 (%)
        private Long hitCount;          // Hit 횟수
        private Long totalCount;        // 전체 접근 횟수
    }

    /**
     * Shared Buffer 사용량
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SharedBufferUsage {
        private Double value;           // 사용률 (%)
        private Long activeBuffers;     // 활성 버퍼 수
        private Long totalBuffers;      // 전체 버퍼 수
    }

    /**
     * Eviction Rate (시계열)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvictionRate {
        private List<String> categories;  // 시간 카테고리
        private List<Long> data;          // 각 시간대별 eviction 횟수
        private Double average;           // 평균
        private Long max;                 // 최대값
        private Long min;                 // 최소값
    }

    /**
     * Fsync Rate (시계열)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FsyncRate {
        private List<String> categories;  // 시간 카테고리
        private List<Long> data;          // 각 시간대별 fsync 횟수
        private Double average;           // 평균
        private Long max;                 // 최대값
        private Long backendFsync;        // Backend Fsync 횟수
    }

    /**
     * Dirty Buffer 추세
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DirtyBufferTrend {
        private List<String> categories;  // 시간 카테고리
        private List<Long> data;          // 각 시간대별 dirty buffer 수
        private Double average;           // 평균
        private Long max;                 // 최대값
        private Long min;                 // 최소값
    }

    /**
     * Eviction vs Flush 비교
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvictionFlushRatio {
        private List<String> categories;  // 시간 카테고리
        private List<Long> evictions;     // Eviction 횟수
        private List<Long> fsyncs;        // Fsync 횟수
    }

    /**
     * 상위 버퍼 사용 객체
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopBufferObjects {
        private List<String> labels;      // 객체명
        private List<Long> data;          // 버퍼 사용량
        private List<String> types;       // 타입 (table/index)
    }

    /**
     * 요약 통계
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryStats {
        private Double dirtyBufferRatio;      // Dirty Buffer 비율 (%)
        private Double backendWaitTime;       // Backend 대기 시간 (ms)
        private Double workMemUsage;          // Work Memory 사용량 (MB)
        private Double tempFileUsage;         // Temp File 사용량 (MB)
        private Double checkpointInterval;    // Checkpoint 간격 (초)
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
        private String objectName;          // 객체명
        private String type;                // 타입 (table/index)
        private Double sizeMB;              // 크기 (MB)
        private Long bufferCount;           // 버퍼 개수
        private Double usagePercent;        // 점유율 (%)
        private Long dirtyCount;            // Dirty 버퍼 개수
        private Double dirtyPercent;        // Dirty 비율 (%)
        private Long pinnedBuffers;         // 고정 버퍼
        private Double hitPercent;          // Hit 비율 (%)
        private Long accessCount;           // 접근 횟수
        private Long evictionCount;         // Eviction 횟수
        private Double avgAccessTime;       // 평균 접근 시간 (ms)
        private String status;              // 상태 (정상, 주의, 위험)
    }
}