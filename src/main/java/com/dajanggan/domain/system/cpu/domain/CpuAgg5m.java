package com.dajanggan.domain.system.cpu.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * CPU 5분 집계 데이터
 * cpu_agg_5m 테이블에 매핑
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpuAgg5m {
    
    private Long cpuAgg5mId;
    private Long instanceId;
    private OffsetDateTime timeBucket;
    
    // 연결 통계 (평균)
    private Double avgTotalConnections;
    private Double avgActiveConnections;
    private Double avgIdleConnections;
    private Double avgIdleInTransaction;
    private Double avgWaitingSessions;
    private Double avgWaitingForLock;
    private Double avgWaitingForIo;
    
    // 대기 이벤트 통계 (평균)
    private Double avgWaitEventClient;
    private Double avgWaitEventActivity;
    private Double avgWaitEventBufferpin;
    private Double avgWaitEventLwlock;
    private Double avgWaitEventTimeout;
    private Double avgWaitEventIpc;
    
    // Backend 타입별 통계 (평균)
    private Double avgClientBackend;
    private Double avgAutovacuumWorker;
    private Double avgParallelWorker;
    private Double avgBackgroundWorker;
    
    // 쿼리 분석 (평균 및 최대)
    private Double avgLongRunningQueries;
    private Double maxQueryDurationSec;
    
    // 트랜잭션 통계 (합계)
    private Long totalXactCommit;
    private Long totalXactRollback;
    private Double avgXactCommitRate;
    private Double avgXactRollbackRate;
    
    // 메타 정보
    private Long recordCount;
    private OffsetDateTime createdAt;
}
