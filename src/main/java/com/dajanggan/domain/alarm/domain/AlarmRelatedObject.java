package com.dajanggan.domain.alarm.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlarmRelatedObject {
    private Long alarmRelatedObjectId;
    private Long alarmFeedId;
    private Long alarmRuleId;
    private String objectName;
    private BigDecimal metricValue;
    private String status;  // 위험, 경고, 주의, 정상
}