package com.dajanggan.domain.system.memory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Memory 리스트 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryListResponse {
    private List<HighBufferUsageItem> highBufferUsageList;
    private List<LowCacheHitItem> lowCacheHitList;
    private Long totalCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HighBufferUsageItem {
        private Long rankNum;
        private String tableName;
        private String relkind;
        private Long bufferCount;
        private Double bufferUsagePercent;
        private Double dirtyRatio;
        private Double cacheHitRatio;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LowCacheHitItem {
        private Long rankNum;
        private String tableName;
        private String databaseName;
        private Double cacheHitRatio;
        private Long physicalReads;
        private Long cacheHits;
        private String status;
    }
}

