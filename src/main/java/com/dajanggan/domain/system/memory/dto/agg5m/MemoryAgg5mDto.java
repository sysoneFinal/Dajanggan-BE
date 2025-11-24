package com.dajanggan.domain.system.memory.dto.agg5m;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Memory 5분 집계 DTO (배치용)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryAgg5mDto {
    private Long instanceId;
    private OffsetDateTime timeBucket;
    private String relname;
    private String relkind;
    private Long avgBuffers;
    private Double avgBufferUsagePct;
    private Double avgDirtyRatio;
    private Double avgPinnedBuffers;
    private Long totalHeapBlksRead;
    private Long totalHeapBlksHit;
    private Long totalIdxBlksRead;
    private Long totalIdxBlksHit;
    private Double avgCacheHitRatio;
    private Double avgUsagecount;
    private Double avgBufferReuseScore;
    private String databaseName;
    private Long totalTempFiles;
    private Long totalTempBytes;
    private Double avgTempFileRate;
    private Double avgTempBytesPerSec;
    private Double totalBlkReadTime;
    private Double totalBlkWriteTime;
    private Double avgIoWaitTimeMs;
    private Long recordCount;
}






