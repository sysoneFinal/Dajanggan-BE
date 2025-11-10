package com.dajanggan.domain.engine.hottable.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HotTableRaw {
    private Long hotTableRawId;
    private Long databaseId;
    private LocalDateTime collectedAt;
    private String schemaName;
    private String tableName;
    private Long nDeadTup;
    private Long nLiveTup;
}
