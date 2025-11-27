// 작성자 : 김동현
package com.dajanggan.domain.system.disk.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Disk I/O 집계 데이터 엔티티 (pg_stat_io 기반)
 * 테이블: disk_io_agg
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiskIoAgg {

    private Long diskIoAggId;               // PK
    private Long instanceId;                // 인스턴스 ID
    private OffsetDateTime collectedAt;     // 수집 시각
    private String backendType;             // Backend 타입
    
    // I/O 작업 증분 (delta)
    private Long deltaReads;                // 읽기 증분
    private Double deltaReadTime;           // 읽기 시간 증분
    private Long deltaWrites;               // 쓰기 증분
    private Double deltaWriteTime;          // 쓰기 시간 증분
    private Long deltaWritebacks;           // Writeback 증분
    private Long deltaExtendCount;          // Extend 증분
    private Long deltaHits;                 // 캐시 히트 증분
    private Long deltaEvictions;            // Eviction 증분
    private Long deltaFsyncs;               // Fsync 증분
    private Double deltaFsyncTime;          // Fsync 시간 증분
    
    // 계산된 메트릭
    private Double avgReadLatencyMs;        // 평균 읽기 레이턴시
    private Double avgWriteLatencyMs;       // 평균 쓰기 레이턴시
    private Double readWriteRatio;          // 읽기/쓰기 비율
    private Double cacheHitRatio;           // 캐시 히트율 (pg_stat_io 기반)
    
    // pg_stat_database 증분 메트릭
    private String databaseName;            // 데이터베이스 이름
    private Long deltaBlksRead;             // 디스크 읽기 블록 증분
    private Long deltaBlksHit;              // 캐시 히트 블록 증분
    private Double bufferHitRatio;          // Buffer Cache Hit Ratio = (blks_hit / (blks_hit + blks_read)) * 100
    
    // pg_stat_bgwriter 증분 메트릭
    private Long deltaBuffersBackendFsync;  // Backend fsync 증분
    private Long deltaBuffersCheckpoint;    // Checkpoint 버퍼 증분
    private Long deltaBuffersClean;         // Background writer 버퍼 증분
    private Long deltaBuffersBackend;       // Backend 직접 쓰기 버퍼 증분
    private Double backendFsyncRate;        // 초당 Backend fsync 수 (병목 핵심 지표)
    
    // 상태 (정상/주의/위험)
    private String status;
    
    private OffsetDateTime createdAt;       // 생성 시각
}