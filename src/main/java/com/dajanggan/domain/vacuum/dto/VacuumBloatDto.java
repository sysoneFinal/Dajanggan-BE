// 작성자: 김민서
package com.dajanggan.domain.vacuum.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

public class VacuumBloatDto {

    // ========== Response DTOs ==========
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private XminHorizonMonitor xminHorizonMonitor;
        private BloatTrend bloatTrend;
        private IndexBloatTrend indexBloatTrend;
        private BloatDistribution bloatDistribution;
        private Kpi kpi;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class XminHorizonMonitor {
        private List<List<Double>> data;
        private List<String> labels;
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
    public static class IndexBloatTrend {
        private List<Double> data;
        private List<String> labels;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BloatDistribution {
        private List<Integer> data;
        private List<String> labels;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Kpi {
        private String tableBloat;
        private Integer criticalTable;
        private String bloatGrowth;
        
        // ✅ Severity 필드 추가 (백엔드에서 계산)
        private String tableBloatSeverity;      // "NORMAL", "WARNING", "CRITICAL"
        private String criticalTableSeverity;    // "NORMAL", "WARNING", "CRITICAL"
        private String bloatGrowthSeverity;      // "NORMAL", "WARNING", "CRITICAL"
        
        // ✅ 메타데이터 (백엔드에서 조회)
        private Long totalDatabaseSizeBytes;     // 전체 DB 크기
        private Integer totalTableCount;        // 전체 테이블 수
    }
}