package com.dajanggan.domain.engine.checkpoint.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Checkpoint 1분 집계 데이터 엔티티
 * 테이블: checkpoint_agg_1m
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckpointAgg1m {
    
    private Long checkpointAggId;           // PK
    private Long instanceId;                // 인스턴스 ID
    private OffsetDateTime collectedAt;      // 수집 시각
    private Double avgCheckpointReqRatio;   // 평균 요청형 체크포인트 비율
    private Double avgWriteTime;            // 평균 쓰기 시간 (초)
    private Double avgSyncTime;             // 평균 동기화 시간 (초)
    private Double avgTotalTime;            // 평균 총 시간 (초)
    private Long totalCheckpointsTimed;     // 총 정기 체크포인트 횟수
    private Long totalCheckpointsReq;       // 총 요청 체크포인트 횟수
    private BigDecimal totalWalBytes;       // 총 WAL 바이트 수
    private Long totalBuffersCheckpoint;    // 총 체크포인트 버퍼 수
    private String status;                  // 상태 (정상, 주의, 위험)
    private OffsetDateTime createdAt;        // 생성 시각
}
