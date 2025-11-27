// 작성자 : 김동현
package com.dajanggan.domain.system.disk.dto.agg5m;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Disk I/O 5분 집계 DTO (배치용)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiskIoAgg5mDto {
    private Long instanceId;
    private OffsetDateTime timeBucket;
    private String backendType;
    private Double avgReadLatencyMs;
    private Double avgWriteLatencyMs;
    private Double avgCacheHitRatio;
    private Double avgBufferHitRatio;
    private Double avgBackendFsyncRate;
    private Double avgReadWriteRatio;
    private Long sumDeltaReads;
    private Long sumDeltaWrites;
    private Long sumDeltaFsyncs;
    private Long sumDeltaEvictions;
    private Long sumDeltaBlksRead;
    private Long sumDeltaBlksHit;
    private Long sumDeltaBuffersCheckpoint;
    private Long sumDeltaBuffersClean;
    private Long sumDeltaBuffersBackend;
    private Long sumDeltaBuffersBackendFsync;
    private Double maxReadLatencyMs;
    private Double maxWriteLatencyMs;
    private Double minBufferHitRatio;
    private String status;
}







