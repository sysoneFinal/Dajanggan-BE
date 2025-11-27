// 작성자 : 김동현
package com.dajanggan.domain.system.disk.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Disk I/O Raw 데이터 엔티티 (pg_stat_io 기반)
 * 테이블: disk_io_raw
 * PostgreSQL 16의 pg_stat_io 뷰에서 수집한 I/O 통계
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiskIoRaw {

    private Long diskIoRawId;               // PK
    private Long instanceId;                // 인스턴스 ID
    private OffsetDateTime collectedAt;     // 수집 시각
    
    // pg_stat_io 기본 필드
    private String backendType;             // Backend 타입
    private String object;                  // Object 타입 (relation, temp relation 등)
    private String context;                 // Context (normal, vacuum, bulkread 등)
    
    // I/O 작업 카운트
    private Long reads;                     // 읽기 횟수
    private Double readTime;                // 읽기 시간 (ms)
    private Long writes;                    // 쓰기 횟수
    private Double writeTime;               // 쓰기 시간 (ms)
    private Long writebacks;                // Writeback 횟수
    private Double writebackTime;           // Writeback 시간 (ms)
    private Long extendCount;               // Extend 횟수
    private Double extendTime;              // Extend 시간 (ms)
    
    // I/O 작업 상세
    private Long opBytes;                   // 작업 바이트
    private Long hits;                      // 캐시 히트
    private Long evictions;                 // Eviction 횟수
    private Long reuses;                    // 재사용 횟수
    private Long fsyncs;                    // Fsync 횟수
    private Double fsyncTime;               // Fsync 시간 (ms)
    
    // pg_stat_database 메트릭 (데이터베이스 레벨)
    private String databaseName;            // 데이터베이스 이름
    private Long blksRead;                  // 디스크에서 읽은 블록 수 (Physical Read)
    private Long blksHit;                   // 캐시에서 읽은 블록 수 (Cache Hit)
    
    // pg_stat_bgwriter 메트릭
    private Long buffersBackendFsync;       // Backend가 직접 수행한 fsync 수 (병목 지표)
    private Long buffersCheckpoint;         // Checkpoint로 쓴 버퍼 수
    private Long buffersClean;              // Background writer가 쓴 버퍼 수
    private Long buffersBackend;            // Backend가 직접 쓴 버퍼 수
    
    // 추가 통계
    private OffsetDateTime statsReset;      // 통계 리셋 시각
    
    private OffsetDateTime createdAt;       // 생성 시각
}