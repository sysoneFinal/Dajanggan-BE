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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AlarmFeed 서비스
 *
 * 주요 책임:
 * - 알람 피드 목록 조회
 * - 알람 상세 조회
 * - 알람 읽음 처리
 * - 알람 삭제
 * - 미확인 알람 개수 조회
 *
 * <pre>
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-13  김민서    1. 최초작성
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlarmFeedService {

    private final AlarmFeedMapper alarmFeedMapper;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 알람 목록 조회
     *
     * @param instanceId 인스턴스 ID (선택)
     * @param databaseId 데이터베이스 ID (선택)
     * @param severityLevel 심각도 (선택)
     * @param isRead 읽음 여부 (선택)
     * @return 알람 목록 응답 DTO
     */
    public AlarmFeedDto.ListResponse getAlarmList(
            Long instanceId,
            Long databaseId,
            String severityLevel,
            Boolean isRead
    ) {
        log.debug("알람 목록 조회: instanceId={}, databaseId={}, severity={}, isRead={}",
                instanceId, databaseId, severityLevel, isRead);

        // Raw 데이터 조회
        List<AlarmFeedDto.AlarmListRaw> rawList = alarmFeedMapper.selectAlarmList(
                instanceId, databaseId, severityLevel, isRead);

        // DTO 변환
        List<AlarmFeedDto.AlarmItem> alarms = rawList.stream()
                .map(raw -> AlarmFeedDto.AlarmItem.builder()
                        .id(raw.getAlarmFeedId())
                        .title(raw.getAlarmTitle())
                        .severity(raw.getSeverityLevel())
                        .occurredAt(raw.getOccurredAt() != null
                                ? raw.getOccurredAt().format(FORMATTER)
                                : null)
                        .description(raw.getDescription())
                        .isRead(raw.getIsRead())
                        .build())
                .collect(Collectors.toList());

        // 미확인 개수 계산
        int unreadCount = (int) alarms.stream()
                .filter(alarm -> !Boolean.TRUE.equals(alarm.getIsRead()))
                .count();

        return AlarmFeedDto.ListResponse.builder()
                .alarms(alarms)
                .totalCount(alarms.size())
                .unreadCount(unreadCount)
                .build();
    }

    /**
     * 알람 상세 조회
     *
     * @param alarmFeedId 알람 피드 ID
     * @return 알람 상세 응답 DTO
     */
    public AlarmFeedDto.DetailResponse getAlarmDetail(Long alarmFeedId) {
        log.debug("알람 상세 조회: alarmFeedId={}", alarmFeedId);

        // 1. 알람 기본 정보 조회
        AlarmFeed feed = alarmFeedMapper.selectAlarmDetail(alarmFeedId);
        if (feed == null) {
            throw new IllegalArgumentException("알람을 찾을 수 없습니다: " + alarmFeedId);
        }

        // 2. 레이턴시 데이터 조회
        List<AlarmFeedDto.LatencyDataRaw> latencyRaw =
                alarmFeedMapper.selectLatencyData(alarmFeedId);

        List<BigDecimal> dataPoints = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (AlarmFeedDto.LatencyDataRaw raw : latencyRaw) {
            dataPoints.add(raw.getAvgLatency());
            labels.add(raw.getHourLabel());
        }

        AlarmFeedDto.LatencyData latencyData = AlarmFeedDto.LatencyData.builder()
                .data(dataPoints)
                .labels(labels)
                .build();

        // 3. 요약 정보 생성
        AlarmFeedDto.Summary summary = AlarmFeedDto.Summary.builder()
                .current(formatValue(feed.getCurrentValue()))
                .threshold(formatValue(feed.getThresholdValue()))
                .duration(calculateDuration(feed))
                .build();

        // 4. 관련 객체 조회
        List<AlarmFeedDto.RelatedObjectRaw> relatedRaw =
                alarmFeedMapper.selectRelatedObjects(alarmFeedId);

        List<AlarmFeedDto.RelatedItem> relatedItems = relatedRaw.stream()
                .map(raw -> AlarmFeedDto.RelatedItem.builder()
                        .type(raw.getObjectType())
                        .name(raw.getObjectName())
                        .metric(raw.getMetricValue())
                        .level(raw.getStatus())
                        .build())
                .collect(Collectors.toList());

        // 5. 응답 DTO 생성
        return AlarmFeedDto.DetailResponse.builder()
                .id(feed.getAlarmFeedId())
                .title(feed.getAlarmTitle())
                .severity(feed.getSeverityLevel())
                .occurredAt(feed.getOccurredAt() != null
                        ? feed.getOccurredAt().format(FORMATTER)
                        : null)
                .description(feed.getMessage())
                .latency(latencyData)
                .summary(summary)
                .related(relatedItems)
                .isGenerating(false)
                .build();
    }

    /**
     * 알람 읽음 처리
     *
     * @param alarmFeedId 알람 피드 ID
     */
    @Transactional
    public void markAsRead(Long alarmFeedId) {
        log.info("알람 읽음 처리: alarmFeedId={}", alarmFeedId);

        int updated = alarmFeedMapper.markAsRead(alarmFeedId);
        if (updated == 0) {
            throw new IllegalArgumentException("알람을 찾을 수 없습니다: " + alarmFeedId);
        }
    }

    /**
     * 알람 삭제
     *
     * @param alarmFeedId 알람 피드 ID
     */
    @Transactional
    public void deleteAlarm(Long alarmFeedId) {
        log.info("알람 삭제: alarmFeedId={}", alarmFeedId);

        int deleted = alarmFeedMapper.deleteAlarm(alarmFeedId);
        if (deleted == 0) {
            throw new IllegalArgumentException("알람을 찾을 수 없습니다: " + alarmFeedId);
        }
    }

    /**
     * 미확인 알람 개수 조회
     *
     * @param instanceId 인스턴스 ID
     * @return 미확인 알람 개수
     */
    public int getUnreadCount(Long instanceId) {
        log.debug("미확인 알람 개수 조회: instanceId={}", instanceId);

        return alarmFeedMapper.countUnreadAlarms(instanceId);
    }

    // ========== Private Helper 메서드 ==========

    /**
     * BigDecimal 값을 포맷팅
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

    /**
     * 지속 시간 계산
     */
    private String calculateDuration(AlarmFeed feed) {
        if (feed.getOccurredAt() == null) return "0분";

        java.time.Duration duration = java.time.Duration.between(
                feed.getOccurredAt(),
                feed.getResolvedAt() != null
                        ? feed.getResolvedAt()
                        : java.time.OffsetDateTime.now()
        );

        long minutes = duration.toMinutes();
        if (minutes >= 60) {
            return (minutes / 60) + "시간 " + (minutes % 60) + "분";
        }
        return minutes + "분";
    }
}
