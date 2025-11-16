package com.dajanggan.domain.alarm.service;

import com.dajanggan.domain.alarm.config.MetricConfig;
import com.dajanggan.domain.alarm.domain.*;
import com.dajanggan.domain.alarm.repository.AlarmFeedMapper;
import com.dajanggan.domain.alarm.repository.AlarmRuleMapper;
import com.dajanggan.domain.alarm.repository.AlarmTrackingMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GenericAlarmCollector {

    private final AlarmRuleMapper alarmRuleMapper;
    private final AlarmTrackingMapper alarmTrackingMapper;
    private final AlarmFeedMapper alarmFeedMapper;
    private final MetricConfig metricConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 범용 알람 체크 - 모든 지표에 사용 가능
     * (스케줄러나 호출자가 Connection을 제공)
     */
    public void checkMetric(
            Connection conn,
            Long instanceId,
            Long databaseId,
            String metricType
    ) {
        try {
            // 1. 현재 지표 값 수집
            BigDecimal currentValue = collectMetricValue(conn, metricType);
            if (currentValue == null) {
                log.warn("지표 수집 실패: {}", metricType);
                return;
            }

            log.debug("[{}] Current value: {}", metricType, currentValue);

            // 2. 해당 지표의 활성화된 알람 규칙 조회
            List<AlarmRule> rules = alarmRuleMapper.findActiveRules(
                    instanceId, databaseId, metricType
            );

            if (rules == null || rules.isEmpty()) {
                log.debug("활성화된 알람 규칙 없음: {}", metricType);
                return;
            }

            // 3. 각 규칙별로 처리
            for (AlarmRule rule : rules) {
                try {
                    // 각 규칙 처리는 트랜잭션으로 처리
                    processAlarmRule(conn, rule, currentValue, instanceId, databaseId, metricType);
                } catch (Exception e) {
                    // 개별 규칙 실패가 전체 수집을 멈추지 않도록 로그만 남김
                    log.error("개별 규칙 처리 실패: ruleId={}, metricType={}", rule.getAlarmRuleId(), metricType, e);
                }
            }

        } catch (Exception e) {
            log.error("알람 체크 실패: metricType={}", metricType, e);
        }
    }

    /**
     * 지표 값 수집 (범용)
     */
    private BigDecimal collectMetricValue(Connection conn, String metricType) {
        String sql = metricConfig.getMetricQuery(metricType);
        if (sql == null) {
            log.error("알 수 없는 지표 타입: {}", metricType);
            return null;
        }

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getBigDecimal(1);
            }
        } catch (SQLException e) {
            log.error("지표 수집 쿼리 실행 실패: {}", metricType, e);
        }

        return null;
    }

    /**
     * 알람 규칙 처리 (트랜잭션 적용)
     * - 트래킹 생성/업데이트와 알람 발동(Feed 생성)은 DB 원자성을 위해 트랜잭션 내에서 수행
     */
    @Transactional
    public void processAlarmRule(
            Connection conn,
            AlarmRule rule,
            BigDecimal currentValue,
            Long instanceId,
            Long databaseId,
            String metricType
    ) {
        // 트래킹 조회 (rule 단위로 단일 트래킹을 가정)
        Optional<AlarmTracking> trackingOpt = alarmTrackingMapper.findByRuleId(rule.getAlarmRuleId());
        AlarmTracking tracking = trackingOpt.orElse(null);

        // 임계치 체크
        String triggeredLevel = checkThresholds(rule, currentValue);

        if (triggeredLevel != null) {
            // 임계치 감지된 상태
            String previousLevel = tracking != null ? tracking.getCurrentLevel() : null;

            if (tracking == null) {
                // 트래킹이 없으면 새로 생성
                tracking = createTracking(rule, instanceId, databaseId, currentValue, triggeredLevel);
                log.info("🔔 알람 트래킹 시작: ruleId={}, metric={}, level={}",
                        rule.getAlarmRuleId(), metricType, triggeredLevel);
            } else {
                // 기존 트래킹이 있으면 metric 값은 tracking 상태에 따라 처리
                if ("FIRED".equals(tracking.getStatus())) {
                    Long feedId = selectLatestFeedIdForTracking(tracking.getAlarmTrackingId());
                    if (feedId != null) {
                        saveMetricHistory(feedId, currentValue);
                    }
                }
            }

            // 트래킹 연속 카운트/값 업데이트
            if (tracking != null) {
                // 레벨이 변경되었으면 카운트 리셋
                if (previousLevel != null && !previousLevel.equals(triggeredLevel)) {
                    log.info("📊 알람 레벨 변경 감지: {} -> {}", previousLevel, triggeredLevel);
                    tracking.setConsecutiveCount(1);
                    tracking.setFirstTriggeredAt(OffsetDateTime.now());
                } else {
                    tracking.setConsecutiveCount(tracking.getConsecutiveCount() + 1);
                }

                tracking.setCurrentValue(currentValue);
                tracking.setCurrentLevel(triggeredLevel);
                tracking.setLastCheckedAt(OffsetDateTime.now());
                alarmTrackingMapper.updateTracking(tracking);
            }

            // 모든 레벨에서 발생 조건 확인 (NOTICE, WARNING, CRITICAL)
            boolean levelChanged = previousLevel != null && !previousLevel.equals(triggeredLevel);

            if (shouldFireAlarm(rule, tracking, triggeredLevel) || levelChanged) {
                // 알람 발생 또는 레벨 변경
                fireAlarm(conn, rule, tracking, currentValue, triggeredLevel, metricType, levelChanged);
            }

        } else {
            // 정상 범위: 상태 복구 또는 트래킹 삭제
            if (tracking != null && "FIRED".equals(tracking.getStatus())) {
                // 이미 FIRED 였다면 resolve 처리 (Feed 상태 업데이트 및 tracking 상태 갱신)
                resolveAlarm(tracking, metricType);
            }

            if (tracking != null) {
                // 비활성화된 규칙일 경우(또는 정상화된 경우) 트래킹을 삭제
                alarmTrackingMapper.delete(tracking.getAlarmTrackingId());
            }
        }
    }

    /**
     * 값 비교 (operator 적용)
     * operator: "gt", "gte", "lt", "lte", "eq"
     */
    private boolean compareValue(BigDecimal current, BigDecimal threshold, String operator) {
        if (current == null || threshold == null || operator == null) return false;

        int cmp = current.compareTo(threshold);
        switch (operator) {
            case "gt":
                return cmp > 0;
            case "gte":
                return cmp >= 0;
            case "lt":
                return cmp < 0;
            case "lte":
                return cmp <= 0;
            case "eq":
                return cmp == 0;
            default:
                log.warn("알 수 없는 operator가 전달되었습니다: {}", operator);
                return false;
        }
    }

    /**
     * 임계치 체크
     */
    private String checkThresholds(AlarmRule rule, BigDecimal currentValue) {
        try {
            // JSONB levels 파싱
            Map<String, Map<String, Object>> levels = parseJsonLevels(rule.getLevels());

            BigDecimal criticalThreshold = getThresholdValue(levels, "critical");
            BigDecimal warningThreshold = getThresholdValue(levels, "warning");
            BigDecimal noticeThreshold = getThresholdValue(levels, "notice");

            String operator = rule.getOperator(); // gt, gte, lt, lte, eq

            if (criticalThreshold != null && compareValue(currentValue, criticalThreshold, operator)) {
                return "CRITICAL";
            } else if (warningThreshold != null && compareValue(currentValue, warningThreshold, operator)) {
                return "WARNING";
            } else if (noticeThreshold != null && compareValue(currentValue, noticeThreshold, operator)) {
                return "NOTICE";
            }

        } catch (Exception e) {
            log.error("임계치 체크 실패", e);
        }

        return null;
    }

    /**
     * 트래킹 생성 (오직 트래킹만 생성)
     */
    private AlarmTracking createTracking(
            AlarmRule rule,
            Long instanceId,
            Long databaseId,
            BigDecimal currentValue,
            String currentLevel
    ) {
        AlarmTracking tracking = AlarmTracking.builder()
                .alarmRuleId(rule.getAlarmRuleId())
                .instanceId(instanceId)
                .databaseId(databaseId)
                .firstTriggeredAt(OffsetDateTime.now())
                .lastCheckedAt(OffsetDateTime.now())
                .consecutiveCount(1)
                .currentValue(currentValue)
                .currentLevel(currentLevel)
                .status("PENDING")
                .build();
        alarmTrackingMapper.insertTracking(tracking);

        if (tracking.getAlarmTrackingId() == null) {
            log.error("AlarmTracking ID가 생성되지 않았습니다!");
            throw new RuntimeException("AlarmTracking 생성 실패");
        }
        return tracking;
    }

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
            String logMessage = isLevelChange
                    ? String.format("📈 알람 레벨 변경: %s", level)
                    : String.format("🚨 새로운 알람 발생: %s", level);

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
                    .thresholdValue(getThresholdValue(parseJsonLevels(rule.getLevels()), level.toLowerCase()))
                    .message(String.format("%s가 임계치를 초과했습니다 (현재: %s, 임계치: %s)",
                            metricType, currentValue, getThresholdValue(parseJsonLevels(rule.getLevels()), level.toLowerCase())))
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

            log.warn("{} ruleId={}, trackingId={}, feedId={}, metric={}, level={}",
                    logMessage, rule.getAlarmRuleId(), tracking.getAlarmTrackingId(), feedId, metricType, level);

        } catch (Exception e) {
            log.error("알람 발생 처리 실패", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 메트릭 히스토리 저장 (차트용) - feedId 필수
     */
    private void saveMetricHistory(Long alarmFeedId, BigDecimal value) {
        if (alarmFeedId == null) {
            log.warn("saveMetricHistory 호출 시 alarmFeedId가 null입니다. 저장 건너뜀. value={}", value);
            return;
        }
        alarmFeedMapper.insertMetricHistory(
                alarmFeedId,
                value,
                OffsetDateTime.now()
        );
    }

    /**
     * 트래킹에 연관된 최신 Feed ID 조회 (유틸)
     */
    private Long selectLatestFeedIdForTracking(Long alarmTrackingId) {
        if (alarmTrackingId == null) return null;
        try {
            return alarmFeedMapper.selectLatestFeedIdByTrackingId(alarmTrackingId);
        } catch (Exception e) {
            log.warn("tracking에 대한 최신 feed 조회 실패: trackingId={}", alarmTrackingId, e);
            return null;
        }
    }

    /**
     * 알람 발생 조건 체크
     */
    private boolean shouldFireAlarm(AlarmRule rule, AlarmTracking tracking, String level) {
        try {
            // 해당 레벨의 발생 조건 확인
            int requiredCount = getOccurCount(rule.getLevels(), level);
            int requiredDuration = getMinDuration(rule.getLevels(), level);

            // 1. 연속 발생 횟수 체크
            if (tracking.getConsecutiveCount() < requiredCount) {
                log.debug("발생 횟수 부족: {}/{}", tracking.getConsecutiveCount(), requiredCount);
                return false;
            }

            // 2. 최소 지속 시간 체크
            long durationMinutes = java.time.Duration.between(
                    tracking.getFirstTriggeredAt(),
                    OffsetDateTime.now()
            ).toMinutes();

            if (durationMinutes < requiredDuration) {
                log.debug("지속 시간 부족: {}분/{}", durationMinutes, requiredDuration);
                return false;
            }

            // 3. 이미 발생한 알람인지 체크 (같은 레벨)
            if ("FIRED".equals(tracking.getStatus()) && level.equals(tracking.getCurrentLevel())) {
                return false;
            }

            return true;

        } catch (Exception e) {
            log.error("알람 발생 조건 체크 실패", e);
            return false;
        }
    }

    /**
     * 알람 해제
     */
    private void resolveAlarm(AlarmTracking tracking, String metricType) {
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
     * 관련 객체 저장 (범용)
     */
    private void saveRelatedObjects(Connection conn, Long alarmFeedId, Long alarmRuleId, String metricType) {
        String sql = metricConfig.getRelatedObjectsQuery(metricType);
        if (sql == null) {
            log.debug("관련 객체 쿼리 없음: {}", metricType);
            return;
        }

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                alarmFeedMapper.insertRelatedObject(
                        alarmFeedId,
                        alarmRuleId,
                        rs.getString("object_type"),
                        rs.getString("object_name"),
                        rs.getBigDecimal("metric_value"),
                        rs.getString("status")
                );
            }

        } catch (SQLException e) {
            log.error("관련 객체 저장 실패: {}", metricType, e);
        }
    }

    // ========== 유틸리티 메서드 ==========

    private Map<String, Map<String, Object>> parseJsonLevels(String levelsJson) {
        try {
            return objectMapper.readValue(levelsJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("JSONB 파싱 실패: {}", levelsJson, e);
            return Map.of();
        }
    }

    private BigDecimal getThresholdValue(Map<String, Map<String, Object>> levels, String level) {
        try {
            Map<String, Object> levelConfig = levels.get(level);
            if (levelConfig != null && levelConfig.containsKey("threshold")) {
                Object threshold = levelConfig.get("threshold");
                if (threshold instanceof Number) {
                    return new BigDecimal(threshold.toString());
                }
            }
        } catch (Exception e) {
            log.error("임계치 추출 실패: level={}", level, e);
        }
        return null;
    }

    private int getOccurCount(String levelsJson, String level) {
        try {
            Map<String, Map<String, Object>> levels = parseJsonLevels(levelsJson);
            Map<String, Object> levelConfig = levels.get(level.toLowerCase());
            if (levelConfig != null && levelConfig.containsKey("occurCount")) {
                return ((Number) levelConfig.get("occurCount")).intValue();
            }
        } catch (Exception e) {
            log.error("발생 횟수 추출 실패: level={}", level, e);
        }
        return 2;
    }

    private int getMinDuration(String levelsJson, String level) {
        try {
            Map<String, Map<String, Object>> levels = parseJsonLevels(levelsJson);
            Map<String, Object> levelConfig = levels.get(level.toLowerCase());
            if (levelConfig != null && levelConfig.containsKey("minDurationMin")) {
                return ((Number) levelConfig.get("minDurationMin")).intValue();
            }
        } catch (Exception e) {
            log.error("최소 지속 시간 추출 실패: level={}", level, e);
        }
        return 5;
    }
}