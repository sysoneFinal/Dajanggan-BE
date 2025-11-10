package com.dajanggan.domain.engine.bgwriter.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * BGWriter Raw 데이터 엔티티
 * 테이블: bgwriter_raw
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BgWriterRaw {
    
    private Long bgwriterRawId;          // PK
    private Long instanceId;             // 인스턴스 ID
    private LocalDateTime collectedAt;   // 수집 시각
    private Long buffersClean;           // BGWriter가 쓴 버퍼 수
    private Long buffersBackend;         // Backend가 직접 쓴 버퍼 수
    private Long buffersBackendFsync;    // Backend fsync 발생 횟수
    private Long maxwrittenClean;        // Maxwritten clean 발생 횟수
    private LocalDateTime createdAt;     // 생성 시각
    private Long buffersAlloc;           // 할당된 버퍼 수
    private Double avgCycleTimeMs;       // 평균 사이클 타임 (ms)
}
