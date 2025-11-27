// 작성자 : 김동현
package com.dajanggan.domain.system.disk.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Disk I/O 5분 집계 데이터 엔티티
 * 테이블: disk_io_agg_5m
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiskIoAgg5m {

    private Long diskIoAgg5mId;              // PK
    private Long instanceId;                 // 인스턴스 ID
    private OffsetDateTime collectedAt;      // 수집 시각 (5분 단위)
    private String backendType;              // Backend 타입
    
    // 평균 메트릭
    private Double avgReadLatencyMs;         // 평균 읽기 레이턴시
    private Double avgWriteLatencyMs;        // 평균 쓰기 레이턴시
    private Double avgCacheHitRatio;         // 평균 캐시 히트율
    private Double avgBufferHitRatio;        // 평균 버퍼 히트율
    private Double avgBackendFsyncRate;      // 평균 Backend fsync rate
    private Double avgReadWriteRatio;        // 평균 읽기/쓰기 비율
    
    // 합계 메트릭
    private Long sumDeltaReads;              // 읽기 횟수 합계
    private Long sumDeltaWrites;             // 쓰기 횟수 합계
    private Long sumDeltaFsyncs;             // Fsync 횟수 합계
    private Long sumDeltaEvictions;          // Eviction 횟수 합계
    private Long sumDeltaBlksRead;           // Physical read 블록 합계
    private Long sumDeltaBlksHit;            // Cache hit 블록 합계
    private Long sumDeltaBuffersCheckpoint;  // Checkpoint 버퍼 합계
    private Long sumDeltaBuffersClean;       // Background writer 버퍼 합계
    private Long sumDeltaBuffersBackend;     // Backend 버퍼 합계
    private Long sumDeltaBuffersBackendFsync; // Backend fsync 합계
    
    // 최대/최소값
    private Double maxReadLatencyMs;         // 최대 읽기 레이턴시
    private Double maxWriteLatencyMs;        // 최대 쓰기 레이턴시
    private Double minBufferHitRatio;        // 최소 버퍼 히트율
    
    // 상태
    private String status;                   // 정상/주의/위험
    
    private OffsetDateTime createdAt;        // 생성 시각
}
