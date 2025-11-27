// 작성자 : 김동현
package com.dajanggan.domain.engine.checkpoint.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Checkpoint Raw 데이터 엔티티
 * 테이블: checkpoint_raw
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckpointRaw {
    
    private Long checkpointRawId;        // PK
    private Long instanceId;             // 인스턴스 ID
    private OffsetDateTime collectedAt;  // 수집 시각
    private String checkpointType;       // 체크포인트 유형 (timed, requested)
    private Long checkpointsTimed;       // 정기 체크포인트 횟수
    private Long checkpointsReq;         // 요청 체크포인트 횟수
    private Double checkpointWriteTime;  // 체크포인트 쓰기 시간 (초)
    private Double checkpointSyncTime;   // 체크포인트 동기화 시간 (초)
    private Long buffersCheckpoint;      // 체크포인트가 쓴 버퍼 수
    private BigDecimal walBytes;         // WAL 바이트 수
    private OffsetDateTime createdAt;    // 생성 시각
    private Long walFilesAdded;          // WAL 파일 추가 수
    private Long walFilesRemoved;        // WAL 파일 제거 수
    private Long checkpointDistance;     // 체크포인트 간격 (bytes)
    private Long buffersBackend;         // Backend가 쓴 버퍼 수
    private Double avgBuffersPerSec;     // 평균 버퍼 처리량 (개/초)
}
