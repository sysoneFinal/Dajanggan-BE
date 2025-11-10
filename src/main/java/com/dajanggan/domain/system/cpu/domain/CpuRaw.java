package com.dajanggan.domain.system.cpu.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * CPU Raw 데이터 엔티티
 * 테이블: cpu_raw
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpuRaw {
    
    private Long cpuRawId;                  // PK
    private Long instanceId;                // 인스턴스 ID
    private OffsetDateTime collectedAt;      // 수집 시각
    private String backendType;             // 백엔드 타입
    private Double userCpu;                 // User CPU 사용률 (%)
    private Double systemCpu;               // System CPU 사용률 (%)
    private Double ioWait;                  // I/O Wait (%)
    private Long activeConnections;         // 활성 연결 수
    private Long parallelWorkers;           // 병렬 워커 수
    private Long waitingSessions;           // 대기 세션 수
    private Long workerTime;                // 워커 시간 (ms)
    private OffsetDateTime createdAt;        // 생성 시각
    private Double totalCpuUsage;           // 전체 CPU 사용률 (%)
    private Double idleCpu;                 // Idle CPU (%)
    private Double loadAvg1;                // Load Average 1분
    private Double loadAvg5;                // Load Average 5분
    private Double loadAvg15;               // Load Average 15분
    private Long contextSwitches;           // Context Switch 횟수
    private Long runningProcesses;          // 실행 중인 프로세스 수
    private Double stealCpu;                // Steal CPU (%)
}
