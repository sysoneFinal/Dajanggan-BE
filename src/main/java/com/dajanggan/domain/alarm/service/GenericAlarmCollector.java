package com.dajanggan.domain.alarm.service;

import com.dajanggan.domain.alarm.config.MetricConfig;
import com.dajanggan.domain.alarm.domain.*;
import com.dajanggan.domain.alarm.repository.AlarmFeedMapper;
import com.dajanggan.domain.alarm.repository.AlarmRuleMapper;
import com.dajanggan.domain.alarm.repository.AlarmTrackingMapper;
import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.repository.DatabaseRepository;
import com.dajanggan.domain.instance.repository.InstanceRepository;
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
    private final SlackNotificationService slackNotificationService;

    // GenericAlarmCollector에 추가
    private final InstanceRepository instanceRepository;
    private final DatabaseRepository databaseRepository;

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

    private String getDatabaseName(Long databaseId) {
        if (databaseId == null) return "Unknown";
        try {
            Database database = databaseRepository.findById(databaseId);
            return database != null ? database.getDatabaseName() : "Database-" + databaseId;
        } catch (Exception e) {
            return "Database-" + databaseId;
        }
    }

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
        
        if (tracking != null) {
            log.info("📋 기존 트래킹 조회: ruleId={}, trackingId={}, status={}, firstTriggered={}, consecutiveCount={}, currentLevel={}",
                    rule.getAlarmRuleId(), tracking.getAlarmTrackingId(), tracking.getStatus(),
                    tracking.getFirstTriggeredAt(), tracking.getConsecutiveCount(), tracking.getCurrentLevel());
        }

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
                // ✅ CRITICAL 수정: FIRED 상태에서는 메트릭 히스토리를 나중에 저장
                // 트래킹 상태 확인만 하고, 메트릭 히스토리는 shouldFireAlarm 이후에 처리
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
                    if ("FIRED".equals(tracking.getStatus())) {
                        tracking.setConsecutiveCount(tracking.getConsecutiveCount() + 1);
                        log.debug("✅ FIRED 상태 유지: firstTriggered={}, consecutiveCount={}",
                                tracking.getFirstTriggeredAt(), tracking.getConsecutiveCount());
                    } else {
                        if (tracking.getFirstTriggeredAt().isBefore(windowStart)) {
                            log.debug("윈도우가 지나가서 PENDING 카운트 리셋: firstTriggered={}, windowStart={}",
                                    tracking.getFirstTriggeredAt(), windowStart);
                            tracking.setConsecutiveCount(1);
                            tracking.setFirstTriggeredAt(now);
                        } else {
                            tracking.setConsecutiveCount(tracking.getConsecutiveCount() + 1);
                        }
                    }
                }

                tracking.setCurrentValue(currentValue);
                tracking.setCurrentLevel(triggeredLevel);
                tracking.setLastCheckedAt(now);

                // ✅ CRITICAL 로그 추가
                log.info("🔄 tracking 업데이트 전: trackingId={}, status={}, count={}",
                        tracking.getAlarmTrackingId(), tracking.getStatus(), tracking.getConsecutiveCount());

                alarmTrackingMapper.updateTracking(tracking);

                log.info("🔄 tracking 업데이트 후: trackingId={}, status={}, count={}",
                        tracking.getAlarmTrackingId(), tracking.getStatus(), tracking.getConsecutiveCount());

                // ✅✅✅ 여기에 추가! DB에서 최신 상태 재조회
                tracking = alarmTrackingMapper.findByRuleId(rule.getAlarmRuleId())
                        .orElseThrow(() -> new RuntimeException("Tracking not found after update: ruleId=" + rule.getAlarmRuleId()));

                log.info("🔄 tracking 재조회 완료: trackingId={}, status={}, count={}",
                        tracking.getAlarmTrackingId(), tracking.getStatus(), tracking.getConsecutiveCount());
            }

            // 모든 레벨에서 발생 조건 확인
            log.info("🔍 알람 발생 조건 체크: ruleId={}, status={}, level={}, previousLevel={}, levelChanged={}, firstTriggered={}, consecutiveCount={}",
                    rule.getAlarmRuleId(), tracking != null ? tracking.getStatus() : "null",
                    triggeredLevel, previousLevel, levelChanged,
                    tracking != null ? tracking.getFirstTriggeredAt() : "null",
                    tracking != null ? tracking.getConsecutiveCount() : 0);

            boolean shouldFire = shouldFireAlarm(rule, tracking, triggeredLevel, previousLevel);

            log.info("🔍 shouldFireAlarm 결과: ruleId={}, shouldFire={}, levelChanged={}",
                    rule.getAlarmRuleId(), shouldFire, levelChanged);

            // ✅ shouldFire=false이고 levelChanged=false인 경우 절대 fireAlarm 호출 안 함
            if (!shouldFire && !levelChanged) {
                // ✅ CRITICAL 추가: FIRED 상태에서 조건 미충족이면 메트릭 히스토리만 저장
                if ("FIRED".equals(tracking.getStatus())) {
                    Long feedId = selectLatestFeedIdForTracking(tracking.getAlarmTrackingId());
                    if (feedId != null) {
                        AlarmFeed existingFeed = alarmFeedMapper.selectAlarmDetail(feedId);
                        if (existingFeed != null && !existingFeed.getIsResolved()
                                && existingFeed.getSeverityLevel() != null
                                && existingFeed.getSeverityLevel().equals(triggeredLevel)) {
                            log.info("📊 기존 Feed에 메트릭 히스토리 저장: feedId={}, value={}, trackingId={}",
                                    feedId, currentValue, tracking.getAlarmTrackingId());
                            saveMetricHistory(feedId, currentValue);
                        }
                    }
                }
                log.info("⏸️ 알람 발생 조건 미충족: ruleId={}, shouldFire={}, levelChanged={}",
                        rule.getAlarmRuleId(), shouldFire, levelChanged);
                return; // early return으로 명확하게 종료
            }

            // shouldFire=true 또는 levelChanged=true인 경우에만 진행
            // ✅ 중복 Feed 체크 강화: rule 기준으로도 체크 (같은 알람은 한 번만 생성)
            Long existingFeedId = alarmFeedMapper.selectUnresolvedFeedIdByRule(
                    rule.getAlarmRuleId(),
                    instanceId,
                    databaseId,
                    triggeredLevel
            );

            if (existingFeedId != null) {
                log.warn("🚫 중복 Feed 방지: 같은 ruleId, instanceId, databaseId, level의 해결되지 않은 Feed가 이미 존재합니다. feedId={}, ruleId={}, level={}",
                        existingFeedId, rule.getAlarmRuleId(), triggeredLevel);
                // 기존 Feed에 메트릭 히스토리만 저장
                saveMetricHistory(existingFeedId, currentValue);
                return; // 중복이므로 알람 발생 안 함
            }

            // 쿨다운 체크 (레벨 변경 시에는 쿨다운 무시)
            if (!levelChanged && isInCooldown(rule, triggeredLevel)) {
                log.info("⏸️ 쿨다운 중: 알람 발생 건너뜀 - ruleId={}, level={}, cooldown={}분",
                        rule.getAlarmRuleId(), triggeredLevel, getCooldown(rule.getLevels(), triggeredLevel));
                return; // 쿨다운 중이면 알람 발생 안 함
            }

            // 알람 발생 또는 레벨 변경
            log.info("🚨 알람 발생: ruleId={}, level={}, levelChanged={}, shouldFire={}",
                    rule.getAlarmRuleId(), triggeredLevel, levelChanged, shouldFire);
            fireAlarm(conn, rule, tracking, currentValue, triggeredLevel, metricType, levelChanged);

        } else {
            // 정상 범위: 히스테리시스 적용하여 복구 조건 체크
            log.info("📍 정상 범위 처리: metricType={}, currentValue={}, tracking={}",
                    metricType, currentValue, tracking != null ? "존재" : "없음");

            if (tracking != null) {
                String currentLevel = tracking.getCurrentLevel();

                log.info("📍 Tracking 상태: trackingId={}, status={}, level={}, value={}",
                        tracking.getAlarmTrackingId(), tracking.getStatus(),
                        tracking.getCurrentLevel(), tracking.getCurrentValue());

                if ("FIRED".equals(tracking.getStatus())) {
                    log.info("📍 FIRED 상태 복구 조건 체크 시작");

                    if (shouldResolveAlarm(conn, rule, tracking, currentValue, currentLevel)) {
                        log.info("✅ 복구 조건 충족: trackingId={}", tracking.getAlarmTrackingId());
                        resolveAlarm(tracking, metricType);
                        alarmTrackingMapper.delete(tracking.getAlarmTrackingId());
                    } else {
                        log.info("❌ 복구 조건 미충족: trackingId={}", tracking.getAlarmTrackingId());
                    }
                } else if ("PENDING".equals(tracking.getStatus()) && currentLevel != null) {
                    log.info("📍 PENDING 상태 복구 조건 체크 시작");

                    if (shouldResolveAlarm(conn, rule, tracking, currentValue, currentLevel)) {
                        log.info("✅ PENDING 복구 조건 충족: trackingId={}", tracking.getAlarmTrackingId());

                        // ✅ Feed 확인
                        Long feedId = selectLatestFeedIdForTracking(tracking.getAlarmTrackingId());
                        if (feedId != null) {
                            log.warn("⚠️ PENDING에 Feed 존재: feedId={}, 해제 처리", feedId);
                            alarmFeedMapper.resolveByFeedId(feedId);
                        }

                        alarmTrackingMapper.delete(tracking.getAlarmTrackingId());
                    } else {
                        log.info("❌ PENDING 복구 조건 미충족: trackingId={}", tracking.getAlarmTrackingId());
                    }
                }
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
            // ✅ 중복 Feed 생성 방지 강화: rule 기준으로 체크 (동시성 문제 방지)
            if (!isLevelChange) {
                Long existingFeedId = alarmFeedMapper.selectUnresolvedFeedIdByRule(
                        rule.getAlarmRuleId(),
                        tracking.getInstanceId(),
                        tracking.getDatabaseId(),
                        level
                );
                
                if (existingFeedId != null) {
                    log.warn("🚫 fireAlarm 내부 중복 체크: 같은 ruleId, instanceId, databaseId, level의 해결되지 않은 Feed가 이미 존재합니다. feedId={}, ruleId={}, level={}",
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
                log.info("📈 레벨 변경으로 인한 Feed 생성: ruleId={}, 기존 level={}, 새 level={}",
                        rule.getAlarmRuleId(), "?", level);
            }

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

            // 5) Slack 알림 전송 (인스턴스 ID 포함)
            String instanceName = getInstanceName(tracking.getInstanceId());
            String databaseName = getDatabaseName(tracking.getDatabaseId());
            BigDecimal threshold = getThresholdValue(parseJsonLevels(rule.getLevels()), level.toLowerCase());

            String description = String.format(
                    "%s가 임계치를 초과했습니다.\n• 현재값: %s\n• 임계치: %s\n• 발생 횟수: %d회",
                    metricType,
                    currentValue,
                    threshold,
                    tracking.getConsecutiveCount()
            );

            log.info("📤 Slack 알림 전송 시작: instanceId={}, instanceName={}, metricType={}, level={}", 
                    tracking.getInstanceId(), instanceName, metricType, level);

            // ✅ 인스턴스 ID를 포함하여 전송 (인스턴스별 Slack 설정 사용)
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
            log.warn("⚠️ saveMetricHistory 호출 시 alarmFeedId가 null입니다. 저장 건너뜀. value={}", value);
            return;
        }
        try {
            alarmFeedMapper.insertMetricHistory(
                    alarmFeedId,
                    value,
                    OffsetDateTime.now()
            );
            log.debug("✅ 메트릭 히스토리 저장 완료: feedId={}, value={}", alarmFeedId, value);
        } catch (Exception e) {
            log.error("❌ 메트릭 히스토리 저장 실패: feedId={}, value={}", alarmFeedId, value, e);
        }
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

            log.info("🔍 shouldFireAlarm 상세 체크: ruleId={}, status={}, level={}, previousLevel={}, " +
                            "requiredCount={}, requiredDuration={}, consecutiveCount={}, firstTriggered={}",
                    rule.getAlarmRuleId(), tracking.getStatus(), level, previousLevel,
                    requiredCount, requiredDuration, tracking.getConsecutiveCount(), firstTriggered);

            // 1. 발생 횟수 체크
            if (tracking.getConsecutiveCount() < requiredCount) {
                log.info("❌ 발생 횟수 부족: {}/{} (윈도우: {}분)",
                        tracking.getConsecutiveCount(), requiredCount, windowMin);
                return false;
            }

            // 2. 최소 지속 시간 체크
            if (requiredDuration > 0) {
                long durationMinutes = java.time.Duration.between(firstTriggered, now).toMinutes();
                long durationSeconds = java.time.Duration.between(firstTriggered, now).getSeconds();
                log.info("⏱️ 지속 시간 체크: duration={}분 ({}초), required={}분",
                        durationMinutes, durationSeconds, requiredDuration);
                if (durationMinutes < requiredDuration) {
                    log.info("❌ 지속 시간 부족: {}분/{}분", durationMinutes, requiredDuration);
                    return false;
                }
            }

            // ✅ CRITICAL 수정: FIRED 상태 체크를 먼저 수행
            if ("FIRED".equals(tracking.getStatus())) {
                String currentTrackingLevel = tracking.getCurrentLevel();
                if (currentTrackingLevel != null && level.equals(currentTrackingLevel)) {
                    // FIRED 상태이고 같은 레벨이면 무조건 false 반환
                    log.info("❌ 이미 FIRED 상태: level={}, currentTrackingLevel={}, status=FIRED",
                            level, currentTrackingLevel);
                    return false;
                }
            }

            // 3. PENDING 상태에서 조건을 만족하면 FIRED로 전환
            if ("PENDING".equals(tracking.getStatus())) {
                long durationMinutes = requiredDuration > 0 ? java.time.Duration.between(firstTriggered, now).toMinutes() : 0;
                log.info("✅ PENDING → FIRED 전환 조건 충족: level={}, count={}/{}, duration={}분/{}분, status=PENDING",
                        level, tracking.getConsecutiveCount(), requiredCount, durationMinutes, requiredDuration);
                return true;
            }

            // 4. 기타 상태 (RESOLVED 등)
            long durationMinutes = requiredDuration > 0 ? java.time.Duration.between(firstTriggered, now).toMinutes() : 0;
            log.info("✅ 알람 발생 조건 충족: level={}, count={}/{}, duration={}분/{}분, status={}",
                    level, tracking.getConsecutiveCount(), requiredCount, durationMinutes, requiredDuration, tracking.getStatus());
            return true;

        } catch (Exception e) {
            log.error("알람 발생 조건 체크 실패", e);
            return false;
        }
    }

    /**
     * 알람 복구 조건 체크 (히스테리시스)
     * - 복구 임계치는 발생 임계치보다 낮게 설정
     * - 복구 지속 시간도 체크
     */
    private boolean shouldResolveAlarm(
            Connection conn,
            AlarmRule rule,
            AlarmTracking tracking,
            BigDecimal currentValue,
            String currentLevel
    ) {
        try {
            // 복구 임계치와 지속 시간 가져오기
            BigDecimal resolveThreshold = getResolveThreshold(rule.getLevels(), currentLevel);
            int resolveDuration = getResolveDuration(rule.getLevels(), currentLevel);
            String operator = rule.getOperator();

            // 복구 임계치가 설정되지 않았으면 기본값 사용 (발생 임계치의 80%)
            if (resolveThreshold == null) {
                BigDecimal fireThreshold = getThresholdValue(parseJsonLevels(rule.getLevels()), currentLevel.toLowerCase());
                if (fireThreshold != null) {
                    // 발생 임계치의 80%로 설정 (operator에 따라 반대 방향)
                    if (operator.equals("gt") || operator.equals("gte")) {
                        resolveThreshold = fireThreshold.multiply(new BigDecimal("0.8"));
                    } else {
                        resolveThreshold = fireThreshold.multiply(new BigDecimal("1.2"));
                    }
                    log.debug("복구 임계치 기본값 사용: {} (발생 임계치의 80%)", resolveThreshold);
                } else {
                    // 복구 임계치를 알 수 없으면 즉시 복구 (기존 동작)
                    log.debug("복구 임계치를 알 수 없음: 즉시 복구");
                    return true;
                }
            }

            // 복구 지속 시간 기본값 (설정되지 않았으면 3분)
            if (resolveDuration <= 0) {
                resolveDuration = 3;
            }

            // 1. 복구 임계치 체크 (발생 임계치보다 낮은 값이어야 함)
            // operator 반대 방향으로 체크 (gt면 lt, lt면 gt)
            String resolveOperator = getReverseOperator(operator);
            boolean belowResolveThreshold = compareValue(currentValue, resolveThreshold, resolveOperator);

            if (!belowResolveThreshold) {
                log.debug("복구 임계치 미달: currentValue={} {} resolveThreshold={}",
                        currentValue, resolveOperator, resolveThreshold);
                return false;
            }

            // 2. 복구 지속 시간 체크
            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime firstTriggered = tracking.getFirstTriggeredAt();
            long durationMinutes = java.time.Duration.between(firstTriggered, now).toMinutes();

            if (durationMinutes < resolveDuration) {
                log.debug("복구 지속 시간 부족: {}분/{}분", durationMinutes, resolveDuration);
                return false;
            }

            log.info("✅ 복구 조건 충족: currentValue={} {} resolveThreshold={}, duration={}분/{}분",
                    currentValue, resolveOperator, resolveThreshold, durationMinutes, resolveDuration);
            return true;

        } catch (Exception e) {
            log.error("복구 조건 체크 실패", e);
            // 에러 발생 시 안전하게 복구 허용
            return true;
        }
    }

    /**
     * operator 반대 방향 반환 (히스테리시스용)
     */
    private String getReverseOperator(String operator) {
        if (operator == null) return "lt";
        return switch (operator) {
            case "gt" -> "lt";
            case "gte" -> "lte";
            case "lt" -> "gt";
            case "lte" -> "gte";
            case "eq" -> "eq";  // 같음은 그대로
            default -> "lt";
        };
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

    /**
     * 복구 임계치 추출 (히스테리시스)
     */
    private BigDecimal getResolveThreshold(String levelsJson, String level) {
        try {
            Map<String, Map<String, Object>> levels = parseJsonLevels(levelsJson);
            Map<String, Object> levelConfig = levels.get(level.toLowerCase());
            if (levelConfig != null && levelConfig.containsKey("resolveThreshold")) {
                Object threshold = levelConfig.get("resolveThreshold");
                if (threshold instanceof Number) {
                    return new BigDecimal(threshold.toString());
                }
            }
        } catch (Exception e) {
            log.error("복구 임계치 추출 실패: level={}", level, e);
        }
        return null; // 기본값 없음 (shouldResolveAlarm에서 처리)
    }

    /**
     * 복구 지속 시간 추출 (히스테리시스)
     */
    private int getResolveDuration(String levelsJson, String level) {
        try {
            Map<String, Map<String, Object>> levels = parseJsonLevels(levelsJson);
            Map<String, Object> levelConfig = levels.get(level.toLowerCase());
            if (levelConfig != null && levelConfig.containsKey("resolveDurationMin")) {
                return ((Number) levelConfig.get("resolveDurationMin")).intValue();
            }
        } catch (Exception e) {
            log.error("복구 지속 시간 추출 실패: level={}", level, e);
        }
        return 0; // 기본값 없음 (shouldResolveAlarm에서 3분으로 설정)
    }

    /**
     * 쿨다운 체크
     * - 같은 레벨의 알람이 최근 cooldownMin 분 내에 발생했는지 확인
     * - 레벨 변경 시에는 쿨다운 무시 (중요한 변화이므로)
     */
    private boolean isInCooldown(AlarmRule rule, String level) {
        try {
            int cooldownMin = getCooldown(rule.getLevels(), level);
            if (cooldownMin <= 0) {
                return false; // 쿨다운이 설정되지 않았으면 쿨다운 없음
            }

            // 마지막 알람 발생 시간 조회 (같은 레벨만, 해결되지 않은 Feed만)
            OffsetDateTime lastFiredAt = alarmFeedMapper.selectLastFiredAtByRuleId(
                    rule.getAlarmRuleId(), level);
            if (lastFiredAt == null) {
                log.debug("쿨다운 체크: 이전 알람 없음 - ruleId={}, level={}", rule.getAlarmRuleId(), level);
                return false; // 이전 알람이 없으면 쿨다운 없음
            }

            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime cooldownEnd = lastFiredAt.plusMinutes(cooldownMin);

            boolean inCooldown = now.isBefore(cooldownEnd);
            if (inCooldown) {
                long remainingSeconds = java.time.Duration.between(now, cooldownEnd).getSeconds();
                log.info("⏸️ 쿨다운 중: ruleId={}, level={}, lastFiredAt={}, 남은 시간={}초 ({}분)", 
                        rule.getAlarmRuleId(), level, lastFiredAt, remainingSeconds, cooldownMin);
            } else {
                log.debug("쿨다운 종료: ruleId={}, level={}, lastFiredAt={}, cooldownEnd={}", 
                        rule.getAlarmRuleId(), level, lastFiredAt, cooldownEnd);
            }

            return inCooldown;

        } catch (Exception e) {
            log.error("쿨다운 체크 실패: ruleId={}, level={}", rule.getAlarmRuleId(), level, e);
            return false; // 에러 발생 시 쿨다운 없음으로 처리
        }
    }

    /**
     * 쿨다운 시간 추출
     */
    private int getCooldown(String levelsJson, String level) {
        try {
            Map<String, Map<String, Object>> levels = parseJsonLevels(levelsJson);
            Map<String, Object> levelConfig = levels.get(level.toLowerCase());
            if (levelConfig != null && levelConfig.containsKey("cooldownMin")) {
                return ((Number) levelConfig.get("cooldownMin")).intValue();
            }
        } catch (Exception e) {
            log.error("쿨다운 시간 추출 실패: level={}", level, e);
        }
        return 0; // 기본값: 쿨다운 없음
    }
}