package com.dajanggan.domain.engine.bgwriter.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * BGWriter 5분 집계 데이터 엔티티
 * 테이블: bgwriter_agg_5m
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BgWriterAgg5m {
    
    private Long bgwriterAggId;             // PK
    private Long instanceId;                // 인스턴스 ID
    private OffsetDateTime collectedAt;      // 수집 시각
    private Double avgBackendFlushRatio;    // 평균 Backend Flush 비율
    private Double avgCleanRate;            // 평균 Clean Rate
    private Long totalBuffersClean;         // 총 BGWriter가 쓴 버퍼
    private Long totalBuffersBackend;       // 총 Backend가 쓴 버퍼
    private Long totalBackendFsync;         // 총 Backend fsync 발생 횟수
    private Long totalMaxwrittenClean;      // 총 Maxwritten clean 발생 횟수
    private String status;                  // 상태
    private OffsetDateTime createdAt;        // 생성 시각
    private Double avgCycleTimeMs;          // 평균 사이클 타임 (ms)
}
