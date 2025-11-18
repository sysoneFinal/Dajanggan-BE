package com.dajanggan.domain.system.cpu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * CPU 대시보드 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpuDashboardResponse {
    private Widgets widgets;
    private Charts charts;

    /**
     * 위젯 5개
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Widgets {
        private OsCpuUsageWidget osCpuUsage;
        private PostgresqlTpsWidget postgresqlTps;
        private ErrorRateWidget errorRate;
        private BackendProcessesWidget backendProcesses;
        private LoadAverageWidget loadAverage;
    }

    /**
     * 차트 9개
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Charts {
        private OsCpuUsageTrend10m osCpuUsageTrend10m;
        private PostgresqlTpsTrend10m postgresqlTpsTrend10m;
        private PostgresqlActiveConnections10m postgresqlActiveConnections10m;
        private LoadAverageTrend15m loadAverageTrend15m;
        private ConnectionStatus1h connectionStatus1h;
        private TpsDailyTrend24h tpsDailyTrend24h;
        private WaitEventDistribution15m waitEventDistribution15m;
        private BackendTypeTrend24h backendTypeTrend24h;
        private ErrorRateTrend15m errorRateTrend15m;
    }

    // ========================================
    // Widget DTOs
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
        private String status;
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
    // Chart DTOs
    // ========================================

    /**
     * 차트1: OS CPU 사용률 추이 (최근 10분)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OsCpuUsageTrend10m {
        private List<String> categories;
        private List<Double> data;
    }

    /**
     * 차트2: PostgreSQL TPS 추이 (최근 10분)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostgresqlTpsTrend10m {
        private List<String> categories;
        private List<Integer> commitTps;
        private List<Integer> rollbackTps;
    }

    /**
     * 차트3: PostgreSQL 활성 연결 (최근 10분)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostgresqlActiveConnections10m {
        private List<String> categories;
        private List<Double> osCpuUsage;
        private List<Integer> activeConnections;
    }

    /**
     * 차트4: Load Average 추이 (최근 15분)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoadAverageTrend15m {
        private List<String> categories;
        private List<Double> load1m;
        private List<Double> load5m;
        private List<Double> load15m;
        private Integer cpuCoreCount;
    }

    /**
     * 차트5: 연결 상태 분포 (1시간)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionStatus1h {
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
     * 차트7: Wait Event 유형별 분포 (최근 15분)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaitEventDistribution15m {
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
     * 차트9: 에러율 추이 (최근 15분)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorRateTrend15m {
        private List<String> categories;
        private List<Double> data;
    }
}

