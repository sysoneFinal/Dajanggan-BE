package com.dajanggan.domain.engine.hottable.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * HotTable 집계 데이터 엔티티
 * 테이블: hot_table_agg
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotTableAgg {

    private Long hotTableAggId;          // PK
    private Long databaseId;             // 데이터베이스 ID
    private OffsetDateTime collectedAt;   // 수집 시각
    private String schemaName;           // 스키마명
    private String tableName;            // 테이블명
    private Long avgTableSize;           // 평균 테이블 크기
    private Long totalSeqScan;           // 총 Sequential Scan 횟수
    private Long totalSeqTupRead;        // 총 Sequential Scan 튜플 수
    private Long totalIdxScan;           // 총 Index Scan 횟수
    private Long totalIdxTupFetch;       // 총 Index Scan 튜플 수
    private Long totalTupIns;            // 총 Insert 튜플 수
    private Long totalTupUpd;            // 총 Update 튜플 수
    private Long totalTupDel;            // 총 Delete 튜플 수
    private Double avgDeadRatio;         // 평균 Dead 튜플 비율
    private Double avgCacheHitRatio;     // 평균 캐시 히트율
    private Double avgSeqScanRatio;      // 평균 Sequential Scan 비율
    private Long vacuumDelaySeconds;     // VACUUM 지연 시간(초)
    private String status;               // 상태
    private OffsetDateTime createdAt;     // 생성 시각
    private Long totalTupHotUpd;         // 총 HOT Update 수
    private Double avgBloatPercent;      // 평균 Bloat 비율(%)
}