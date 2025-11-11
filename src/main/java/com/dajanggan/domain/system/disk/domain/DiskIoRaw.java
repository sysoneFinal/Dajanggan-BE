package com.dajanggan.domain.system.disk.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Disk I/O Raw 데이터 엔티티
 * 테이블: disk_io_raw
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiskIoRaw {

    private Long diskIoRawId;           // PK
    private Long instanceId;            // 인스턴스 ID
    private LocalDateTime collectedAt;  // 수집 시각
    private String backendType;         // Backend 타입
    private Long reads;                 // 읽기 횟수
    private Long writes;                // 쓰기 횟수
    private Long fsyncs;                // Fsync 횟수
    private Long evictions;             // Eviction 횟수
    private Double readLatencyMs;       // 읽기 지연시간 (ms)
    private Double writeLatencyMs;      // 쓰기 지연시간 (ms)
    private Long blksRead;              // 읽은 블록 수
    private Long blksWritten;           // 쓴 블록 수
    private Long walBytes;              // WAL 바이트
    private Double readPercent;         // 읽기 비율 (%)
    private Double writePercent;        // 쓰기 비율 (%)
    private LocalDateTime createdAt;    // 생성 시각
    private Double queueDepth;          // 큐 깊이
    private Double diskUtilization;     // 디스크 사용률
    private Long extendCount;               // Extend 횟수
}