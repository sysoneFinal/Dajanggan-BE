package com.dajanggan.domain.alarm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * AlarmFeed DTO 모음
 *
 * 내부 클래스:
 * - AlarmListRaw: 목록 조회 Raw 데이터
 * - LatencyDataRaw: 레이턴시 데이터 Raw
 * - RelatedObjectRaw: 관련 객체 Raw 데이터
 * - AlarmItem: 알람 목록 아이템 (Response)
 * - LatencyData: 레이턴시 차트 데이터 (Response)
 * - Summary: 알람 요약 정보 (Response)
 * - RelatedItem: 관련 객체 아이템 (Response)
 * - DetailResponse: 알람 상세 응답
 * - ListResponse: 알람 목록 응답
 *
 *
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-13  김민서    1. 최초작성
 *
 */
public class AlarmFeedDto {

    // ========== Raw DTOs (Repository에서 조회) ==========

    /**
     * 알람 목록 조회 Raw 데이터
     *
     * 용도: MyBatis ResultMap 매핑
     */
    @Getter
    @Setter
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

    /**
     * 레이턴시 데이터 Raw
     *
     * 용도: 차트 데이터 조회
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LatencyDataRaw {
        private String hourLabel;
        private BigDecimal avgLatency;
    }

    /**
     * 관련 객체 Raw 데이터
     *
     * 용도: 알람 관련 세션/쿼리 조회
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelatedObjectRaw {
        private String objectType;
        private String objectName;
        private String metricValue;
        private String status;
    }

    // ========== Response DTOs (API 응답) ==========

    /**
     * 알람 목록 아이템
     *
     * 용도: GET /api/alarms/feeds
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AlarmItem {
        private Long id;
        private String title;
        private String severity;
        private String occurredAt;
        private String description;
        private Boolean isRead;
    }

    /**
     * 레이턴시 차트 데이터
     *
     * 용도: 알람 상세 화면 차트
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LatencyData {
        private List<BigDecimal> data;
        private List<String> labels;
    }

    /**
     * 알람 요약 정보
     *
     * 용도: 알람 상세 화면 요약
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Summary {
        private String current;
        private String threshold;
        private String duration;
    }

    /**
     * 관련 객체 아이템
     *
     * 용도: 알람과 관련된 세션/쿼리 표시
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RelatedItem {
        private String type;
        private String name;
        private String metric;
        private String level;
    }

    /**
     * 알람 상세 응답
     *
     * 용도: GET /api/alarms/feeds/{id}
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DetailResponse {
        private Long id;
        private String title;
        private String severity;
        private String occurredAt;
        private String description;
        private LatencyData latency;
        private Summary summary;
        private List<RelatedItem> related;
        private Boolean isGenerating;
    }

    /**
     * 알람 목록 응답
     *
     * 용도: GET /api/alarms/feeds
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ListResponse {
        @Builder.Default
        private List<AlarmItem> alarms = List.of();
        private Integer totalCount;
        private Integer unreadCount;
    }
}