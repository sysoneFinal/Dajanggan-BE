package com.dajanggan.domain.alarm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * AlarmTracking DTO 모음
 *
 *
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-13  김민서    1. 최초작성
 *
 */
public class AlarmTrackingDto {

    // ========== Raw DTOs ==========

    @Getter
    @Setter
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

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
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