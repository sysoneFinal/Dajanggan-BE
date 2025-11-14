package com.dajanggan.domain.alarm.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlarmMetricHistory {
    private Long alarmMetricHistoryId;
    private Long alarmFeedId;
    private String metricValue;
    private OffsetDateTime recordedAt;
    private OffsetDateTime createdAt;
}