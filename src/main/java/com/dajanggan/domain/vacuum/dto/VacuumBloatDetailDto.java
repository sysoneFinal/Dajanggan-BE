package com.dajanggan.domain.vacuum.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class VacuumBloatDetailDto {

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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Kpi {
        private String bloatPct;
        private String tableSize;
        private String wastedSpace;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BloatTrend {
        private List<Double> data;
        private List<String> labels;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeadTuplesTrend {
        private List<Long> data;
        private List<String> labels;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndexBloatTrend {
        private List<List<Double>> data;
        private List<String> labels;
        private List<String> names;
    }
}