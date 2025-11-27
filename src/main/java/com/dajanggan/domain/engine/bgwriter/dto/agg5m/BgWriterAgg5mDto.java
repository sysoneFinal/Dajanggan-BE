// 작성자 : 김동현
package com.dajanggan.domain.engine.bgwriter.dto.agg5m;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * BGWriter 5분 집계 DTO (배치용)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BgWriterAgg5mDto {
    private Long instanceId;
    private OffsetDateTime timeBucket;
    private Double avgBackendFlushRatio;
    private Double avgCleanRate;
    private Long totalBuffersClean;
    private Long totalBuffersBackend;
    private Long totalBackendFsync;
    private Long totalMaxwrittenClean;
    private String status;
    private Double avgCycleTimeMs;
    private Long recordCount;
}







