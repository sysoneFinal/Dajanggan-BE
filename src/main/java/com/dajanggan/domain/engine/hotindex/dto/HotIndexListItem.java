package com.dajanggan.domain.engine.hotindex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HotIndex 리스트 아이템 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotIndexListItem {
    private String id;
    private String indexName;
    private String tableName;
    private String schemaName;
    private String indexType;
    private String size;
    private Long idxScan;
    private Long idxTupRead;
    private Long idxTupFetch;
    private Double cacheHit;
    private Double bloatPercent;
    private Double avgScanTime;
    private String lastUsed;
    private String status;
}



