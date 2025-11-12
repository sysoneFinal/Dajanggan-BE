package com.dajanggan.domain.alarm.service;

import com.dajanggan.domain.alarm.dto.AlarmTrackingDto;
import com.dajanggan.domain.alarm.repository.AlarmTrackingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlarmTrackingService {

    private final AlarmTrackingMapper alarmTrackingMapper;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 트래킹 상태 조회
     */
    public List<AlarmTrackingDto.TrackingStatus> getTrackingStatus(Long instanceId, String status) {
        List<AlarmTrackingDto.TrackingStatusRaw> rawList =
                alarmTrackingMapper.selectTrackingStatus(instanceId, status);

        return rawList.stream()
                .map(raw -> AlarmTrackingDto.TrackingStatus.builder()
                        .trackingId(raw.getAlarmTrackingId())
                        .ruleId(raw.getAlarmRuleId())
                        .instance(raw.getInstanceName())
                        .database(raw.getDatabaseName())
                        .metric(raw.getMetricType())
                        .currentValue(formatValue(raw.getCurrentValue()))
                        .consecutiveCount(raw.getConsecutiveCount())
                        .status(raw.getStatus())
                        .lastChecked(raw.getLastCheckedAt().format(FORMATTER))
                        .build())
                .collect(Collectors.toList());
    }

    private String formatValue(java.math.BigDecimal value) {
        if (value == null) return "0";

        double val = value.doubleValue();
        if (val >= 1_000_000) {
            return String.format("%.1fM", val / 1_000_000);
        } else if (val >= 1_000) {
            return String.format("%.1fK", val / 1_000);
        }
        return String.valueOf(val);
    }
}