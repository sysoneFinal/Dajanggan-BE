package com.dajanggan.domain.alarm.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

public class AlarmRuleDto {

    // ========== Raw DTOs ==========

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleListRaw {
        private Long alarmRuleId;
        private Long instanceId;
        private Long databaseId;
        private String instanceName;
        private String databaseName;
        private String section;
        private String metricType;
        private Boolean enabled;
    }

    // ========== Request DTOs ==========

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThresholdLevel {
        private BigDecimal threshold;
        private Integer minDurationMin;
        private Integer occurCount;
        private Integer windowMin;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Levels {
        private ThresholdLevel notice;
        private ThresholdLevel warning;
        private ThresholdLevel critical;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        private Boolean enabled;
        private Long targetInstanceId;
        private Long targetDatabaseId;
        private String metricType;
        private String aggregationType;
        private Levels levels;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private Long alarmRuleId;
        private Boolean enabled;
        private String aggregationType;
        private Levels levels;
    }

    // ========== Response DTOs ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleItem {
        private Long alarmRuleId;
        private Long targetInstanceId;
        private Long targetDatabaseId;
        private String instanceName;
        private String databaseName;
        private String section;
        private String metricType;
        private Boolean enabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetailResponse {
        private Long alarmRuleId;
        private Boolean enabled;
        private Long targetInstanceId;
        private Long targetDatabaseId;
        private String instanceName;
        private String databaseName;
        private String section;
        private String metricType;
        private String aggregationType;
        private String updatedAt;
        private Levels levels;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListResponse {
        private List<RuleItem> rules;
        private Integer totalCount;
    }
}