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
        // 실시간 위젯 (5개)
        private OsMemoryUsageWidget osMemoryUsage;
        private SwapUsageWidget swapUsage;
        private SharedBufferHitWidget sharedBufferHit;
        private BufferUsageWidget bufferUsage;
        private TempFileUsageWidget tempFileUsage;
        
        // 1시간 차트 (2개)
        private OsMemoryUsageChart1h osMemoryChart1h;
        private BufferUtilizationChart1h bufferUtilChart1h;
        
        // 6시간 차트 (2개)
        private TempFileChart6h tempFileChart6h;
        private IoWaitTimeChart6h ioWaitTimeChart6h;
        
        // 24시간 차트 (4개)
        private OsMemoryTrendChart24h osMemoryTrend24h;
        private SwapUsageTrendChart24h swapTrend24h;
        private BufferReuseScoreChart24h bufferReuseChart24h;
        private TopTablesByBufferChart24h topTablesChart24h;
    }

    // ========================================
    // 실시간 위젯 (5개)
    // ========================================

    /**
     * 위젯 1: OS Memory Usage (Redis)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OsMemoryUsageWidget {
        private Double usagePercent;        // 메모리 사용률 (%)
        private String trend;               // 추세 (up/down/stable)
        private String status;              // 상태 (normal/warning/danger)
        private Long totalGB;               // 전체 메모리 (GB)
        private Long usedGB;                // 사용 중 메모리 (GB)
        private Long availableGB;           // 가용 메모리 (GB)
        private Long cacheGB;               // 캐시 메모리 (GB)
    }

    /**
     * 위젯 2: Swap Usage (Redis)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SwapUsageWidget {
        private Double swapUsagePercent;    // Swap 사용률 (%)
        private String status;              // 상태 (normal/warning/danger)
        private Long totalSwapGB;           // 전체 Swap (GB)
        private Long usedSwapGB;            // 사용 중 Swap (GB)
        private Long swapInPerSec;          // Swap In/s
        private Long swapOutPerSec;         // Swap Out/s
    }

    /**
     * 위젯 3: Shared Buffer Hit Ratio (PostgreSQL)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SharedBufferHitWidget {
        private Double hitRatio;            // 캐시 히트율 (%)
        private String status;              // 상태 (normal/warning/danger)
        private Long cacheHits;             // 캐시 히트 수
        private Long physicalReads;         // 물리 읽기 수
    }

    /**
     * 위젯 4: Buffer Usage (PostgreSQL)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BufferUsageWidget {
        private Double bufferUsagePercent;  // 버퍼 사용률 (%)
        private Double dirtyRatio;          // Dirty 버퍼 비율 (%)
        private Double pinnedRatio;         // Pinned 버퍼 비율 (%)
        private String status;              // 상태
        private Long usedBuffers;           // 사용 중 버퍼 수
        private Long totalBuffers;          // 전체 버퍼 수
    }

    /**
     * 위젯 5: Temp File Usage (PostgreSQL)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TempFileUsageWidget {
        private Double tempFileRate;        // 임시 파일 생성 빈도 (/초)
        private String status;              // 상태 (work_mem 부족 여부)
        private Long totalTempFiles;        // 전체 임시 파일 수
        private Long totalTempMB;           // 전체 임시 파일 크기 (MB)
        private String message;             // 상태 메시지
    }

    // ========================================
    // 1시간 차트 (2개)
    // ========================================

    /**
     * 차트 1: OS Memory Usage (1시간)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OsMemoryUsageChart1h {
        private List<String> categories;    // 시간 라벨
        private List<Double> usedGB;        // Used 메모리 (GB)
        private List<Double> cacheGB;       // Cache 메모리 (GB)
        private List<Double> bufferGB;      // Buffer 메모리 (GB)
    }

    /**
     * 차트 2: Buffer Utilization (1시간)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BufferUtilizationChart1h {
        private List<String> categories;    // 시간 라벨
        private List<Long> dirtyBuffers;    // Dirty 버퍼 수
        private List<Long> pinnedBuffers;   // Pinned 버퍼 수
    }

    // ========================================
    // 6시간 차트 (2개)
    // ========================================

    /**
     * 차트 4: Temp File Generation (6시간)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TempFileChart6h {
        private List<String> categories;        // 시간 라벨
        private List<Long> tempFileCount;       // 임시 파일 생성 수
        private List<Double> tempFileSizeMB;    // 임시 파일 크기 (MB)
    }

    /**
     * 차트 5: I/O Wait Time (6시간)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IoWaitTimeChart6h {
        private List<String> categories;    // 시간 라벨
        private List<Double> readWaitMs;    // 읽기 대기 시간 (ms)
        private List<Double> writeWaitMs;   // 쓰기 대기 시간 (ms)
    }

    // ========================================
    // 24시간 차트 (4개)
    // ========================================

    /**
     * 차트 6: OS Memory Trend (24시간)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OsMemoryTrendChart24h {
        private List<String> categories;    // 시간 라벨
        private List<Double> usagePercent;  // 메모리 사용률 (%)
        private Double warningThreshold;    // 주의 임계값 (80%)
        private Double dangerThreshold;     // 위험 임계값 (90%)
    }

    /**
     * 차트 7: Swap Usage Trend (24시간)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SwapUsageTrendChart24h {
        private List<String> categories;    // 시간 라벨
        private List<Double> swapUsagePercent;  // Swap 사용률 (%)
        private List<Long> swapInRate;      // Swap In 빈도
        private List<Long> swapOutRate;     // Swap Out 빈도
    }

    /**
     * 차트 8: Buffer Reuse Score (24시간)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BufferReuseScoreChart24h {
        private List<String> categories;        // 시간 라벨
        private List<Double> reuseScore;        // 버퍼 재사용 점수 (0~100)
        private List<Double> avgUsagecount;     // 평균 usagecount
    }

    /**
     * 차트 9: Top Tables by Buffer Usage (24시간)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopTablesByBufferChart24h {
        private List<String> tableNames;    // 테이블명 (Top 10)
        private List<Long> bufferCounts;    // 버퍼 사용량
        private List<Double> usagePercent;  // 버퍼 점유율 (%)
    }

    // ========================================
    // SSE 실시간 데이터
    // ========================================

    /**
     * SSE 실시간 위젯 데이터
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RealtimeMetrics {
        private OsMemoryUsageWidget osMemoryUsage;
        private SwapUsageWidget swapUsage;
        private SharedBufferHitWidget sharedBufferHit;
        private BufferUsageWidget bufferUsage;
        private TempFileUsageWidget tempFileUsage;
    }

    // ========================================
    // 리스트 (2개 섹션)
    // ========================================

    /**
     * Memory 리스트 응답
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListResponse {
        private List<LowCacheHitItem> lowCacheHitList;             // 낮은 캐시 히트율 테이블
        private Long totalCount;
    }

    /**
     * 낮은 캐시 히트율 테이블 Top 20
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LowCacheHitItem {
        private Long rankNum;               // 순위
        private String tableName;           // 테이블명
        private String databaseName;        // 데이터베이스명
        private Double cacheHitRatio;       // 캐시 히트율 (%)
        private Long physicalReads;         // 물리 읽기 수
        private Long cacheHits;             // 캐시 히트 수
        private String status;              // 상태
    }

    /**
     * 공통 Series (차트용)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Series {
        private String name;
        private List<Long> data;
    }
}