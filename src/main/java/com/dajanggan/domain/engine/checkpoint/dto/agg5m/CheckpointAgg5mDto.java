// 작성자 : 김동현
package com.dajanggan.domain.engine.checkpoint.dto.agg5m;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Checkpoint 5분 집계 DTO (배치용)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckpointAgg5mDto {
    private Long instanceId;
    private OffsetDateTime timeBucket;
    private Double avgCheckpointReqRatio;
    private Double avgWriteTime;
    private Double avgSyncTime;
    private Double avgTotalTime;
    private Long totalCheckpointsTimed;
    private Long totalCheckpointsReq;
    private BigDecimal totalWalBytes;
    private Long totalBuffersCheckpoint;
    private String status;
    private Long recordCount;
}







