package com.dajanggan.domain.engine.checkpoint.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * - checkpoint_agg 테이블 매핑
 * - 시간대별 집계 데이터
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckpointAgg {

    private Long checkpointAggId;               // PK
    private Long instanceId;                    // FK - monitor_instance
    private OffsetDateTime collectedAt;         // 집계 시각
    private Double avgCheckpointReqRatio;       // 평균 요청 비율 (%)
    private Double avgWriteTime;                // 평균 Write 시간 (ms)
    private Double avgSyncTime;                 // 평균 Sync 시간 (ms)
    private Double avgTotalTime;                // 평균 총 시간 (ms)
    private Long totalCheckpointsTimed;         // 총 시간 기반 발생 횟수
    private Long totalCheckpointsReq;           // 총 요청 기반 발생 횟수
    private Long totalWalBytes;                 // 총 WAL 생성량 (bytes)
    private Long totalBuffersCheckpoint;        // 총 버퍼 처리량
    private String status;                      // 상태 (NORMAL/WARNING/CRITICAL)
    private OffsetDateTime createdAt;           // 생성 시각

}
