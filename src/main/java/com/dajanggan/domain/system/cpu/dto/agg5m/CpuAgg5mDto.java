// 작성자 : 김동현
package com.dajanggan.domain.system.cpu.dto.agg5m;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * CPU 5분 집계 DTO (배치용)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpuAgg5mDto {
    private Long instanceId;
    private OffsetDateTime timeBucket;
    private Double avgTotalConnections;
    private Double avgActiveConnections;
    private Double avgIdleConnections;
    private Double avgIdleInTransaction;
    private Double avgWaitingSessions;
    private Double avgWaitingForLock;
    private Double avgWaitingForIo;
    private Double avgWaitEventClient;
    private Double avgWaitEventActivity;
    private Double avgWaitEventBufferpin;
    private Double avgWaitEventLwlock;
    private Double avgWaitEventTimeout;
    private Double avgWaitEventIpc;
    private Double avgClientBackend;
    private Double avgAutovacuumWorker;
    private Double avgParallelWorker;
    private Double avgBackgroundWorker;
    private Double avgLongRunningQueries;
    private Long totalXactCommit;
    private Long totalXactRollback;
    private Long recordCount;
}

