package com.dajanggan.domain.engine.checkpoint.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * GET /api/engine/checkpoint/dashboard
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckpointDashboardDto {

    private DataWrapper data;
    private OffsetDateTime timestamp;
    private Boolean success;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataWrapper {
        private Long instance;
        private RequestRatio requestRatio;
        private AvgWriteTime avgWriteTime;
        private Occurrence occurrence;
        private WalGeneration walGeneration;
        private ProcessTime processTime;
        private Buffer buffer;
        private CheckpointInterval checkpointInterval;
        private RecentStats recentStats;
    }

    /**
     * Checkpoint 요청 비율 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestRatio {
        private Double value;              // 요청 비율 (%)
        private Long requestedCount;       // 요청 기반 횟수
        private Long timedCount;           // 시간 기반 횟수
    }

    /**
     * 평균 블록 쓰기 시간 추이 (24시간)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvgWriteTime {
        private List<String> categories;   // 시간 라벨
        private List<Double> data;         // 각 시간대별 평균 쓰기 시간 (초)
        private Double average;            // 전체 평균
        private Double max;                // 최대값
        private Double min;                // 최소값
    }

    /**
     * Checkpoint 발생 추이 (24시간)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Occurrence {
        private List<String> categories;   // 시간 라벨
        private List<Long> requested;      // 요청 기반 발생 횟수
        private List<Long> timed;          // 시간 기반 발생 횟수
        private Long requestedTotal;       // 총 요청 기반 발생 횟수
        private Long timedTotal;           // 총 시간 기반 발생 횟수
        private Double ratio;              // 요청 기반 비율 (%)
    }

    /**
     * WAL 생성량 추이 (24시간)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WalGeneration {
        private List<String> categories;   // 시간 라벨
        private List<Long> data;           // 각 시간대별 WAL 생성량 (bytes)
        private Long total;                // 총 생성량
        private Long average;              // 평균 생성량
        private Long max;                  // 최대 생성량
    }

    /**
     * Checkpoint 처리 시간 추이 (24시간)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessTime {
        private List<String> categories;   // 시간 라벨
        private List<Long> syncTime;       // Sync 시간 배열 (ms)
        private List<Long> writeTime;      // Write 시간 배열 (ms)
        private Long avgSync;              // 평균 Sync 시간
        private Long avgWrite;             // 평균 Write 시간
        private Long avgTotal;             // 평균 총 시간
    }

    /**
     * Checkpoint Buffer 처리량 (24시간)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Buffer {
        private List<String> categories;   // 시간 라벨
        private List<Long> data;           // 각 시간대별 버퍼 처리량 (개/초)
        private Long average;              // 평균 처리량
        private Long max;                  // 최대 처리량
        private Long min;                  // 최소 처리량
    }

    /**
     * Checkpoint 간격 추이 (24시간)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckpointInterval {
        private List<String> categories;   // 시간 라벨
        private List<Double> data;         // 각 시간대별 간격 (분)
        private Double average;            // 평균 간격
        private Double max;                // 최대 간격
        private Double min;                // 최소 간격
    }

    /**
     * 최근 5분 평균 통계 (요약 카드용)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentStats {
        private MetricWithDiff buffersWritten;           // 기록된 버퍼
        private MetricWithDiff avgTotalProcessTime;      // 평균 처리 시간 (초)
        private MetricWithDiff checkpointDistance;       // 체크포인트 거리 (%)
        private MetricWithDiff checkpointInterval;       // Checkpoint 간격 (분)
        private MetricWithDiff avgWalGenerationSpeed;    // 평균 WAL 생성 속도 (GB/h)
    }

    /**
     * 현재 값과 변화량
     * TODO: 추후 고도화 시 사용 (변화량 추가)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricWithDiff {
        private Double current;            // 현재 값
        private Double diff;               // 변화량
    }
}
