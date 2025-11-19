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
            // 1. 해당 지표의 활성화된 알람 규칙 조회
            List<AlarmRule> rules = alarmRuleMapper.findActiveRules(
                    instanceId, databaseId, metricType
            );

            log.info("🔍 활성화된 알람 규칙 조회: metricType={}, instanceId={}, databaseId={}, 규칙 개수={}", 
                    metricType, instanceId, databaseId, rules != null ? rules.size() : 0);

            if (rules == null || rules.isEmpty()) {
                log.info("⚠️ 활성화된 알람 규칙 없음: metricType={}, instanceId={}, databaseId={}", 
                        metricType, instanceId, databaseId);
                return;
            }

            // 2. 각 규칙별로 처리 (집계 타입에 따라 다른 값 수집)
            for (AlarmRule rule : rules) {
                log.info("📋 규칙 처리 시작: ruleId={}, metricType={}, aggregationType={}, operator={}", 
                        rule.getAlarmRuleId(), rule.getMetricType(), rule.getAggregationType(), rule.getOperator());
                try {
                    // 규칙별 집계 타입에 맞는 지표 값 수집
                    BigDecimal currentValue = collectMetricValue(
                            conn, metricType, instanceId, databaseId, rule.getAggregationType()
                    );
                    
                    if (currentValue == null) {
                        log.warn("지표 수집 실패: metricType={}, aggregationType={}", 
                                metricType, rule.getAggregationType());
                        continue;
                    }

                    log.info("📊 지표 체크: metricType={}, aggregationType={}, currentValue={}, operator={}, ruleId={}", 
                            metricType, rule.getAggregationType(), currentValue, rule.getOperator(), rule.getAlarmRuleId());

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
     * - 집계 타입에 따라 다른 쿼리 사용
     * - latest_avg: 실시간 값
     * - avg_5m: 5분 집계 테이블 평균
     * - avg_15m: 1분 집계 테이블에서 15분 평균
     * - p95_15m: 1분 집계 테이블에서 15분 95퍼센트
     */
    private BigDecimal collectMetricValue(Connection conn, String metricType, Long instanceId, Long databaseId, String aggregationType) {
        log.debug("📥 지표 수집 시작: metricType={}, aggregationType={}, instanceId={}, databaseId={}", 
                metricType, aggregationType, instanceId, databaseId);
        
        // aggregationType이 null이거나 latest_avg면 실시간 값 사용
        if (aggregationType == null || "latest_avg".equals(aggregationType)) {
            BigDecimal value = collectLatestValue(conn, metricType, instanceId, databaseId);
            log.debug("📥 실시간 값 수집 결과: metricType={}, value={}", metricType, value);
            return value;
        }

        // 집계 타입에 따라 집계 테이블에서 조회
        String sql = metricConfig.getAggregatedMetricQuery(metricType, aggregationType);
        if (sql == null) {
            log.warn("집계 타입에 대한 쿼리가 없음: metricType={}, aggregationType={}, 실시간 값 사용", 
                    metricType, aggregationType);
            return collectLatestValue(conn, metricType, instanceId, databaseId);
        }
        
        log.debug("📥 집계 쿼리 실행: metricType={}, aggregationType={}, sql={}", metricType, aggregationType, sql);

        try (var pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, instanceId);
            pstmt.setLong(2, databaseId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    BigDecimal value = rs.getBigDecimal(1);
                    log.debug("📥 집계 값 수집 결과: metricType={}, aggregationType={}, value={}", 
                            metricType, aggregationType, value);
                    return value;
                } else {
                    log.warn("📥 집계 쿼리 결과 없음: metricType={}, aggregationType={}", metricType, aggregationType);
                }
            }
        } catch (SQLException e) {
            log.error("집계 지표 수집 쿼리 실행 실패: metricType={}, aggregationType={}", 
                    metricType, aggregationType, e);
        }

        return null;
    }

    /**
     * 실시간 지표 값 수집 (latest_avg)
     */
    private BigDecimal collectLatestValue(Connection conn, String metricType, Long instanceId, Long databaseId) {
        String sql = metricConfig.getMetricQuery(metricType);
        if (sql == null) {
            log.error("❌ 알 수 없는 지표 타입: {}", metricType);
            return null;
        }

        log.debug("📥 실시간 쿼리 실행: metricType={}, sql={}", metricType, sql);

        // 집계 테이블 기반 지표는 PreparedStatement 사용
        boolean needsParams = sql.contains("?");
        
        try {
            if (needsParams) {
                // 집계 테이블 기반 지표 (slow_query_spike, avg_execution_spike, qps_spike)
                try (var pstmt = conn.prepareStatement(sql)) {
                    pstmt.setLong(1, instanceId);
                    pstmt.setLong(2, databaseId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            BigDecimal value = rs.getBigDecimal(1);
                            log.debug("📥 실시간 값 수집 성공: metricType={}, value={}", metricType, value);
                            return value;
                        } else {
                            log.warn("📥 실시간 쿼리 결과 없음: metricType={}", metricType);
                        }
                    }
                }
            } else {
                // 일반 지표 (pg_stat_* 기반)
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    if (rs.next()) {
                        BigDecimal value = rs.getBigDecimal(1);
                        log.debug("📥 실시간 값 수집 성공: metricType={}, value={}", metricType, value);
                        return value;
                    } else {
                        log.warn("📥 실시간 쿼리 결과 없음: metricType={}", metricType);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("❌ 지표 수집 쿼리 실행 실패: metricType={}, sql={}", metricType, sql, e);
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
            boolean levelChanged = previousLevel != null && !previousLevel.equals(triggeredLevel);

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
                OffsetDateTime now = OffsetDateTime.now();
                int windowMin = getWindowMin(rule.getLevels(), triggeredLevel);
                OffsetDateTime windowStart = now.minusMinutes(windowMin);
                
                // 레벨이 변경되었으면 카운트 리셋
                if (levelChanged) {
                    log.info("📊 알람 레벨 변경 감지: {} -> {}", previousLevel, triggeredLevel);
                    tracking.setConsecutiveCount(1);
                    tracking.setFirstTriggeredAt(now);
                } else {
                    // 윈도우가 지나갔으면 카운트 리셋 (윈도우 기반 체크)
                    if (tracking.getFirstTriggeredAt().isBefore(windowStart)) {
                        log.debug("윈도우가 지나가서 카운트 리셋: firstTriggered={}, windowStart={}, window={}분", 
                                tracking.getFirstTriggeredAt(), windowStart, windowMin);
                        tracking.setConsecutiveCount(1);
                        tracking.setFirstTriggeredAt(now);
                    } else {
                        // 윈도우 내에서 연속 발생
                        tracking.setConsecutiveCount(tracking.getConsecutiveCount() + 1);
                    }
                }

                tracking.setCurrentValue(currentValue);
                tracking.setCurrentLevel(triggeredLevel);
                tracking.setLastCheckedAt(now);
                alarmTrackingMapper.updateTracking(tracking);
            }

            // 모든 레벨에서 발생 조건 확인 (NOTICE, WARNING, CRITICAL)
            // 레벨 변경 시 무조건 알람 발생, 같은 레벨에서 처음 조건 만족 시에도 알람 발생
            if (shouldFireAlarm(rule, tracking, triggeredLevel, previousLevel) || levelChanged) {
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
            
            // 디버깅: 임계치와 비교 결과 로그
            log.debug("🔍 임계치 체크: currentValue={}, operator={}, notice={}, warning={}, critical={}", 
                    currentValue, operator, noticeThreshold, warningThreshold, criticalThreshold);

            if (criticalThreshold != null && compareValue(currentValue, criticalThreshold, operator)) {
                log.info("🚨 CRITICAL 임계치 초과: currentValue={} {} threshold={}", 
                        currentValue, operator, criticalThreshold);
                return "CRITICAL";
            } else if (warningThreshold != null && compareValue(currentValue, warningThreshold, operator)) {
                log.info("⚠️ WARNING 임계치 초과: currentValue={} {} threshold={}", 
                        currentValue, operator, warningThreshold);
                return "WARNING";
            } else if (noticeThreshold != null && compareValue(currentValue, noticeThreshold, operator)) {
                log.info("ℹ️ NOTICE 임계치 초과: currentValue={} {} threshold={}", 
                        currentValue, operator, noticeThreshold);
                return "NOTICE";
            } else {
                log.debug("✅ 임계치 미달: currentValue={} {} 모든 임계치", currentValue, operator);
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
     * - 윈도우 기반: 최근 windowMin 분 내에 occurCount번 발생해야 함
     * - 지속 시간: minDurationMin 분 이상 지속되어야 함
     * - 레벨 변경 시 무조건 알람 발생 (levelChanged로 처리)
     * - 같은 레벨에서 처음 조건 만족 시 알람 발생
     */
    private boolean shouldFireAlarm(AlarmRule rule, AlarmTracking tracking, String level, String previousLevel) {
        try {
            // 해당 레벨의 발생 조건 확인
            int requiredCount = getOccurCount(rule.getLevels(), level);
            int requiredDuration = getMinDuration(rule.getLevels(), level);
            int windowMin = getWindowMin(rule.getLevels(), level);

            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime firstTriggered = tracking.getFirstTriggeredAt();
            OffsetDateTime windowStart = now.minusMinutes(windowMin);
            
            // 1. 윈도우 기반 발생 횟수 체크
            // processAlarmRule에서 이미 윈도우가 지나갔을 때 리셋하므로,
            // 여기서는 윈도우 내에 있는지와 발생 횟수만 확인
            if (firstTriggered.isBefore(windowStart)) {
                log.debug("윈도우가 지나감: firstTriggered={}, windowStart={}, window={}분", 
                        firstTriggered, windowStart, windowMin);
                return false;
            }
            
            // 윈도우 내에서 발생 횟수 체크
            if (tracking.getConsecutiveCount() < requiredCount) {
                log.debug("윈도우 내 발생 횟수 부족: {}/{} (윈도우: {}분)", 
                        tracking.getConsecutiveCount(), requiredCount, windowMin);
                return false;
            }

            // 2. 최소 지속 시간 체크
            long durationMinutes = java.time.Duration.between(firstTriggered, now).toMinutes();
            if (durationMinutes < requiredDuration) {
                log.debug("지속 시간 부족: {}분/{}분", durationMinutes, requiredDuration);
                return false;
            }

            // 3. 이미 발생한 알람인지 체크
            // - 레벨이 변경되었으면 무조건 알람 발생 (levelChanged로 처리되므로 여기서는 false 반환하지 않음)
            // - 같은 레벨에서 이미 FIRED 상태이고, 이전 레벨과 현재 레벨이 같으면 알람을 다시 발생시키지 않음
            // - 같은 레벨에서 처음 조건을 만족하면 알람 발생
            if ("FIRED".equals(tracking.getStatus()) && previousLevel != null && level.equals(previousLevel)) {
                // 같은 레벨에서 이미 FIRED 상태면 알람을 다시 발생시키지 않음
                log.debug("같은 레벨에서 이미 알람 발생: level={}, previousLevel={}, status=FIRED", level, previousLevel);
                return false;
            }

            log.debug("✅ 알람 발생 조건 충족: level={}, count={}/{}, duration={}분/{}분, window={}분", 
                    level, tracking.getConsecutiveCount(), requiredCount, durationMinutes, requiredDuration, windowMin);
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

    private int getWindowMin(String levelsJson, String level) {
        try {
            Map<String, Map<String, Object>> levels = parseJsonLevels(levelsJson);
            Map<String, Object> levelConfig = levels.get(level.toLowerCase());
            if (levelConfig != null && levelConfig.containsKey("windowMin")) {
                return ((Number) levelConfig.get("windowMin")).intValue();
            }
        } catch (Exception e) {
            log.error("윈도우 시간 추출 실패: level={}", level, e);
        }
        return 15; // 기본값: 15분
    }
}