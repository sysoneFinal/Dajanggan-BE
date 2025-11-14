package com.dajanggan.domain.query.dto.agg1m;

import lombok.*;

import java.time.OffsetDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class QueryTypeTrendDto {
    private OffsetDateTime collectedAt;
    private Integer selectQueries;
    private Integer insertQueries;
    private Integer updateQueries;
    private Integer deleteQueries;
}