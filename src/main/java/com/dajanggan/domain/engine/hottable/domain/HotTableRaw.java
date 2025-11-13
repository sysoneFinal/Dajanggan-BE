package com.dajanggan.domain.engine.hottable.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * HotTable Raw 데이터 엔티티
 * 테이블: hot_table_raw
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotTableRaw {

    private Long hotTableRawId;          // PK
    private Long databaseId;             // 데이터베이스 ID
    private LocalDateTime collectedAt;   // 수집 시각
    private String schemaName;           // 스키마명
    private String tableName;            // 테이블명
    private Long tableSize;              // 테이블 크기
    private Long seqScan;                // Sequential Scan 횟수
    private Long seqTupRead;             // Sequential Scan으로 읽은 튜플 수
    private Long idxScan;                // Index Scan 횟수
    private Long idxTupFetch;            // Index Scan으로 가져온 튜플 수
    private Long nTupIns;                // Insert된 튜플 수
    private Long nTupUpd;                // Update된 튜플 수
    private Long nTupDel;                // Delete된 튜플 수
    private Long nLiveTup;               // Live 튜플 수
    private Long nDeadTup;               // Dead 튜플 수
    private Long heapBlksRead;           // Heap Block Read 수
    private Long heapBlksHit;            // Heap Block Hit 수
    private LocalDateTime lastVacuum;    // 마지막 VACUUM 시각
    private LocalDateTime lastAutovacuum;// 마지막 Auto VACUUM 시각
    private LocalDateTime createdAt;     // 생성 시각
    private Long nTupHotUpd;             // HOT Update 수
    private Double bloatPercent;         // Bloat 비율(%)
}