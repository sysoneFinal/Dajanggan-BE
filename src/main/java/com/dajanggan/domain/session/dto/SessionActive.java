package com.dajanggan.domain.session.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class SessionActive {

    private Long sessionMetricId;
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
    private BigDecimal waitDurationSec;
    private Integer blockingPid;
    private OffsetDateTime xactStart;
    private String queryType;
    private String query;
    private OffsetDateTime queryStart;
    private BigDecimal queryAgeSec;
    private BigDecimal cpuUsage;
    private BigDecimal memoryUsageMb;
    private String lockType;
    private BigDecimal lockDurationMs;
    private Boolean isDeadlock;
    private OffsetDateTime createdAt;

}
