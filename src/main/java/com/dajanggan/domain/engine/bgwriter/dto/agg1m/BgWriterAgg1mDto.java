package com.dajanggan.domain.engine.bgwriter.dto.agg1m;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * BGWriter 1분 집계 DTO (배치용)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BgWriterAgg1mDto {
    private Long instanceId;
    private OffsetDateTime collectedAt;
}






