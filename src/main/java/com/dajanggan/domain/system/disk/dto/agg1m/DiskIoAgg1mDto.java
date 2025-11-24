package com.dajanggan.domain.system.disk.dto.agg1m;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Disk I/O 1분 집계 DTO (배치용)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiskIoAgg1mDto {
    private Long instanceId;
    private OffsetDateTime collectedAt;
    private String backendType;
    private String databaseName;
}






