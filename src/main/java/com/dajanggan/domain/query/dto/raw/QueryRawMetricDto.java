package com.dajanggan.domain.query.dto.raw;

import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryRawMetricDto {

    private Long databaseId;
    private Long instanceId;
    private OffsetDateTime collectedAt;

    private String queryId;
    private String queryHash;
    private String queryText;
    private String shortQuery;
    private String queryType;

    private Integer executionCount;
    private Long ioBlocks;

    private BigDecimal planningTimeMs;
    private BigDecimal executionTimeMs;
    private BigDecimal cpuUsagePercent;
    private BigDecimal memoryUsageMb;

    private String username;
    private String applicationName;
    private String clientAddr;
    private String databasename;
    private String state;

    private OffsetDateTime createdAt;
}