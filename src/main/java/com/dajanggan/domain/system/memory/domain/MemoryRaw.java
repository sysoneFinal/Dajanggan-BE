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
public class MemoryRaw {

    private Long memoryRawId;            // PK
    private Long instanceId;             // 인스턴스 ID
    private LocalDateTime collectedAt;   // 수집 시각
    private String relname;              // 객체명 (테이블/인덱스명)
    private String relkind;              // 객체 종류 (r=table, i=index)
    private Long buffers;                // 버퍼 수
    private Long dirtyBuffers;           // Dirty 버퍼 수
    private Long heapBlksRead;           // Heap Block Read 수
    private Long heapBlksHit;            // Heap Block Hit 수
    private LocalDateTime createdAt;     // 생성 시각
    private Long pinnedBuffers;          // 고정 버퍼 수
    private Long accessCount;            // 접근 횟수
    private Long evictionCount;          // Eviction 횟수
    private Double avgAccessTimeMs;      // 평균 접근 시간 (ms)
}