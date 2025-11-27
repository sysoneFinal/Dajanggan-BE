// 작성자 : 김동현
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
    
    // 연결 통계 (평균) - DDL: int8
    private Long avgTotalConnections;
    private Long avgActiveConnections;
    private Long avgIdleConnections;
    private Long avgIdleInTransaction;
    private Long avgWaitingConnections;  // 추가
    private Double avgConnectionUsagePct;  // 추가
    
    // 대기 세션 통계 (평균) - DDL: float8
    private Double avgWaitingSessions;
    private Double avgWaitingForLock;
    private Double avgWaitingForIo;
    
    // 대기 이벤트 통계 (평균) - DDL: float8
    private Double avgWaitEventClient;
    private Double avgWaitEventActivity;
    private Double avgWaitEventBufferpin;
    private Double avgWaitEventLwlock;
    private Double avgWaitEventTimeout;
    private Double avgWaitEventIpc;
    
    // Backend 타입별 통계 (평균) - DDL: float8
    private Double avgClientBackend;
    private Double avgAutovacuumWorker;
    private Double avgParallelWorker;
    private Double avgBackgroundWorker;
    
    // 쿼리 분석 (평균 및 최대) - DDL: float8
    private Double avgLongRunningQueries;
    
    // 트랜잭션 통계 (합계) - DDL: int8
    private Long totalXactCommit;
    private Long totalXactRollback;
    
    // 블록 통계 (합계) - DDL: int8 (추가)
    private Long totalBlksRead;
    private Long totalBlksHit;
    private Double avgCacheHitRatio;  // 추가
    
    // 튜플 통계 (합계) - DDL: int8 (추가)
    private Long totalTupReturned;
    private Long totalTupFetched;
    private Long totalTupInserted;
    private Long totalTupUpdated;
    private Long totalTupDeleted;
    
    // 임시 파일 통계 (합계) - DDL: int8 (추가)
    private Long totalTempFiles;
    private Long totalTempBytes;
    
    // 메타 정보
    private Long recordCount;
    private OffsetDateTime createdAt;
}
