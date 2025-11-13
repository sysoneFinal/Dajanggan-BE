package com.dajanggan.domain.engine.hotindex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class HotIndexDto {

    /**
     * HotIndex 대시보드 전체 응답
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardResponse {
        private UsageDistribution usageDistribution;
        private TopUsage topUsage;
        private InefficientIndexes inefficientIndexes;
        private CacheHitRatio cacheHitRatio;
        private Efficiency efficiency;
        private AccessTrend accessTrend;
        private ScanSpeed scanSpeed;
        private RecentStats recentStats;
    }

    /**
     * 인덱스 사용 분포
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsageDistribution {
        private List<String> categories;    // ["사용 중", "미사용", "비효율"]
        private List<Long> data;            // 각 카테고리별 인덱스 개수
    }

    /**
     * Top 사용 인덱스
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopUsage {
        private List<String> categories;    // 인덱스명
        private List<Long> data;            // 스캔 횟수
        private Long total;                 // 총 스캔 횟수
    }

    /**
     * 비효율 인덱스 Top-5
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InefficientIndexes {
        private List<String> categories;    // 인덱스명
        private List<Double> data;          // 비효율성 점수
        private Long total;                 // 총 비효율 인덱스 수
    }

    /**
     * 캐시 히트율
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheHitRatio {
        private List<String> categories;    // 시간 카테고리
        private List<Double> data;          // 캐시 히트율
        private Double average;             // 평균
        private Double min;                 // 최소값
        private Double max;                 // 최대값
    }

    /**
     * 인덱스 효율성 (Scatter)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Efficiency {
        private List<String> categories;    // 인덱스명
        private List<IndexEfficiency> indexes; // 효율성 데이터
    }

    /**
     * 인덱스 효율성 데이터 포인트
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndexEfficiency {
        private Long x;                     // 사용 횟수 (idx_scan)
        private Double y;                   // 효율성 (%)
        private String name;                // 인덱스명
    }

    /**
     * 인덱스 접근 추이
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessTrend {
        private List<String> categories;    // 시간 카테고리
        private List<Long> reads;           // 읽기 (idx_tup_read)
        private List<Long> writes;          // 쓰기 (추정값)
        private Long totalReads;            // 총 읽기
        private Long totalWrites;           // 총 쓰기
    }

    /**
     * 인덱스 스캔 속도 추이
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScanSpeed {
        private List<String> categories;    // 시간 카테고리
        private List<Double> data;          // 스캔 속도 (ms)
        private Double average;             // 평균
        private Double max;                 // 최대값
        private Double min;                 // 최소값
    }

    /**
     * 최근 통계 (Summary Cards)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentStats {
        private Double cacheHitRatio;       // 평균 캐시 히트율
        private Double avgScanSpeed;        // 평균 스캔 속도 (ms)
        private Long totalReads;            // 총 읽기 횟수
        private Long totalWrites;           // 총 쓰기 횟수
        private Long inefficientCount;      // 비효율 인덱스 수
    }

    /**
     * HotIndex 리스트 응답
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListResponse {
        private List<ListItem> data;
        private Long total;
    }

    /**
     * HotIndex 리스트 아이템
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListItem {
        private String id;
        private String indexName;           // 인덱스명
        private String tableName;           // 테이블명
        private String schemaName;          // 스키마명
        private String indexType;           // 인덱스 타입 (btree, hash, gin, gist 등)
        private String size;                // 크기 (포맷된 문자열)
        private Long idxScan;               // Index Scan 횟수
        private Long idxTupRead;            // Index Tuple Read
        private Long idxTupFetch;           // Index Tuple Fetch
        private Double cacheHit;            // 캐시 Hit(%)
        private Double bloatPercent;        // Bloat(%)
        private Double avgScanTime;         // 평균 스캔 시간 (ms)
        private String lastUsed;            // 마지막 사용 시각
        private String status;              // 상태 (정상, 비효율, 미사용, bloat)
    }
}