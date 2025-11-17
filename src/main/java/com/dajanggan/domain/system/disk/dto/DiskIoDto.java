package com.dajanggan.domain.system.disk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Disk I/O 모니터링 DTO
 * 
 * 데이터 소스:
 * 1. PostgreSQL 메트릭 (disk_io_raw, disk_io_agg) - 1분마다
 * 2. OS 메트릭 (Redis, os_metric_agg) - 5초마다
 */
public class DiskIoDto {

    // ========================================
    // 대시보드 전체 응답
    // ========================================

    /**
     * Disk I/O 대시보드 전체 응답
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardResponse {
        // 실시간 위젯 (Redis 데이터)
        private OsDiskUsageWidget osDiskUsage;              // 위젯 1: OS Disk 사용률
        private DiskIoThroughputWidget diskIoThroughput;    // 위젯 2: Disk I/O 처리량
        private BufferCacheHitWidget bufferCacheHit;        // 위젯 3: Buffer Cache Hit Ratio
        private BackendFsyncWidget backendFsync;            // 위젯 4: Backend Fsync Rate
        private DiskLatencyWidget diskLatency;              // 위젯 5: Disk Latency

        // 1시간 차트 (1분 집계)
        private OsDiskIoChart1h osDiskIoChart1h;            // 차트 1: OS Disk I/O 추이 (1시간)
        private BufferCacheChart1h bufferCacheChart1h;      // 차트 2: Buffer Cache Hit Ratio 추이 (1시간)

        // 6시간 차트 (5분 집계)
        private IoLatencyChart6h ioLatencyChart6h;          // 차트 3: I/O Latency 추이 (6시간)

        // 24시간 차트 (30분 집계)
        private DiskUsageChart24h diskUsageChart24h;        // 차트 4: Disk 사용률 추이 (24시간)
        private CheckpointVsBackendChart24h checkpointChart24h; // 차트 5: Checkpoint vs Backend Write
        private BackendFsyncChart24h backendFsyncChart24h;  // 차트 6: Backend Fsync Rate 추이
        private PhysicalVsCacheChart24h physicalCacheChart24h; // 차트 7: Physical vs Cache Read
        private ThroughputChart24h throughputChart24h;      // 차트 8: Disk I/O Throughput (24시간)
    }

    // ========================================
    // 실시간 위젯 (Redis 데이터 사용)
    // ========================================

    /**
     * 위젯 1: OS Disk 사용률
     * 데이터: Redis 실시간 (5초)
     * 표시: 현재 사용률 (%), 추세, 상태
     * 경고: >80% 주의, >90% 위험
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OsDiskUsageWidget {
        private Double usagePercent;        // 현재 사용률 (%)
        private String trend;               // 추세: "up", "down", "stable"
        private String status;              // 상태: "normal", "warning", "danger"
        private Long totalGB;               // 전체 용량 (GB)
        private Long usedGB;                // 사용 중 (GB)
        private Long availableGB;           // 사용 가능 (GB)
    }

    /**
     * 위젯 2: Disk I/O 처리량 (Throughput)
     * 데이터: Redis 실시간 (5초)
     * 표시: 읽기/쓰기 MB/s
     * 추세: 1분 전 대비 증감
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiskIoThroughputWidget {
        private Double readMBps;            // 읽기 처리량 (MB/s)
        private Double writeMBps;           // 쓰기 처리량 (MB/s)
        private Double totalMBps;           // 전체 처리량 (MB/s)
        private String readTrend;           // 읽기 추세
        private String writeTrend;          // 쓰기 추세
        private Double readChangePct;       // 1분 전 대비 읽기 변화율 (%)
        private Double writeChangePct;      // 1분 전 대비 쓰기 변화율 (%)
    }

    /**
     * 위젯 3: Buffer Cache Hit Ratio
     * 데이터: disk_io_agg (최근 1분)
     * 표시: 캐시 히트율 (%)
     * 상태: >95% 정상, 85-95% 주의, <85% 위험
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BufferCacheHitWidget {
        private Double hitRatio;            // 캐시 히트율 (%)
        private String status;              // 상태: "normal", "warning", "danger"
        private Long cacheHits;             // 캐시 히트 수
        private Long physicalReads;         // 물리적 읽기 수
    }

    /**
     * 위젯 4: Backend Fsync Rate
     * 데이터: disk_io_agg (최근 1분)
     * 표시: 초당 Backend fsync 수
     * 경고: >100/s 주의 (병목 징후)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackendFsyncWidget {
        private Double fsyncRate;           // 초당 fsync 수
        private String status;              // 상태: "normal", "warning", "danger"
        private Long totalFsyncs;           // 총 fsync 수
        private String message;             // 상태 메시지
    }

    /**
     * 위젯 5: Disk Latency
     * 데이터: disk_io_agg (최근 1분)
     * 표시: 평균 읽기/쓰기 레이턴시 (ms)
     * 경고: >10ms 주의, >50ms 위험
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiskLatencyWidget {
        private Double avgReadLatency;      // 평균 읽기 레이턴시 (ms)
        private Double avgWriteLatency;     // 평균 쓰기 레이턴시 (ms)
        private String status;              // 상태: "normal", "warning", "danger"
        private Double maxLatency;          // 최대 레이턴시
    }

    // ========================================
    // 1시간 차트 (1분 집계)
    // ========================================

    /**
     * 차트 1: OS Disk I/O 추이 (1시간)
     * 데이터: os_metric_agg (1분) - DISK_READ, DISK_WRITE
     * Y축: MB/s
     * 라인: 읽기 / 쓰기
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OsDiskIoChart1h {
        private List<String> categories;    // 시간 라벨 (HH:mm)
        private List<Double> readMBps;      // 읽기 MB/s
        private List<Double> writeMBps;     // 쓰기 MB/s
    }

    /**
     * 차트 2: Buffer Cache Hit Ratio 추이 (1시간)
     * 데이터: disk_io_agg (1분)
     * Y축: 히트율 (%)
     * 경고선: 85%, 95%
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BufferCacheChart1h {
        private List<String> categories;    // 시간 라벨
        private List<Double> hitRatio;      // 히트율 (%)
        private Double warningThreshold;    // 경고 임계값 (85%)
        private Double normalThreshold;     // 정상 임계값 (95%)
    }

    // ========================================
    // 6시간 차트 (5분 집계)
    // ========================================

    /**
     * 차트 3: I/O Latency 추이 (6시간)
     * 데이터: disk_io_agg_5m (5분 집계)
     * Y축: 레이턴시 (ms)
     * 라인: 읽기 / 쓰기 레이턴시
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IoLatencyChart6h {
        private List<String> categories;    // 시간 라벨
        private List<Double> readLatency;   // 읽기 레이턴시
        private List<Double> writeLatency;  // 쓰기 레이턴시
    }

    // ========================================
    // 24시간 차트 (30분 집계)
    // ========================================

    /**
     * 차트 4: Disk 사용률 추이 (24시간)
     * 테이블: os_metric_agg (metricType='DISK_USAGE')
     * Y축: 사용률 (%)
     * 경고선: 80%, 90%
     * 집계: 30분
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiskUsageChart24h {
        private List<String> categories;    // 시간 라벨
        private List<Double> usagePercent;  // 사용률 (%)
        private Double warningThreshold;    // 경고 임계값 (80%)
        private Double dangerThreshold;     // 위험 임계값 (90%)
    }

    /**
     * 차트 5: Checkpoint vs Backend Write (24시간)
     * 테이블: disk_io_agg_30m
     * Y축: 버퍼 쓰기 수
     * 스택 바: Checkpoint / Clean / Backend
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckpointVsBackendChart24h {
        private List<String> categories;        // 시간 라벨
        private List<Long> checkpointBuffers;   // Checkpoint 버퍼
        private List<Long> cleanBuffers;        // Clean 버퍼
        private List<Long> backendBuffers;      // Backend 버퍼
    }

    /**
     * 차트 6: Backend Fsync Rate 추이 (24시간)
     * 테이블: disk_io_agg_30m
     * Y축: 초당 fsync 수
     * 경고선: 100
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackendFsyncChart24h {
        private List<String> categories;    // 시간 라벨
        private List<Double> fsyncRate;     // 초당 fsync 수
        private Double warningThreshold;    // 경고 임계값 (100)
    }

    /**
     * 차트 7: Physical vs Cache Read (24시간)
     * 테이블: disk_io_agg_30m
     * Y축: 블록 수
     * 스택 영역: Physical Read / Cache Hit
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PhysicalVsCacheChart24h {
        private List<String> categories;    // 시간 라벨
        private List<Long> physicalReads;   // 물리적 읽기
        private List<Long> cacheHits;       // 캐시 히트
    }

    /**
     * 차트 8: Disk I/O Throughput (24시간)
     * 테이블: os_metric_agg (metricType='DISK_READ', 'DISK_WRITE')
     * Y축: MB/s
     * 라인: 읽기 / 쓰기
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThroughputChart24h {
        private List<String> categories;    // 시간 라벨
        private List<Double> readMBps;      // 읽기 MB/s
        private List<Double> writeMBps;     // 쓰기 MB/s
    }

    // ========================================
    // 리스트 페이지
    // ========================================

    /**
     * 리스트 응답
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListResponse {
        private List<HighFsyncItem> highFsyncList;      // 섹션 1: 높은 Fsync 발생 시간대
        private List<LowCacheHitItem> lowCacheHitList;  // 섹션 2: 낮은 Cache Hit Ratio 시간대
        private Long totalCount;
    }

    /**
     * 섹션 1: 높은 Fsync 발생 시간대 (Top 20)
     * 데이터: disk_io_agg (1분)
     * 정렬: Backend Fsync Rate 내림차순
     * 컬럼: 시간, Fsync Rate, Buffer Hit%, Latency, 상태
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HighFsyncItem {
        private OffsetDateTime collectedAt;     // 시간
        private Double fsyncRate;               // Fsync Rate (/s)
        private Double bufferHitRatio;          // Buffer Hit (%)
        private Double avgLatency;              // Latency (ms)
        private String status;                  // 상태 (정상, 주의, 위험)
        private String backendType;             // Backend 타입
    }

    /**
     * 섹션 2: 낮은 Cache Hit Ratio 시간대 (Top 20)
     * 데이터: disk_io_agg (1분)
     * 정렬: Buffer Hit Ratio 오름차순
     * 컬럼: 시간, Hit%, Physical Read, Cache Hit, 상태
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LowCacheHitItem {
        private OffsetDateTime collectedAt;     // 시간
        private Double bufferHitRatio;          // Hit (%)
        private Long physicalReads;             // Physical Read
        private Long cacheHits;                 // Cache Hit
        private String status;                  // 상태
        private String backendType;             // Backend 타입
        private String databaseName;            // Database 이름
    }

    // ========================================
    // 공통 DTO
    // ========================================

    /**
     * 시계열 데이터 포인트
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSeriesPoint {
        private String timestamp;
        private Double value;
    }

    /**
     * 차트 시리즈
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartSeries {
        private String name;
        private List<Double> data;
    }
}
