package com.dajanggan.domain.system.disk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class DiskIoDto {

    /**
     * Disk I/O 대시보드 전체 응답
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardResponse {
        private DiskUsage diskUsage;
        private ProcessIO processIO;
        private QueueDepth queueDepth;
        private IoLatency ioLatency;
        private Throughput throughput;
        private Evictions evictions;
        private WalBytes walBytes;
        private RecentStats recentStats;
    }

    /**
     * 디스크 사용률
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiskUsage {
        private Double value;           // 디스크 사용률 (%)
        private Long iopsRead;          // 읽기 IOPS
        private Long iopsWrite;         // 쓰기 IOPS
    }

    /**
     * 프로세스별 I/O
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessIO {
        private List<String> categories;  // 시간 카테고리
        private List<Series> series;      // 각 프로세스별 데이터
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Series {
        private String name;            // 프로세스 이름
        private List<Long> data;        // I/O 데이터
    }

    /**
     * Queue Depth
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueueDepth {
        private List<String> categories;  // 시간 카테고리
        private List<Double> queueLength; // 큐 길이
        private Double average;           // 평균
    }

    /**
     * I/O Latency
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IoLatency {
        private List<String> categories;  // 시간 카테고리
        private List<Double> readLatency; // 읽기 지연시간
        private List<Double> writeLatency;// 쓰기 지연시간
        private Double avgRead;           // 평균 읽기 지연시간
        private Double avgWrite;          // 평균 쓰기 지연시간
    }

    /**
     * Throughput
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Throughput {
        private List<String> categories;  // 시간 카테고리
        private List<Long> iops;          // IOPS
        private List<Double> throughputMB;// 처리량 (MB/s)
    }

    /**
     * Evictions
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Evictions {
        private List<String> categories;  // 시간 카테고리
        private List<Long> evictionRate;  // Eviction 발생률
        private Double average;           // 평균
    }

    /**
     * WAL Bytes
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WalBytes {
        private List<String> categories;  // 시간 카테고리
        private List<Long> walBytes;      // WAL 바이트
        private Double average;           // 평균
    }

    /**
     * 최근 통계 (Summary Cards)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentStats {
        private Double diskQueueLength;         // 디스크 대기열 길이
        private Double iopsSaturation;          // IOPS 포화도 (%)
        private Double avgLatency;              // 평균 응답시간 (ms)
        private Double walBottleneck;           // WAL 병목 여부 (%)
        private Double bufferEvictionRate;      // 버퍼 교체 빈도 (/sec)
    }

    /**
     * Disk I/O 리스트 응답
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
     * Disk I/O 리스트 아이템
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListItem {
        private String id;
        private String processType;         // 프로세스 타입
        private Long totalIO;               // 전체 I/O (개/s)
        private Long readRate;              // 읽기 (개/s)
        private Long writeRate;             // 쓰기 (개/s)
        private Double readMBs;             // 읽기 (MB/s)
        private Double writeMBs;            // 쓰기 (MB/s)
        private Double throughputMBs;       // 처리량 (MB/s)
        private Long fsyncRate;             // Fsync (회/s)
        private Long evictionRate;          // Eviction (개/s)
        private Long extendRate;            // Extend (회/s)
        private Double hitRatio;            // Hit Ratio (%)
        private Double avgQueueDepth;       // 평균 큐 깊이
        private Double avgLatency;          // 평균 지연 (ms)
        private Double readPercent;         // 읽기 (%)
        private Double writePercent;        // 쓰기 (%)
        private String status;              // 상태 (정상, 주의, 위험)
    }
}