package com.dajanggan.domain.session.dto.raw;

import lombok.*;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionRawMetricDto {

    private Long databaseId;
    private OffsetDateTime collectedAt;

    private Integer pid;
    private String username;
    private String databasename;
    private String clientAddr;
    private String applicationName;
    private String state;

    private String waitEventType;
    private String waitEvent;
    private String waitClass;
    private String bottleneckCause;

    private String impactLevel;
    private Double waitDurationSec;
    private Integer blockingPid;

    private String queryType;
    private String query;
    private OffsetDateTime queryStart;
    private Double queryAgeSec;

    private Double cpuUsage;
    private Double memoryUsageMb;

    private String lockType;
    private Double lockDurationMs;

    private Boolean isDeadlock;
    private String tableName;

    private OffsetDateTime createdAt;
    private Long instanceId;
}
