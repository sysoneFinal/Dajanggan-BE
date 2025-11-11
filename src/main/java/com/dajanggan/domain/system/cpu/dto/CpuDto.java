package com.dajanggan.domain.system.cpu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class CpuDto {

    /**
     * 대시보드 응답 - 프론트엔드 스펙에 맞춤
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardResponse {
        private CpuUsage cpuUsage;
        private CpuUsageTrend cpuUsageTrend;
        private CpuLoadTypes cpuLoadTypes;
        private IoWaitVsLatency ioWaitVsLatency;
        private BackendProcessStats backendProcessStats;
        private WaitEventDistribution waitEventDistribution;
        private RecentStats recentStats;
    }

    /**
     * CPU 사용률 (게이지용)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CpuUsage {
        private Double value;
        private String description;
        private Long runningQueries;
        private Long waitingQueries;
        private Long idleConnections;
    }

    /**
     * CPU 사용률 트렌드
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
        private List<Double> autoVacuum;
        private List<Double> bgWriter;
        private List<Double> checkpoint;
        private List<Double> postgresqlBackend;
    }

    /**
     * I/O Wait vs 디스크 Latency 상관관계
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
        private List<Long> activeCount;
        private List<Long> idleCount;
        private List<Long> totalCount;
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
     * 최근 통계
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentStats {
        private LoadAverageStats loadAverage;
        private Double ioWait;
        private ConnectionStats connections;
        private Double idleCpu;
        private Long contextSwitches;
        private Double postgresqlBackendCpu;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoadAverageStats {
        private Double one;
        private Double five;
        private Double fifteen;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionStats {
        private Long active;
        private Long idle;
        private Long total;
    }

    /**
     * 리스트 응답
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListResponse {
        private List<ListItem> data;
        private Long total;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListItem {
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
        private Long activeSessions;
        private Long parallelWorkers;
        private Long waitingSessions;
        private Double workerTime;
        private Long contextSwitches;
        private String status;
    }
}