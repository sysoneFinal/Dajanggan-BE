package com.dajanggan.domain.engine.checkpoint.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * - checkpoint_raw 테이블 매핑
 * - pg_stat_bgwriter에서 수집한 원시 데이터
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckpointRaw {

    private Long checkpointRawId;           // PK
    private Long instanceId;                // FK - monitor_instance
    private OffsetDateTime collectedAt;     // 수집 시각
    private String checkpointType;          // Checkpoint 타입 (timed/requested)
    private Long checkpointsTimed;          // 시간 기반 발생 횟수
    private Long checkpointsReq;            // 요청 기반 발생 횟수
    private Double checkpointWriteTime;     // Write 시간 (ms)
    private Double checkpointSyncTime;      // Sync 시간 (ms)
    private Long buffersCheckpoint;         // 기록된 버퍼 수
    private Long walBytes;                  // WAL 생성량 (bytes)
    private OffsetDateTime createdAt;       // 생성 시각

}
