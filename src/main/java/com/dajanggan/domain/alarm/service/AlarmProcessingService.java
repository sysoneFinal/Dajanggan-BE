package com.dajanggan.domain.alarm.service;

import com.dajanggan.domain.alarm.domain.AlarmFeed;
import com.dajanggan.domain.alarm.domain.AlarmRule;
import com.dajanggan.domain.alarm.domain.AlarmTracking;
import com.dajanggan.domain.alarm.repository.AlarmFeedMapper;
import com.dajanggan.domain.alarm.repository.AlarmTrackingMapper;
import com.dajanggan.domain.alarm.repository.AlarmRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * AlarmProcessing 서비스
 *
 * 주요 책임:
 * - 알람 발생 처리 (Feed 생성)
 * - 알람 해제 처리
 * - 메트릭 히스토리 저장
 * - Slack 알림 전송
 *
 * <pre>
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-14  김민서    1. 최초작성
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmProcessingService {

    private final AlarmTrackingMapper alarmTrackingMapper;
    private final AlarmFeedMapper alarmFeedMapper;
    private final AlarmRuleMapper alarmRuleMapper;
    private final AlarmThresholdService thresholdService;
    private final SlackNotificationService slackNotificationService;

    /**
     * 알람 발생 처리
     *
     * @param alarmRuleId 알람 규칙 ID
     * @param alarmTrackingId 알람 추적 ID
     * @param currentValue 현재 값
     * @param level 레벨 (INFO, WARN, CRITICAL)
     * @param metricType 메트릭 타입
     * @param isLevelChange 레벨 변경 여부
     */
    @Transactional
    public void fireAlarm(
            Long alarmRuleId,
            Long alarmTrackingId,
            BigDecimal currentValue,
            String level,
            String metricType,
            boolean isLevelChange
    ) {
        // 1. Rule, Tracking 조회
        AlarmRule rule = alarmRuleMapper.selectRuleDetail(alarmRuleId);
        if (rule == null) {
            log.error("알람 규칙을 찾을 수 없습니다: {}", alarmRuleId);
            return;
        }

        AlarmTracking tracking = alarmTrackingMapper.selectById(alarmTrackingId);
        if (tracking == null) {
            log.error("알람 추적을 찾을 수 없습니다: {}", alarmTrackingId);
            return;
        }

        // 2. 중복 Feed 체크
        if (!isLevelChange) {
            Long existingFeedId = alarmFeedMapper.selectUnresolvedFeedIdByRule(
                    alarmRuleId,
                    tracking.getInstanceId(),
                    tracking.getDatabaseId(),
                    level
            );

            if (existingFeedId != null) {
                log.warn("이미 존재하는 Feed: {}", existingFeedId);
                saveMetricHistory(existingFeedId, currentValue);
                updateTrackingStatus(tracking, "FIRED");
                return;
            }
        }

        // 3. Feed 생성
        BigDecimal thresholdValue = thresholdService.getThresholdForLevel(
                rule.getLevels(), level.toLowerCase());

        AlarmFeed feed = AlarmFeed.builder()
                .alarmRuleId(alarmRuleId)
                .alarmTrackingId(alarmTrackingId)
                .instanceId(tracking.getInstanceId())
                .databaseId(tracking.getDatabaseId())
                .alarmTitle(metricType + " 임계치 초과 (" + level + ")")
                .severityLevel(level)
                .metricType(metricType)
                .currentValue(currentValue)
                .thresholdValue(thresholdValue)
                .message(String.format("%s가 임계치를 초과했습니다 (현재: %s, 임계치: %s)",
                        metricType, currentValue, thresholdValue))
                .occurredAt(OffsetDateTime.now())
                .isResolved(false)
                .isRead(false)
                .acknowledged(false)
                .build();

        alarmFeedMapper.insertAlarmFeed(feed);

        Long feedId = feed.getAlarmFeedId();
        if (feedId == null) {
            log.error("AlarmFeed 생성 실패");
            throw new RuntimeException("AlarmFeed 생성 실패");
        }

        // 4. Tracking 상태 업데이트
        updateTrackingStatus(tracking, "FIRED");

        // 5. 메트릭 히스토리 저장
        saveMetricHistory(feedId, currentValue);

        // 6. Slack 알림 전송
        sendSlackNotification(tracking.getInstanceId(), metricType, level, currentValue, thresholdValue, tracking.getConsecutiveCount());

        log.warn("🚨 알람 발생: ruleId={}, trackingId={}, feedId={}, level={}",
                alarmRuleId, alarmTrackingId, feedId, level);
    }

    /**
     * 알람 해제 처리
     *
     * @param alarmTrackingId 알람 추적 ID
     * @param metricType 메트릭 타입
     */
    @Transactional
    public void resolveAlarm(Long alarmTrackingId, String metricType) {
        log.info("✅ 알람 해제: trackingId={}, metric={}", alarmTrackingId, metricType);

        AlarmTracking tracking = alarmTrackingMapper.selectById(alarmTrackingId);
        if (tracking == null) {
            log.warn("알람 추적을 찾을 수 없습니다: {}", alarmTrackingId);
            return;
        }

        Long latestFeedId = alarmFeedMapper.selectLatestFeedIdByTrackingId(alarmTrackingId);
        if (latestFeedId != null) {
            alarmFeedMapper.resolveByFeedId(latestFeedId);
        }

        updateTrackingStatus(tracking, "RESOLVED");
    }

    /**
     * 메트릭 히스토리 저장
     *
     * @param alarmFeedId 알람 피드 ID
     * @param value 메트릭 값
     */
    public void saveMetricHistory(Long alarmFeedId, BigDecimal value) {
        if (alarmFeedId == null) {
            log.warn("alarmFeedId가 null입니다");
            return;
        }

        try {
            alarmFeedMapper.insertMetricHistory(
                    alarmFeedId,
                    value,
                    OffsetDateTime.now()
            );
        } catch (Exception e) {
            log.error("메트릭 히스토리 저장 실패: feedId={}", alarmFeedId, e);
        }
    }

    /**
     * Tracking 상태 업데이트
     */
    private void updateTrackingStatus(AlarmTracking tracking, String status) {
        tracking.setStatus(status);
        tracking.setLastCheckedAt(OffsetDateTime.now());
        alarmTrackingMapper.updateTracking(tracking);
    }

    /**
     * Slack 알림 전송
     */
    private void sendSlackNotification(
            Long instanceId,
            String metricType,
            String level,
            BigDecimal currentValue,
            BigDecimal threshold,
            Integer consecutiveCount
    ) {
        try {
            String description = String.format(
                    "%s가 임계치를 초과했습니다.\n• 현재값: %s\n• 임계치: %s\n• 발생 횟수: %d회",
                    metricType, currentValue, threshold, consecutiveCount
            );

            slackNotificationService.sendAlarmNotification(
                    instanceId,
                    metricType + " 임계치 초과",
                    level,
                    description,
                    "Instance-" + instanceId,
                    "Database"
            );
        } catch (Exception e) {
            log.error("Slack 알림 전송 실패: instanceId={}", instanceId, e);
        }
    }
}