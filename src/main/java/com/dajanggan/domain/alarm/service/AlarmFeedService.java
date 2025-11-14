package com.dajanggan.domain.alarm.service;

import com.dajanggan.domain.alarm.domain.AlarmFeed;
import com.dajanggan.domain.alarm.dto.AlarmFeedDto;
import com.dajanggan.domain.alarm.repository.AlarmFeedMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlarmFeedService {

    private final AlarmFeedMapper alarmFeedMapper;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 알림 목록 조회
     */
    public AlarmFeedDto.ListResponse getAlarmList(
            Long instanceId, Long databaseId, String severityLevel, Boolean isRead
    ) {
        List<AlarmFeedDto.AlarmListRaw> rawList = alarmFeedMapper.selectAlarmList(
                instanceId, databaseId, severityLevel, isRead);

        List<AlarmFeedDto.AlarmItem> alarms = rawList.stream()
                .map(raw -> AlarmFeedDto.AlarmItem.builder()
                        .id(raw.getAlarmFeedId())
                        .title(raw.getAlarmTitle())
                        .severity(raw.getSeverityLevel())
                        .occurredAt(raw.getOccurredAt().format(FORMATTER))
                        .description(raw.getDescription())
                        .isRead(raw.getIsRead())
                        .build())
                .collect(Collectors.toList());

        int unreadCount = (int) alarms.stream()
                .filter(a -> !a.getIsRead())
                .count();

        return AlarmFeedDto.ListResponse.builder()
                .alarms(alarms)
                .totalCount(alarms.size())
                .unreadCount(unreadCount)
                .build();
    }

    /**
     * 알림 상세 조회
     */
    public AlarmFeedDto.DetailResponse getAlarmDetail(Long alarmFeedId) {
        // 알림 기본 정보
        AlarmFeed feed = alarmFeedMapper.selectAlarmDetail(alarmFeedId);
        if (feed == null) {
            throw new IllegalArgumentException("알림을 찾을 수 없습니다: " + alarmFeedId);
        }

        // Latency 데이터 (24시간)
        List<AlarmFeedDto.LatencyDataRaw> latencyRaw =
                alarmFeedMapper.selectLatencyData(feed.getAlarmFeedId());

        List<BigDecimal> latencyData = latencyRaw.stream()
                .map(AlarmFeedDto.LatencyDataRaw::getAvgLatency)
                .collect(Collectors.toList());

        List<String> latencyLabels = latencyRaw.stream()
                .map(AlarmFeedDto.LatencyDataRaw::getHourLabel)
                .collect(Collectors.toList());

        AlarmFeedDto.LatencyData latency = AlarmFeedDto.LatencyData.builder()
                .data(latencyData)
                .labels(latencyLabels)
                .build();

        // 요약 정보
        AlarmFeedDto.Summary summary = AlarmFeedDto.Summary.builder()
                .current(formatValue(feed.getCurrentValue()))
                .threshold(formatValue(feed.getThresholdValue()))
                .duration("15m")
                .build();

        // 관련 객체
        List<AlarmFeedDto.RelatedObjectRaw> relatedRaw =
                alarmFeedMapper.selectRelatedObjects(feed.getAlarmFeedId());

        List<AlarmFeedDto.RelatedItem> related = relatedRaw.stream()
                .map(raw -> AlarmFeedDto.RelatedItem.builder()
                        .type(raw.getObjectType())
                        .name(raw.getObjectName())
                        .metric(raw.getMetricValue())
                        .level(raw.getStatus())
                        .build())
                .collect(Collectors.toList());

        return AlarmFeedDto.DetailResponse.builder()
                .id(feed.getAlarmFeedId())
                .title(feed.getAlarmTitle())
                .severity(feed.getSeverityLevel())
                .occurredAt(feed.getOccurredAt().format(FORMATTER))
                .description(feed.getMessage())
                .latency(latency)
                .summary(summary)
                .related(related)
                .build();
    }

    /**
     * 알림 읽음 처리
     */
    @Transactional
    public void markAsRead(Long alarmFeedId) {
        int updated = alarmFeedMapper.updateAlarmRead(alarmFeedId);
        if (updated == 0) {
            throw new IllegalArgumentException("알림을 찾을 수 없습니다: " + alarmFeedId);
        }
    }

    /**
     * 알림 확인 처리
     */
    @Transactional
    public void acknowledgeAlarm(Long alarmFeedId) {
        int updated = alarmFeedMapper.updateAlarmAcknowledged(alarmFeedId);
        if (updated == 0) {
            throw new IllegalArgumentException("알림을 찾을 수 없습니다: " + alarmFeedId);
        }
    }

    /**
     * 알림 삭제
     */
    @Transactional
    public void deleteAlarm(Long alarmFeedId) {
        int deleted = alarmFeedMapper.deleteAlarm(alarmFeedId);
        if (deleted == 0) {
            throw new IllegalArgumentException("알림을 찾을 수 없습니다: " + alarmFeedId);
        }
    }

    /**
     * 미확인 알림 개수
     */
    public int getUnreadCount(Long instanceId) {
        return alarmFeedMapper.countUnreadAlarms(instanceId);
    }

    /**
     * 값 포맷팅 (K, M 단위)
     */
    private String formatValue(BigDecimal value) {
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
