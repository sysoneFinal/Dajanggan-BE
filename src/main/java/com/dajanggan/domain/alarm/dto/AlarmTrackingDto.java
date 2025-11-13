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
        private String instanceName;
        private String databaseName;
        private String metricType;
        private BigDecimal currentValue;
        private Integer consecutiveCount;
        private String status;
        private OffsetDateTime lastCheckedAt;
    }

    // ========== Response DTOs ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackingStatus {
        private Long trackingId;
        private Long ruleId;
        private String instance;
        private String database;
        private String metric;
        private String currentValue;
        private Integer consecutiveCount;
        private String status;
        private String lastChecked;
    }
}