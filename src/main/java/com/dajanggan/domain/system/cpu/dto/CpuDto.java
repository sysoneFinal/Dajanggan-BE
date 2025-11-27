// 작성자 : 김동현
package com.dajanggan.domain.system.cpu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * CPU 페이지 DTO
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
        private OsCpuUsageTrend10m osCpuUsageTrend10m;                     // 차트1
        private PostgresqlTpsTrend10m postgresqlTpsTrend10m;               // 차트2
        private PostgresqlActiveConnections10m postgresqlActiveConnections10m; // 차트3
        private LoadAverageTrend15m loadAverageTrend15m;                 // 차트4
        private ConnectionStatus1h connectionStatus1h;                 // 차트5
        private TpsDailyTrend24h tpsDailyTrend24h;                       // 차트6
        private WaitEventDistribution15m waitEventDistribution15m;       // 차트7
        private BackendTypeTrend24h backendTypeTrend24h;                 // 차트8
        private ErrorRateTrend15m errorRateTrend15m;                     // 차트9
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
        private Integer page;
        private Integer size;
        private Integer totalPages;
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
     * 차트2: PostgreSQL TPS 추이 (최근 1시간)
     * 최근 60개 데이터 포인트 (1분 간격)
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
     * 차트3: PostgreSQL 활성 연결 (최근 24시간)
     * cpu_agg_5m 테이블에서 avg_active_connections만 사용
     * OS CPU 사용률은 실시간 차트에서 별도 제공되므로 제거됨
     * osCpuUsage 필드는 호환성을 위해 유지하되 빈 값으로 설정됨
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostgresqlActiveConnections10m {
        private List<String> categories; // 시간 형식: HH:mm
        private List<Double> osCpuUsage; // 호환성을 위해 유지 (빈 값)
        private List<Integer> activeConnections; // avg_active_connections
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
     * 차트5: 연결 상태 분포 (최근 24시간)
     * cpu_agg_5m 테이블에서 avg_active_connections, avg_idle_connections, avg_idle_in_transaction 사용
     * 시간 형식: HH:mm
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionStatus1h {
        private List<String> categories; // 시간 형식: HH:mm
        private List<Integer> active; // avg_active_connections
        private List<Integer> idle; // avg_idle_connections
        private List<Integer> idleInTx; // avg_idle_in_transaction
    }

    /**
     * 차트6: TPS 일일 추이 (최근 24시간)
     * cpu_agg_5m 테이블에서 total_xact_commit / record_count, total_xact_rollback / record_count로 TPS 계산
     * 시간 형식: HH:mm
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TpsDailyTrend24h {
        private List<String> categories; // 시간 형식: HH:mm
        private List<Integer> commitTps; // total_xact_commit / record_count / 60 (초당 TPS)
        private List<Integer> rollbackTps; // total_xact_rollback / record_count / 60 (초당 TPS)
    }

    /**
     * 차트7: Wait Event 유형별 분포 (최근 24시간)
     * cpu_agg_5m 테이블에서 avg_waiting_for_lock, avg_waiting_for_io, avg_wait_event_client,
     * avg_wait_event_activity, avg_wait_event_lwlock, avg_wait_event_bufferpin + avg_wait_event_timeout + avg_wait_event_ipc 사용
     * 시간 형식: HH:mm
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaitEventDistribution15m {
        private List<String> categories; // 시간 형식: HH:mm
        private List<Integer> lock; // avg_waiting_for_lock
        private List<Integer> io; // avg_waiting_for_io
        private List<Integer> client; // avg_wait_event_client
        private List<Integer> activity; // avg_wait_event_activity
        private List<Integer> lwlock; // avg_wait_event_lwlock
        private List<Integer> other; // avg_wait_event_bufferpin + avg_wait_event_timeout + avg_wait_event_ipc
    }

    /**
     * 차트8: Backend 유형별 추이 (최근 24시간)
     * cpu_agg_5m 테이블에서 avg_client_backend, avg_autovacuum_worker, avg_parallel_worker, avg_background_worker 사용
     * 시간 형식: HH:mm
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackendTypeTrend24h {
        private List<String> categories; // 시간 형식: HH:mm
        private List<Integer> client; // avg_client_backend
        private List<Integer> autovacuum; // avg_autovacuum_worker
        private List<Integer> parallel; // avg_parallel_worker
        private List<Integer> background; // avg_background_worker
    }

    /**
     * 차트9: 에러율 추이 (최근 24시간)
     * cpu_agg_5m 테이블에서 (total_xact_rollback / (total_xact_commit + total_xact_rollback)) * 100로 에러율 계산
     * 시간 형식: HH:mm
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorRateTrend15m {
        private List<String> categories; // 시간 형식: HH:mm
        private List<Double> data; // 에러율 (%): (total_xact_rollback / (total_xact_commit + total_xact_rollback)) * 100
    }
}
