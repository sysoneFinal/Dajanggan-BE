package com.dajanggan.domain.alarm.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlarmRule {
    private Long alarmRuleId;
    private Long databaseId;
    private Long instanceId;
    private String instanceName;
    private String databaseName;
    private String metricType;    // dead_tuples, bloat_pct, vacuum_backlog, wal_lag
    private String aggregationType; // latest_avg, avg_5m, avg_15m, p95_15m
    private String operator;      // >, >=, <, <=, =
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Boolean enabled;

    private String levels;
}