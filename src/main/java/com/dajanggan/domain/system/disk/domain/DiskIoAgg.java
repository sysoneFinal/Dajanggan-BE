package com.dajanggan.domain.system.disk.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Disk I/O 집계 데이터 엔티티
 * 테이블: disk_io_agg
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiskIoAgg {

    private Long diskIoAggId;              // PK
    private Long instanceId;               // 인스턴스 ID
    private LocalDateTime collectedAt;     // 수집 시각
    private String backendType;            // Backend 타입
    private Long totalReads;               // 총 읽기 횟수
    private Long totalWrites;              // 총 쓰기 횟수
    private Long totalFsyncs;              // 총 Fsync 횟수
    private Long totalEvictions;           // 총 Eviction 횟수
    private Long totalBlksRead;            // 총 읽은 블록 수
    private Long totalBlksWritten;         // 총 쓴 블록 수
    private BigDecimal totalWalBytes;      // 총 WAL 바이트
    private Double avgReadLatency;         // 평균 읽기 지연시간
    private Double avgWriteLatency;        // 평균 쓰기 지연시간
    private Double readWriteRatio;         // 읽기/쓰기 비율
    private String status;                 // 상태
    private LocalDateTime createdAt;       // 생성 시각
    private Double avgQueueDepth;          // 평균 큐 깊이
    private Double maxQueueDepth;          // 최대 큐 깊이
    private Long totalExtendCount;       // 총 Extend 횟수
}