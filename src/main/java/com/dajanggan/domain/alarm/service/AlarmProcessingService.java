package com.dajanggan.domain.alarm.service;

import com.dajanggan.domain.alarm.config.MetricConfig;
import com.dajanggan.domain.alarm.domain.AlarmFeed;
import com.dajanggan.domain.alarm.domain.AlarmRule;
import com.dajanggan.domain.alarm.domain.AlarmTracking;
import com.dajanggan.domain.alarm.repository.AlarmFeedMapper;
import com.dajanggan.domain.alarm.repository.AlarmTrackingMapper;
import com.dajanggan.domain.alarm.service.util.AlarmLevelParser;
import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.repository.DatabaseRepository;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;

/**
 * 알람 처리 서비스
 * - Feed 생성 (알람 발생)
 * - 알람 해제 (복구)
 * - 메트릭 히스토리 저장
 * - 관련 객체 저장
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlarmProcessingService {

    private final AlarmTrackingMapper alarmTrackingMapper;
    private final AlarmFeedMapper alarmFeedMapper;
    private final MetricConfig metricConfig;
    private final SlackNotificationService slackNotificationService;
    private final InstanceRepository instanceRepository;
    private final DatabaseRepository databaseRepository;
    private final AlarmLevelParser parser;

    /**
     * 알람 발생(실제 FIRED 처리)
     * - Feed를 생성하고 metric history / related objects를 feed 기준으로 저장
     * - tracking.status는 FIRED로 업데이트
     */
    @Transactional
    public void fireAlarm(
            Connection conn,
            AlarmRule rule,
            AlarmTracking tracking,
            BigDecimal currentValue,
            String level,
            String metricType,
            boolean isLevelChange
    ) {
        if (tracking == null) {
            log.error("fireAlarm 호출 시 tracking이 null 입니다. ruleId={}", rule.getAlarmRuleId());
            return;
        }

        try {
            // 중복 Feed 생성 방지 강화: rule 기준으로 체크 (동시성 문제 방지)
            if (!isLevelChange) {
                Long existingFeedId = alarmFeedMapper.selectUnresolvedFeedIdByRule(
                        rule.getAlarmRuleId(),
                        tracking.getInstanceId(),
                        tracking.getDatabaseId(),
                        level
                );

                if (existingFeedId != null) {
                    log.warn("fireAlarm 내부 중복 체크: 같은 ruleId, instanceId, databaseId, level의 해결되지 않은 Feed가 이미 존재합니다. feedId={}, ruleId={}, level={}",
                            existingFeedId, rule.getAlarmRuleId(), level);
                    // 기존 Feed에 메트릭 히스토리만 저장하고 종료
                    saveMetricHistory(existingFeedId, currentValue);
                    // 트래킹 상태만 업데이트
                    tracking.setStatus("FIRED");
                    tracking.setLastCheckedAt(OffsetDateTime.now());
                    alarmTrackingMapper.updateTracking(tracking);
                    return; // 중복이므로 Feed 생성 안 함
                }
            } else {
                // 레벨 변경인 경우 로그만 출력
                log.info("레벨 변경으로 인한 Feed 생성: ruleId={}, 기존 level={}, 새 level={}",
                        rule.getAlarmRuleId(), "?", level);
            }

            String logMessage = isLevelChange
                    ? String.format("알람 레벨 변경: %s", level)
                    : String.format("새로운 알람 발생: %s", level);

            // 1) Feed 생성 (feed.alarm_tracking_id = tracking.alarmTrackingId)
            AlarmFeed feed = AlarmFeed.builder()
                    .alarmRuleId(rule.getAlarmRuleId())
                    .alarmTrackingId(tracking.getAlarmTrackingId())
                    .instanceId(tracking.getInstanceId())
                    .databaseId(tracking.getDatabaseId())
                    .alarmTitle(metricType + " 임계치 초과 (" + level + ")")
                    .severityLevel(level)
                    .metricType(metricType)
                    .currentValue(currentValue)
                    .thresholdValue(parser.getThresholdValue(parser.parseJsonLevels(rule.getLevels()), level.toLowerCase()))
                    .message(String.format("%s가 임계치를 초과했습니다 (현재: %s, 임계치: %s)",
                            metricType, currentValue, parser.getThresholdValue(parser.parseJsonLevels(rule.getLevels()), level.toLowerCase())))
                    .occurredAt(OffsetDateTime.now())
                    .isResolved(false)
                    .isRead(false)
                    .acknowledged(false)
                    .build();

            alarmFeedMapper.insertAlarmFeed(feed);

            Long feedId = feed.getAlarmFeedId();
            if (feedId == null) {
                log.error("AlarmFeed ID가 생성되지 않았습니다! trackingId={}", tracking.getAlarmTrackingId());
                throw new RuntimeException("AlarmFeed 생성 실패");
            }

            // 2) 트래킹 상태 업데이트 (FIRED)
            tracking.setStatus("FIRED");
            tracking.setLastCheckedAt(OffsetDateTime.now());
            alarmTrackingMapper.updateTracking(tracking);

            // 3) 첫 메트릭 히스토리 저장 (feed 기준)
            saveMetricHistory(feedId, currentValue);

            // 4) 관련 객체 저장 (feed 기준)
            saveRelatedObjects(conn, feedId, rule.getAlarmRuleId(), metricType);

            // 5) Slack 알림 전송 (인스턴스 ID 포함)
            String instanceName = getInstanceName(tracking.getInstanceId());
            //String databaseName = getDatabaseName(tracking.getDatabaseId());
            BigDecimal threshold = parser.getThresholdValue(parser.parseJsonLevels(rule.getLevels()), level.toLowerCase());

            String description = String.format(
                    "%s가 임계치를 초과했습니다.\n• 현재값: %s\n• 임계치: %s\n• 발생 횟수: %d회",
                    metricType,
                    currentValue,
                    threshold,
                    tracking.getConsecutiveCount()
            );

            log.info("📤 Slack 알림 전송 시작: instanceId={}, instanceName={}, metricType={}, level={}",
                    tracking.getInstanceId(), instanceName, metricType, level);

            // 인스턴스 ID를 포함하여 전송 (인스턴스별 Slack 설정 사용)
            try {
                slackNotificationService.sendAlarmNotification(
                        tracking.getInstanceId(),
                        metricType + " 임계치 초과",
                        level,
                        description,
                        instanceName,
                        databaseName
                );
                log.info("✅ Slack 알림 전송 요청 완료: instanceId={}, instanceName={}",
                        tracking.getInstanceId(), instanceName);
            } catch (Exception e) {
                log.error("❌ Slack 알림 전송 중 예외 발생: instanceId={}, instanceName={}, error={}",
                        tracking.getInstanceId(), instanceName, e.getMessage(), e);
            }

            log.warn("🚨 {} ruleId={}, trackingId={}, feedId={}, metric={}, level={}",
                    logMessage, rule.getAlarmRuleId(), tracking.getAlarmTrackingId(), feedId, metricType, level);

        } catch (Exception e) {
            log.error("알람 발생 처리 실패", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 알람 해제
     */
    public void resolveAlarm(AlarmTracking tracking, String metricType) {
        log.info("✅ 알람 해제: metric={}", metricType);

        Long latestFeedId = selectLatestFeedIdForTracking(tracking.getAlarmTrackingId());
        if (latestFeedId != null) {
            alarmFeedMapper.resolveByFeedId(latestFeedId);
        } else {
            log.warn("resolveAlarm: 관련 feed를 찾을 수 없음. trackingId={}", tracking.getAlarmTrackingId());
        }

        tracking.setStatus("RESOLVED");
        alarmTrackingMapper.updateTracking(tracking);
    }

    /**
     * 메트릭 히스토리 저장 (차트용) - feedId 필수
     */
    public void saveMetricHistory(Long alarmFeedId, BigDecimal value) {
        if (alarmFeedId == null) {
            log.warn("⚠️ saveMetricHistory 호출 시 alarmFeedId가 null입니다. 저장 건너뜀. value={}", value);
            return;
        }
        try {
            alarmFeedMapper.insertMetricHistory(
                    alarmFeedId,
                    value,
                    OffsetDateTime.now()
            );
            log.debug("메트릭 히스토리 저장 완료: feedId={}, value={}", alarmFeedId, value);
        } catch (Exception e) {
            log.error("메트릭 히스토리 저장 실패: feedId={}, value={}", alarmFeedId, value, e);
        }
    }

    /**
     * 트래킹에 연관된 최신 Feed ID 조회 (유틸)
     */
    public Long selectLatestFeedIdForTracking(Long alarmTrackingId) {
        if (alarmTrackingId == null) return null;
        try {
            return alarmFeedMapper.selectLatestFeedIdByTrackingId(alarmTrackingId);
        } catch (Exception e) {
            log.warn("tracking에 대한 최신 feed 조회 실패: trackingId={}", alarmTrackingId, e);
            return null;
        }
    }

    /**
     * 관련 객체 저장 (범용)
     */
    public void saveRelatedObjects(Connection conn, Long alarmFeedId, Long alarmRuleId, String metricType) {
        log.info("💾 관련 객체 저장 시작: alarmFeedId={}, metricType={}", alarmFeedId, metricType);

        String sql = metricConfig.getRelatedObjectsQuery(metricType);
        if (sql == null) {
            log.warn("⚠️ 관련 객체 쿼리 없음: metricType={}", metricType);
            return;
        }

        log.info("📝 관련 객체 쿼리 실행: metricType={}, sql={}", metricType, sql);

        int savedCount = 0;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String objectType = rs.getString("object_type");
                String objectName = rs.getString("object_name");
                BigDecimal metricValue = rs.getBigDecimal("metric_value");
                String status = rs.getString("status");

                log.info("  - 저장할 객체: type={}, name={}, metricValue={}, status={}",
                        objectType, objectName, metricValue, status);

                alarmFeedMapper.insertRelatedObject(
                        alarmFeedId,
                        alarmRuleId,
                        objectType,
                        objectName,
                        metricValue,
                        status
                );
                savedCount++;
            }

            log.info("✅ 관련 객체 저장 완료: alarmFeedId={}, 저장된 개수={}", alarmFeedId, savedCount);

        } catch (SQLException e) {
            log.error("❌ 관련 객체 저장 실패: metricType={}, alarmFeedId={}", metricType, alarmFeedId, e);
        }
    }

    // ========== 헬퍼 메서드 ==========

    private String getInstanceName(Long instanceId) {
        if (instanceId == null) return "Unknown";
        try {
            return instanceRepository.findById(instanceId)
                    .map(Instance::getInstanceName)
                    .orElse("Instance-" + instanceId);
        } catch (Exception e) {
            return "Instance-" + instanceId;
        }
    }
//
//    private String getDatabaseName(Long databaseId) {
//        if (databaseId == null) return "Unknown";
//        try {
//            Database database = databaseRepository.findById(databaseId);
//            return database != null ? database.getDatabaseName() : "Database-" + databaseId;
//        } catch (Exception e) {
//            return "Database-" + databaseId;
//        }
//    }
}