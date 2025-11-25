package com.dajanggan.domain.alarm.dto;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public class AlarmFeedDto {

    // ========== Raw DTOs ==========

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlarmListRaw {
        private Long alarmFeedId;
        private String alarmTitle;
        private String severityLevel;
        private OffsetDateTime occurredAt;
        private String description;
        private Boolean isRead;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LatencyDataRaw {
        private String hourLabel;
        private BigDecimal avgLatency;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelatedObjectRaw {
        private String objectType;  // table, index, schema
        private String objectName;
        private String metricValue;
        private String status;  // 위험, 경고, 주의, 정상
    }

    // ========== Response DTOs ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlarmItem {
        private Long id;
        private String title;
        private String severity;  // CRITICAL, WARNING, INFO
        private String occurredAt;
        private String description;
        private Boolean isRead;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LatencyData {
        private List<BigDecimal> data;
        private List<String> labels;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private String current;
        private String threshold;
        private String duration;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelatedItem {
        private String type;  // table, index, schema
        private String name;
        private String metric;
        private String level;  // 위험, 경고, 주의, 정상
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetailResponse {
        private Long id;
        private String title;
        private String severity;
        private String occurredAt;
        private String description;
        private LatencyData latency;
        private Summary summary;
        private List<RelatedItem> related;
        private Boolean isGenerating;  // 관련 객체 생성 중 여부 (프론트엔드 자동 폴링용)
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListResponse {
        private List<AlarmItem> alarms;
        private Integer totalCount;
        private Integer unreadCount;
    }
}