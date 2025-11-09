package com.dajanggan.domain.vacuum.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Vacuum Bloat 관련 모든 DTO 통합
 */
public class VacuumBloatDto {

    /**
     * 대시보드 응답 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardResponse {
        private XminHorizonMonitor xminHorizonMonitor;
        private BloatTrend bloatTrend;
        private BloatDistribution bloatDistribution;
        private Kpi kpi;
    }

    /**
     * Xmin Horizon Monitor 데이터
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class XminHorizonMonitor {
        private List<List<Double>> data;  // [xminHorizonAge, vacuumProcessingSpeed]
        private List<String> labels;      // 시간 라벨 (00:00, 01:00, ...)
    }

    /**
     * Bloat Trend 데이터
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BloatTrend {
        private List<Double> data;        // Bloat 크기 (GB)
        private List<String> labels;      // 날짜 라벨
    }

    /**
     * Bloat Distribution 데이터
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BloatDistribution {
        private List<Integer> data;       // 각 범위별 테이블 수
        private List<String> labels;      // 범위 라벨 (0-5%, 5-10%, ...)
    }

    /**
     * KPI 지표 데이터
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Kpi {
        private String tableBloat;        // 전체 테이블 Bloat (예: "305.3GB")
        private Integer criticalTable;    // 위험 수준 테이블 수
        private String bloatGrowth;       // Bloat 증가량 (예: "+31GB")
    }
}