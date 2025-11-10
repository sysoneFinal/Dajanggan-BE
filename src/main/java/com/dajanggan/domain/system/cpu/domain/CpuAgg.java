package com.dajanggan.domain.system.cpu.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * CPU 집계 데이터 엔티티
 * 테이블: cpu_agg
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpuAgg {
    
    private Long cpuAggId;                      // PK
    private Long instanceId;                    // 인스턴스 ID
    private OffsetDateTime collectedAt;          // 수집 시각
    private String backendType;                 // 백엔드 타입
    private Double avgUserCpu;                  // 평균 User CPU (%)
    private Double avgSystemCpu;                // 평균 System CPU (%)
    private Double avgIoWait;                   // 평균 I/O Wait (%)
    private Double maxUserCpu;                  // 최대 User CPU (%)
    private Double maxIoWait;                   // 최대 I/O Wait (%)
    private Long avgActiveConnections;          // 평균 활성 연결 수
    private Double avgParallelWorkers;          // 평균 병렬 워커 수
    private Double avgWaitingSessions;          // 평균 대기 세션 수
    private Double avgWorkerTime;               // 평균 워커 시간 (ms)
    private Long contextSwitches;               // Context Switch 횟수
    private Double cpuQueueLength;              // CPU Queue 길이
    private String status;                      // 상태 (정상, 주의, 위험)
    private OffsetDateTime createdAt;            // 생성 시각
    private Double avgTotalCpu;                 // 평균 전체 CPU (%)
    private Double maxTotalCpu;                 // 최대 전체 CPU (%)
    private Double avgIdleCpu;                  // 평균 Idle CPU (%)
    private Double avgLoad1;                    // 평균 Load Average 1분
    private Double avgLoad5;                    // 평균 Load Average 5분
    private Double avgLoad15;                   // 평균 Load Average 15분
    private Long totalContextSwitches;          // 총 Context Switch 횟수
    private Double avgRunningProcesses;         // 평균 실행 중인 프로세스 수
    private Double avgStealCpu;                 // 평균 Steal CPU (%)
    private Double maxStealCpu;                 // 최대 Steal CPU (%)
}
