package com.dajanggan.domain.query.dto.agg1m;

import lombok.*;

import java.time.OffsetDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class SlowQueryTrendDto {
    private OffsetDateTime collectedAt;
    private Integer slowQueryCount;
}