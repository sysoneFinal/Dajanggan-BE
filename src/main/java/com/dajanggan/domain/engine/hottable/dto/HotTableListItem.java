package com.dajanggan.domain.engine.hottable.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HotTable 리스트 아이템 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotTableListItem {
    private String id;
    private String tableName;
    private String schemaName;
    private String size;
    private Long seqScan;
    private Long seqTupRead;
    private Long idxScan;
    private Long idxTupFetch;
    private Long nTupIns;
    private Long nTupUpd;
    private Long nTupDel;
    private Long nTupHotUpd;
    private Long nLiveTup;
    private Long nDeadTup;
    private Double bloatPercent;
    private String lastVacuum;
    private String lastAutoVacuum;
    private Double cacheHit;
    private String status;
}



