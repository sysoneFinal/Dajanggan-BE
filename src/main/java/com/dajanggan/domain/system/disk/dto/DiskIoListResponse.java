// 작성자 : 김동현
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
    private List<HighFsyncItem> highFsyncList;
    private List<LowCacheHitItem> lowCacheHitList;
    private Long totalCount;
    private Integer page;
    private Integer size;
    private Integer totalPages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HighFsyncItem {
        private OffsetDateTime collectedAt;
        private Double fsyncRate;
        private Double bufferHitRatio;
        private Double avgLatency;
        private String status;
        private String backendType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LowCacheHitItem {
        private OffsetDateTime collectedAt;
        private Double bufferHitRatio;
        private Long physicalReads;
        private Long cacheHits;
        private String status;
        private String backendType;
        private String databaseName;
    }
}

