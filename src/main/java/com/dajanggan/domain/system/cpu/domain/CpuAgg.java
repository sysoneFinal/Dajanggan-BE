package com.dajanggan.domain.system.cpu.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * CPU 집계 데이터 엔티티 (pg_stat_activity 기반)
 * 테이블: cpu_agg
 * Raw 데이터를 그대로 복사하여 저장 (시계열 분석용)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpuAgg {
    
    private Long cpuAggId;                          // PK
    private Long instanceId;                        // 인스턴스 ID
    private OffsetDateTime collectedAt;             // 수집 시각
    
    // 연결 수 통계
    private Double avgTotalConnections;             // 전체 연결 수
    private Double avgActiveConnections;            // 활성 연결 수
    private Double avgIdleConnections;              // Idle 연결 수
    private Double avgIdleInTransaction;            // Idle in transaction 연결 수
    
    // 대기 세션 통계
    private Double avgWaitingSessions;              // 대기 세션 수
    private Double avgWaitingForLock;               // Lock 대기 세션 수
    private Double avgWaitingForIo;                 // IO 대기 세션 수
    private Double avgWaitEventClient;              // Client 대기 세션 수
    private Double avgWaitEventActivity;            // Activity 대기 세션 수
    private Double avgWaitEventBufferpin;           // BufferPin 대기 세션 수
    private Double avgWaitEventLwlock;              // LWLock 대기 세션 수
    private Double avgWaitEventTimeout;             // Timeout 대기 세션 수
    private Double avgWaitEventIpc;                 // IPC 대기 세션 수
    
    // Backend 타입별 통계
    private Double avgClientBackend;                // Client Backend 수
    private Double avgAutovacuumWorker;             // Autovacuum Worker 수
    private Double avgParallelWorker;               // Parallel Worker 수
    private Double avgBackgroundWorker;             // Background Worker 수
    
    // 쿼리 분석
    private Double avgLongRunningQueries;           // 장시간 실행 쿼리 수
    private Double maxQueryDurationSec;             // 최대 쿼리 실행 시간(초)
    
    // pg_stat_database 트랜잭션 통계
    private String databaseName;                    // 데이터베이스명
    private Long deltaXactCommit;                   // 커밋 트랜잭션 증분
    private Long deltaXactRollback;                 // 롤백 트랜잭션 증분
    private Double xactCommitRate;                  // 초당 커밋 트랜잭션 수 (TPS)
    private Double xactRollbackRate;                // 초당 롤백 트랜잭션 수
    
    // 상태 (정상/주의/위험)
    private String status;                          // 상태
    
    private OffsetDateTime createdAt;               // 생성 시각
}
