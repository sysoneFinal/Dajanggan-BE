package com.dajanggan.domain.engine.checkpoint.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class CheckpointDto {

    /**
     * Checkpoint 대시보드 전체 응답
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardResponse {
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
     * 요청형 체크포인트 비율
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestRatio {
        private Double value;           // 요청형 비율 (%)
        private Long requestedCount;    // 요청형 횟수
        private Long timedCount;        // 정기형 횟수
    }

    /**
     * 평균 쓰기 시간
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvgWriteTime {
        private List<String> categories;  // 시간 카테고리
        private List<Double> data;        // 평균 쓰기 시간
        private Double average;           // 평균
        private Double max;               // 최대값
        private Double min;               // 최소값
    }

    /**
     * Checkpoint 발생 횟수 (Requested vs Timed)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Occurrence {
        private List<String> categories;  // 시간 카테고리
        private List<Long> requested;     // 요청형 발생 횟수
        private List<Long> timed;         // 정기형 발생 횟수
        private Long requestedTotal;      // 요청형 총합
        private Long timedTotal;          // 정기형 총합
        private Double ratio;             // 요청형 비율 (%)
    }

    /**
     * WAL 생성량
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WalGeneration {
        private List<String> categories;  // 시간 카테고리
        private List<Long> data;          // WAL 생성량 (bytes)
        private Long total;               // 총 WAL 생성량
        private Double average;           // 평균
        private Long max;                 // 최대값
    }

    /**
     * 처리 시간 (Sync + Write)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessTime {
        private List<String> categories;  // 시간 카테고리
        private List<Double> syncTime;    // Sync 시간
        private List<Double> writeTime;   // Write 시간
        private Double avgSync;           // Sync 평균
        private Double avgWrite;          // Write 평균
        private Double avgTotal;          // 총 평균
    }

    /**
     * Buffer 처리량
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Buffer {
        private List<String> categories;  // 시간 카테고리
        private List<Double> data;        // 버퍼 처리량 (buffers/sec)
        private Double average;           // 평균
        private Double max;               // 최대값
        private Double min;               // 최소값
    }

    /**
     * Checkpoint 간격
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckpointInterval {
        private List<String> categories;  // 시간 카테고리
        private List<Double> data;        // 간격 (분)
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
        private Long buffersWritten;          // 총 버퍼 쓰기 횟수
        private Double avgTotalProcessTime;   // 평균 총 처리 시간 (초)
        private Double checkpointDistance;    // Checkpoint 거리 (MB)
        private Double checkpointInterval;    // Checkpoint 간격 (분)
        private Double avgWalGenerationSpeed; // 평균 WAL 생성 속도 (MB/s)
    }

    /**
     * Checkpoint 리스트 응답
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
     * Checkpoint 리스트 아이템
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListItem {
        private String id;
        private String timestamp;           // 시간
        private String type;                // 유형 (timed, requested)
        private Double writeTime;           // Write 시간(초)
        private Double syncTime;            // Sync 시간(초)
        private Double totalTime;           // 총 시간(초)
        private String walGenerated;        // WAL 생성량
        private Long walFilesAdded;         // WAL 파일 추가
        private Long walFilesRemoved;       // WAL 파일 제거
        private String checkpointDistance;  // Checkpoint 간격
        private Long buffersWritten;        // 버퍼 쓰기(개)
        private Long buffersBackend;        // Backend(개)
        private Double avgBuffersPerSec;    // 평균(개/초)
        private String status;              // 상태 (정상, 주의, 위험)
    }
}
