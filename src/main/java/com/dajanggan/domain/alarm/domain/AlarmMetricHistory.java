package com.dajanggan.domain.alarm.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * AlarmMetricHistory 엔티티
 *
 * 테이블: alarm_metric_history
 *
 * 주요 책임:
 * - 알람 메트릭 이력 관리
 * - 차트 데이터 제공
 * - 시계열 데이터 저장
 *
 * 관계:
 * - AlarmFeed (N:1) - 어떤 알람 피드의 이력인지
 *
 *
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-13  김민서    1. 최초작성
 *
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmMetricHistory {

    private Long alarmMetricHistoryId;
    private Long alarmFeedId;
    private String metricValue;
    private OffsetDateTime recordedAt;
    private OffsetDateTime createdAt;

    // ========== 비즈니스 로직 메서드 ==========

    /**
     * 메트릭 값을 숫자로 변환
     *
     * @return 숫자로 변환된 값, 실패 시 0.0
     */
    public double getMetricValueAsDouble() {
        if (metricValue == null || metricValue.isEmpty()) {
            return 0.0;
        }

        try {
            return Double.parseDouble(metricValue);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * 기록 시간이 특정 시간 이후인지 확인
     *
     * @param dateTime 비교할 시간
     * @return 기록 시간이 이후이면 true
     */
    public boolean isRecordedAfter(OffsetDateTime dateTime) {
        if (recordedAt == null || dateTime == null) {
            return false;
        }
        return recordedAt.isAfter(dateTime);
    }

    /**
     * 기록 시간이 특정 시간 이전인지 확인
     *
     * @param dateTime 비교할 시간
     * @return 기록 시간이 이전이면 true
     */
    public boolean isRecordedBefore(OffsetDateTime dateTime) {
        if (recordedAt == null || dateTime == null) {
            return false;
        }
        return recordedAt.isBefore(dateTime);
    }

    /**
     * 특정 시간 범위 내에 있는지 확인
     *
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 범위 내에 있으면 true
     */
    public boolean isWithinRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        if (recordedAt == null || startTime == null || endTime == null) {
            return false;
        }
        return recordedAt.isAfter(startTime) && recordedAt.isBefore(endTime);
    }
}