package com.dajanggan.domain.instance.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class DatabaseDto {
    private Long databaseId;
    private Long instanceId;
    private String databaseName;
    private String status;
    private Integer connections;
    private String sizeBytes;
    private String cacheHitRate;
    private OffsetDateTime updatedAt;
}
