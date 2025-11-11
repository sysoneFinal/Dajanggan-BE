package com.dajanggan.domain.system.memory.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryAgg {

    private Long memoryAggId;              // PK
    private Long instanceId;               // 인스턴스 ID
    private LocalDateTime collectedAt;     // 수집 시각
    private String relname;                // 객체명 (테이블/인덱스명)
    private String relkind;                // 객체 종류 (r=table, i=index)
    private Long avgBuffers;               // 평균 버퍼 수
    private Double avgBufferUsagePct;      // 평균 버퍼 사용률 (%)
    private Double avgDirtyRatio;          // 평균 Dirty 비율
    private Double avgHitRatio;            // 평균 Hit 비율
    private Long totalHeapBlksRead;        // 총 Heap Block Read
    private Long totalHeapBlksHit;         // 총 Heap Block Hit
    private String status;                 // 상태 (정상, 주의, 위험)
    private LocalDateTime createdAt;       // 생성 시각
    private Double avgPinnedBuffers;       // 평균 고정 버퍼 수
    private Long totalAccessCount;         // 총 접근 횟수
    private Long totalEvictionCount;       // 총 Eviction 횟수
    private Double avgAccessTimeMs;        // 평균 접근 시간 (ms)
}