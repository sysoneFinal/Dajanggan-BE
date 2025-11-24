package com.dajanggan.domain.engine.checkpoint.dto.agg1m;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Checkpoint 1분 집계 DTO (배치용)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckpointAgg1mDto {
    private Long instanceId;
    private OffsetDateTime collectedAt;
}






