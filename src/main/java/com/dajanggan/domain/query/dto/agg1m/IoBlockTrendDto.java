package com.dajanggan.domain.query.dto.agg1m;

import lombok.*;

import java.time.OffsetDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class IoBlockTrendDto {
    private OffsetDateTime collectedAt;
    private Double avgIoBlocks;
}