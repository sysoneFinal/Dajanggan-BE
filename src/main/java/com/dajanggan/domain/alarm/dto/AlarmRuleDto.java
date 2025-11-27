package com.dajanggan.domain.alarm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * AlarmRule DTO 모음
 *
 *
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-13  김민서    1. 최초작성
 *
 */
public class AlarmRuleDto {

    // ========== Raw DTOs ==========

    @Getter
    @Setter
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

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThresholdLevel {
        @NotNull(message = "임계값은 필수입니다.")
        private BigDecimal threshold;

        @Min(value = 1, message = "최소 지속 시간은 1분 이상이어야 합니다.")
        private Integer minDurationMin;

        @Min(value = 1, message = "발생 횟수는 1회 이상이어야 합니다.")
        private Integer occurCount;

        @Min(value = 1, message = "윈도우 시간은 1분 이상이어야 합니다.")
        private Integer windowMin;

        private BigDecimal resolveThreshold;
        private Integer resolveDurationMin;
        private Integer cooldownMin;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Levels {
        private ThresholdLevel info;
        private ThresholdLevel warn;
        private ThresholdLevel critical;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        private Boolean enabled;

        @NotNull(message = "인스턴스 ID는 필수입니다.")
        private Long instanceId;

        @NotNull(message = "데이터베이스 ID는 필수입니다.")
        private Long databaseId;

        @NotBlank(message = "메트릭 타입은 필수입니다.")
        private String metricType;

        private String operator;

        @NotNull(message = "레벨 설정은 필수입니다.")
        private Levels levels;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private Long alarmRuleId;
        private Boolean enabled;
        private String operator;
        private Levels levels;
    }

    // ========== Response DTOs ==========

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RuleItem {
        private Long alarmRuleId;
        private Long instanceId;
        private Long databaseId;
        private String instanceName;
        private String databaseName;
        private String section;
        private String metricType;
        private Boolean enabled;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DetailResponse {
        private Long alarmRuleId;
        private Boolean enabled;
        private Long instanceId;
        private Long databaseId;
        private String instanceName;
        private String databaseName;
        private String section;
        private String metricType;
        private String updatedAt;
        private Levels levels;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ListResponse {
        @Builder.Default
        private List<RuleItem> rules = List.of();
        private Integer totalCount;
    }
}