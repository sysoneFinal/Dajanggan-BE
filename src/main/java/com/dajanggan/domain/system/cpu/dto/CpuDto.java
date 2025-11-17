package com.dajanggan.domain.system.cpu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * CPU 페이지 DTO (2025-11-16 개편)
 * 
 * 데이터 소스:
 * - PostgreSQL Metrics: cpu_agg (1분 집계), cpu_agg_5m (5분 집계)
 * - OS Metrics: Redis 실시간 (5초마다), os_metric_agg (1분 집계)
 * 
 * 프론트엔드 구조:
 * - CPU 게이지 (실시간 SSE)
 * - 차트 6개 (PostgreSQL + OS 통합)
 * - 리스트 페이지
 */
public class CpuDto {

    // ========================================
    // 대시보드 응답 (PDF 명세 기반)
    // ========================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardResponse {
        private Widgets widgets;
        private Charts charts;
    }

    /**
     * 위젯 5개
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Widgets {
        private OsCpuUsageWidget osCpuUsage;           // 위젯1
        private PostgresqlTpsWidget postgresqlTps;     // 위젯2
        private ErrorRateWidget errorRate;             // 위젯3
        private BackendProcessesWidget backendProcesses; // 위젯4
        private LoadAverageWidget loadAverage;         // 위젯5
    }

    /**
     * 차트 9개
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Charts {
        private OsCpuUsageTrend1h osCpuUsageTrend1h;                     // 차트1
        private PostgresqlTpsTrend1h postgresqlTpsTrend1h;               // 차트2
        private OsCpuVsActiveConnections24h osCpuVsActiveConnections24h; // 차트3
        private LoadAverageTrend24h loadAverageTrend24h;                 // 차트4
        private ConnectionStatus24h connectionStatus24h;                 // 차트5
        private TpsDailyTrend24h tpsDailyTrend24h;                       // 차트6
        private WaitEventDistribution24h waitEventDistribution24h;       // 차트7
        private BackendTypeTrend24h backendTypeTrend24h;                 // 차트8
        private ErrorRateTrend24h errorRateTrend24h;                     // 차트9
    }

    // ========================================
    // Widget & Chart DTOs
    // ========================================

    /**
     * CPU 게이지 위젯 (프론트엔드에서 설명 참고용)
     * 실시간 값은 SSE로 별도 전송
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CpuUsageWidget {
        private Double value;
        private String description;
        private Integer runningQueries;
        private Integer waitingQueries;
        private Integer idleConnections;
    }

    /**
     * CPU 사용률 1분 추이
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CpuUsageTrend {
        private List<String> categories;
        private List<Double> data;
    }

    /**
     * CPU 부하 유형별 분석
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CpuLoadTypes {
        private List<String> categories;
        private List<Double> postgresqlBackend;
        private List<Double> bgWriter;
        private List<Double> autoVacuum;
        private List<Double> checkpoint;
    }

    /**
     * I/O Wait vs Disk Latency 상관관계
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IoWaitVsLatency {
        private List<Point> normal;
        private List<Point> warning;
        private List<Point> danger;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Point {
        private Double x;
        private Double y;
    }

    /**
     * Backend 프로세스 타입별 분포
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackendProcessStats {
        private List<String> types;
        private List<Integer> activeCount;
        private List<Integer> idleCount;
        private List<Integer> totalCount;
        private List<String> colors;
    }

    /**
     * 대기 유형별 비중 변화
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaitEventDistribution {
        private List<String> categories;
        private List<Double> cpu;
        private List<Double> client;
        private List<Double> io;
        private List<Double> lock;
        private List<Double> other;
    }

    /**
     * 최근 통계 (사이드바)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentStats {
        private LoadAverage loadAverage;
        private Double ioWait;
        private Connections connections;
        private Double idleCpu;
        private Long contextSwitches;
        private Double postgresqlBackendCpu;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoadAverage {
        private Double one;
        private Double five;
        private Double fifteen;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Connections {
        private Integer active;
        private Integer idle;
        private Integer total;
    }

    // ========================================
    // 리스트 페이지
    // ========================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListResponse {
        private List<CpuListItem> data;
        private Integer total;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListRequest {
        private String timeRange;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CpuListItem {
        private String id;
        private String time;
        private Double totalCPU;
        private Double userCPU;
        private Double systemCPU;
        private Double idleCPU;
        private Double ioWait;
        private Double stealCPU;
        private Double loadAvg1;
        private Double loadAvg5;
        private Double loadAvg15;
        private Integer activeSessions;
        private Integer parallelWorkers;
        private Integer waitingSessions;
        private Double workerTime;
        private Long contextSwitches;
        private String status;
    }

    // ========================================
    // 위젯 DTO (PDF 명세)
    // ========================================

    /**
     * 위젯1: OS CPU 사용률
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OsCpuUsageWidget {
        private Double current;
        private Double trend;
        private String status; // "정상" | "주의" | "위험"
    }

    /**
     * 위젯2: PostgreSQL TPS
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostgresqlTpsWidget {
        private Integer current;
        private Integer trend;
        private String status;
    }

    /**
     * 위젯3: 에러율
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorRateWidget {
        private Integer rollbackTps;
        private Double errorRate;
        private String status;
    }

    /**
     * 위젯4: Backend 프로세스
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackendProcessesWidget {
        private Integer clientBackend;
        private Integer autovacuum;
        private Integer parallelWorker;
    }

    /**
     * 위젯5: Load Average
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoadAverageWidget {
        private Double load1m;
        private Double load5m;
        private Double load15m;
        private Integer cpuCoreCount;
    }

    // ========================================
    // 차트 DTO (PDF 명세)
    // ========================================

    /**
     * 차트1: OS CPU 사용률 추이 (1시간)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OsCpuUsageTrend1h {
        private List<String> categories;
        private List<Double> data;
    }

    /**
     * 차트2: PostgreSQL TPS 추이 (1시간)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostgresqlTpsTrend1h {
        private List<String> categories;
        private List<Integer> commitTps;
        private List<Integer> rollbackTps;
    }

    /**
     * 차트3: OS CPU vs PostgreSQL 활성 연결 (24시간)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OsCpuVsActiveConnections24h {
        private List<String> categories;
        private List<Double> osCpuUsage;
        private List<Integer> activeConnections;
    }

    /**
     * 차트4: Load Average 추이 (24시간)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoadAverageTrend24h {
        private List<String> categories;
        private List<Double> load1m;
        private List<Double> load5m;
        private List<Double> load15m;
        private Integer cpuCoreCount;
    }

    /**
     * 차트5: 연결 상태 분포 (24시간)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionStatus24h {
        private List<String> categories;
        private List<Integer> active;
        private List<Integer> idle;
        private List<Integer> idleInTx;
    }

    /**
     * 차트6: TPS 일일 추이 (24시간)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TpsDailyTrend24h {
        private List<String> categories;
        private List<Integer> commitTps;
        private List<Integer> rollbackTps;
    }

    /**
     * 차트7: Wait Event 유형별 분포 (24시간)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaitEventDistribution24h {
        private List<String> categories;
        private List<Integer> lock;
        private List<Integer> io;
        private List<Integer> client;
        private List<Integer> activity;
        private List<Integer> lwlock;
        private List<Integer> other;
    }

    /**
     * 차트8: Backend 유형별 추이 (24시간)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackendTypeTrend24h {
        private List<String> categories;
        private List<Integer> client;
        private List<Integer> autovacuum;
        private List<Integer> parallel;
        private List<Integer> background;
    }

    /**
     * 차트9: 에러율 추이 (24시간)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorRateTrend24h {
        private List<String> categories;
        private List<Double> data;
    }
}
