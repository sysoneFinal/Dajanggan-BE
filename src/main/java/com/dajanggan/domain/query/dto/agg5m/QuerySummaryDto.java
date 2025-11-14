package com.dajanggan.domain.query.dto.agg5m;

import lombok.*;

import java.time.OffsetDateTime;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class QuerySummaryDto {
    private Long instanceId;
    private Long databaseId;
    private Integer totalQueries;
    private Double avgExecutionTimeMs;
    private Integer slowQueryCount;
    private OffsetDateTime createdAt;
}