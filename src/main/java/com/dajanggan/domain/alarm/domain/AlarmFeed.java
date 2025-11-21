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
public class AlarmFeed {
    private Long alarmFeedId;
    private Long alarmRuleId;
    private Long alarmTrackingId;
    private Long instanceId;
    private Long databaseId;
    private String alarmTitle;
    private String severityLevel;
    private String metricType;
    private BigDecimal currentValue;
    private BigDecimal thresholdValue;
    private String message;
    private OffsetDateTime occurredAt;
    private OffsetDateTime resolvedAt;
    private Boolean isResolved;
    private Boolean isRead;
    private Boolean acknowledged;
    private OffsetDateTime acknowledgedAt;
}