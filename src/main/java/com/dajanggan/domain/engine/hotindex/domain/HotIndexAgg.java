package com.dajanggan.domain.engine.hotindex.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * HotIndex 집계 데이터 엔티티
 * 테이블: hot_index_agg
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotIndexAgg {

    private Long hotIndexAggId;          // PK
    private Long databaseId;             // 데이터베이스 ID
    private OffsetDateTime collectedAt;   // 수집 시각
    private String schemaName;           // 스키마명
    private String tableName;            // 테이블명
    private String indexName;            // 인덱스명
    private Long avgIndexSize;           // 평균 인덱스 크기
    private Long totalIdxScan;           // 총 Index Scan 횟수
    private Long totalIdxTupRead;        // 총 Index Tuple Read 수
    private Long totalIdxTupFetch;       // 총 Index Tuple Fetch 수
    private Long totalIdxBlksRead;       // 총 Index Block Read 수
    private Long totalIdxBlksHit;        // 총 Index Block Hit 수
    private Double avgIdxEfficiency;     // 평균 인덱스 효율성(%)
    private Double avgIdxHitRatio;       // 평균 인덱스 캐시 히트율(%)
    private Long idxScanPerDay;          // 일일 인덱스 스캔 횟수
    private String status;               // 상태 (정상, 비효율, 미사용, bloat)
    private OffsetDateTime createdAt;     // 생성 시각
    private String indexType;            // 인덱스 타입 (btree, hash, gin, gist 등)
    private Double avgBloatPercent;      // 평균 Bloat 비율(%)
    private Double avgScanTimeMs;        // 평균 스캔 시간(ms)
}