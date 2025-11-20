package com.dajanggan.domain.system.disk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Disk I/O 리스트 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiskIoListResponse {
    private List<LowCacheHitItem> lowCacheHitList;
    private Long totalCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LowCacheHitItem {
        private String collectedAt;
        private Double bufferHitRatio;
        private Long physicalReads;
        private Long cacheHits;
        private String status;
        private String backendType;
        private String databaseName;
    }
}


