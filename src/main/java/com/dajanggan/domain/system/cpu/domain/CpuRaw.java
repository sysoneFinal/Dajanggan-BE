package com.dajanggan.domain.system.cpu.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * CPU Raw 데이터 엔티티 (pg_stat_activity 기반)
 * 테이블: cpu_raw
 * PostgreSQL의 연결 상태 및 활동 통계를 저장
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpuRaw {
    
    private Long cpuRawId;                      // PK
    private Long instanceId;                    // 인스턴스 ID
    private OffsetDateTime collectedAt;         // 수집 시각
    
    // 전체 연결 통계
    private Long totalConnections;              // 전체 연결 수
    private Long activeConnections;             // state='active'
    private Long idleConnections;               // state='idle'
    private Long idleInTransaction;             // state='idle in transaction'
    
    // 대기 상태 분석
    private Long waitingSessions;               // wait_event IS NOT NULL
    private Long waitingForLock;                // wait_event_type='Lock'
    private Long waitingForIo;                  // wait_event_type='IO'
    private Long waitEventClient;               // wait_event_type='Client'
    private Long waitEventActivity;             // wait_event_type='Activity'
    private Long waitEventBufferpin;            // wait_event_type='BufferPin'
    private Long waitEventLwlock;               // wait_event_type='LWLock'
    private Long waitEventTimeout;              // wait_event_type='Timeout'
    private Long waitEventIpc;                  // wait_event_type='IPC'
    
    // Backend 타입별 분석
    private Long clientBackendCount;            // backend_type='client backend'
    private Long autovacuumWorkerCount;         // backend_type='autovacuum worker'
    private Long parallelWorkerCount;           // backend_type='parallel worker'
    private Long backgroundWorkerCount;         // 기타 background worker
    
    // 쿼리 실행 시간 분석
    private Long longRunningQueries;            // state_change > 5분
    private Double maxQueryDurationSec;         // 가장 긴 쿼리 실행 시간(초)
    
    // pg_stat_database 트랜잭션 통계
    private String databaseName;                // 데이터베이스명
    private Long xactCommit;                    // 커밋된 트랜잭션 수 (누적)
    private Long xactRollback;                  // 롤백된 트랜잭션 수 (누적)
    
    private OffsetDateTime createdAt;           // 생성 시각
}
