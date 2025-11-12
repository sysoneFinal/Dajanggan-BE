package com.dajanggan.domain.engine.hotindex.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * HotIndex Raw 데이터 엔티티
 * 테이블: hot_index_raw
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotIndexRaw {

    private Long hotIndexRawId;          // PK
    private Long databaseId;             // 데이터베이스 ID
    private LocalDateTime collectedAt;   // 수집 시각
    private String schemaName;           // 스키마명
    private String tableName;            // 테이블명
    private String indexName;            // 인덱스명
    private Long indexSize;              // 인덱스 크기
    private Long idxScan;                // Index Scan 횟수
    private Long idxTupRead;             // Index Scan으로 읽은 튜플 수
    private Long idxTupFetch;            // Index Scan으로 가져온 튜플 수
    private Long idxBlksRead;            // Index Block Read 수
    private Long idxBlksHit;             // Index Block Hit 수
    private LocalDateTime lastIdxScan;   // 마지막 Index Scan 시각
    private LocalDateTime createdAt;     // 생성 시각
    private String indexType;            // 인덱스 타입 (btree, hash, gin, gist 등)
    private Double bloatPercent;         // Bloat 비율(%)
    private Double avgScanTimeMs;        // 평균 스캔 시간(ms)
}