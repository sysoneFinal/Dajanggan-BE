package com.dajanggan.domain.vacuum.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class VacuumBloatDetailDto {

    /**
     * 전체 응답 (대시보드용)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Kpi kpi;
        private BloatTrend bloatTrend;
        private DeadTuplesTrend deadTuplesTrend;
        private IndexBloatTrend indexBloatTrend;
    }

    /**
     * KPI 지표
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Kpi {
        private String bloatPct;        // 예: "9.4%"
        private String tableSize;       // 예: "16 GB"
        private String wastedSpace;     // 예: "1.5 GB"
    }

    /**
     * Bloat % 트렌드
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BloatTrend {
        private List<Double> data;      // Bloat % 값들
        private List<String> labels;    // 시간 레이블들
    }

    /**
     * Dead Tuples 트렌드
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeadTuplesTrend {
        private List<Long> data;        // Dead tuples 개수
        private List<String> labels;    // 시간 레이블들
    }

    /**
     * 인덱스별 Bloat 트렌드
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndexBloatTrend {
        private List<List<Double>> data;  // 각 인덱스별 bloat % 시계열
        private List<String> labels;      // 시간 레이블들
        private List<String> names;       // 인덱스 이름들
    }
}