package com.dajanggan.domain.engine.hottable.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HotTableAgg {
    private Long hotTableAggId;
    private Long databaseId;
    private LocalDateTime collectedAt;
    private String schemaName;
    private String tableName;
    private Long totalSeqScan;
    private Long totalIdxScan;
    private Long totalTupIns;
    private Long totalTupUpd;
    private Long totalTupDel;
    private Double avgDeadRatio;
    private Double avgCacheHitRatio;
    private Double avgSeqScanRatio;
    private Long vacuumDelaySeconds;
    private Double avgBloatPercent;
}
