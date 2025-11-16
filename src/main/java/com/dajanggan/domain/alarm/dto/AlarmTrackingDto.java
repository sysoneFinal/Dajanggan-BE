package com.dajanggan.domain.alarm.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class AlarmTrackingDto {

    // ========== Raw DTOs ==========

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackingStatusRaw {
        private Long alarmTrackingId;
        private Long alarmRuleId;
        private Long alarmFeedId;
        private Long instanceId;
        private Long databaseId;
        private String instanceName;
        private String databaseName;
        private String metricType;
        private BigDecimal currentValue;
        private String currentLevel;
        private Integer consecutiveCount;
        private String status;
        private OffsetDateTime firstTriggeredAt;
        private OffsetDateTime lastCheckedAt;
    }

    // ========== Response DTOs ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackingStatus {
        private Long alarmTrackingId;
        private Long alarmRuleId;
        private String instanceName;
        private String databaseName;
        private String metricType;
        private String currentValue;
        private String currentLevel;
        private Integer consecutiveCount;
        private String status;
        private String lastChecked;
    }
}