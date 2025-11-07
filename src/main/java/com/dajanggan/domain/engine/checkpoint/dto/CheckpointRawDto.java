package com.dajanggan.domain.engine.checkpoint.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Checkpoint Raw Data DTO
 * - MyBatis 조회 결과 매핑용
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckpointRawDto {

    private Long checkpointRawId;
    private Long instanceId;
    private OffsetDateTime collectedAt;
    private String checkpointType;
    private Long checkpointsTimed;
    private Long checkpointsReq;
    private Double checkpointWriteTime;
    private Double checkpointSyncTime;
    private Long buffersCheckpoint;
    private Long walBytes;
    private OffsetDateTime createdAt;

    // 계산된 필드들
    private Double totalTime;              // write_time + sync_time
    private Double requestRatio;           // (req / (req + timed)) * 100
    private Long totalCheckpoints;         // req + timed
    private Double checkpointInterval;     // Checkpoint 간격 (분)
    private Double avgWalGenerationSpeed;  // 평균 WAL 생성 속도 (GB/h)
    
}
