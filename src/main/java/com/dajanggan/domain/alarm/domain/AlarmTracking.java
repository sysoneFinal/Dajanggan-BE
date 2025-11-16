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
public class AlarmTracking {
    private Long alarmTrackingId;
    private Long alarmRuleId;
    private Long instanceId;
    private Long databaseId;
    private OffsetDateTime firstTriggeredAt;
    private OffsetDateTime lastCheckedAt;
    private Integer consecutiveCount;
    private BigDecimal currentValue;
    private String currentLevel;
    private String status;  // PENDING_FIRED, FIRED, RESOLVED
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}