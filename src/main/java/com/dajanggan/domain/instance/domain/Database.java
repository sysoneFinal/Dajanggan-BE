package com.dajanggan.domain.instance.domain;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class Database {
    private Long databaseId;
    private Long instanceId;
    private String databaseName;
    private Boolean isEnabled;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Integer connections;
    private String sizeBytes;
    private String cacheHitRate;
}
