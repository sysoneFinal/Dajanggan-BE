package com.dajanggan.domain.alarm.controller.test;

import com.dajanggan.domain.alarm.service.test.AlarmTestService;
import com.dajanggan.domain.alarm.service.SlackNotificationService;
import com.dajanggan.domain.alarm.repository.AlarmRuleMapper;
import com.dajanggan.domain.alarm.repository.AlarmTrackingMapper;
import com.dajanggan.domain.alarm.repository.AlarmFeedMapper;
import com.dajanggan.domain.alarm.domain.AlarmRule;
import com.dajanggan.domain.alarm.domain.AlarmTracking;
import com.dajanggan.domain.alarm.domain.AlarmFeed;
import com.dajanggan.domain.alarm.config.MetricConfig;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.domain.instance.repository.DatabaseRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 알람 수동 트리거 및 테스트 규칙 생성
 * - 수동 알람 발생
 * - 테스트 규칙 생성
 * - 임계치 조정
 * - 복구/쿨다운 테스트
 */
@Tag(name = "Alarm-Manual-Trigger", description = "알람 수동 트리거 API")
@RestController
@RequestMapping("/api/test/alarm/trigger")
@RequiredArgsConstructor
@Slf4j
public class AlarmManualTriggerController {

    private final AlarmRuleMapper alarmRuleMapper;
    private final AlarmTrackingMapper alarmTrackingMapper;
    private final AlarmFeedMapper alarmFeedMapper;
    private final InstanceRepository instanceRepository;
    private final DatabaseRepository databaseRepository;
    private final SlackNotificationService slackNotificationService;
    private final MetricConfig metricConfig;
    private final AlarmTestService alarmTestService;

    /**
     * 인위적으로 알람 발생시키기
     */
    @PostMapping("/manual")
    public Map<String, Object> triggerManualAlarm(
            @RequestParam Long alarmRuleId,
            @RequestParam(defaultValue = "CRITICAL") String severityLevel,
            @RequestParam(defaultValue = "1000000") BigDecimal currentValue
    ) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 규칙 조회
            AlarmRule rule = alarmRuleMapper.selectRuleDetail(alarmRuleId);
            if (rule == null) {
                result.put("success", false);
                result.put("error", "알람 규칙을 찾을 수 없습니다: " + alarmRuleId);
                return result;
            }

            if (!rule.getEnabled()) {
                result.put("success", false);
                result.put("error", "알람 규칙이 비활성화되어 있습니다.");
                return result;
            }

            OffsetDateTime now = OffsetDateTime.now();

            // 1. 트래킹 생성 또는 업데이트
            AlarmTracking tracking = alarmTrackingMapper.findByRuleId(alarmRuleId).orElse(null);

            if (tracking == null) {
                // 새 트래킹 생성
                tracking = AlarmTracking.builder()
                        .alarmRuleId(alarmRuleId)
                        .instanceId(rule.getInstanceId())
                        .databaseId(rule.getDatabaseId())
                        .firstTriggeredAt(now)
                        .lastCheckedAt(now)
                        .consecutiveCount(1)
                        .currentValue(currentValue)
                        .currentLevel(severityLevel)
                        .status("FIRED")
                        .build();
                alarmTrackingMapper.insertTracking(tracking);
                log.info("새 트래킹 생성: ruleId={}, trackingId={}", alarmRuleId, tracking.getAlarmTrackingId());
            } else {
                // 기존 트래킹 업데이트
                tracking.setLastCheckedAt(now);
                tracking.setConsecutiveCount(tracking.getConsecutiveCount() + 1);
                tracking.setCurrentValue(currentValue);
                tracking.setCurrentLevel(severityLevel);
                tracking.setStatus("FIRED");
                alarmTrackingMapper.updateTracking(tracking);
                log.info("트래킹 업데이트: ruleId={}, trackingId={}", alarmRuleId, tracking.getAlarmTrackingId());
            }

            // 2. 알람 피드 생성
            AlarmFeed feed = AlarmFeed.builder()
                    .alarmRuleId(alarmRuleId)
                    .alarmTrackingId(tracking.getAlarmTrackingId())
                    .instanceId(rule.getInstanceId())
                    .databaseId(rule.getDatabaseId())
                    .alarmTitle(rule.getMetricType() + " 임계치 초과 (" + severityLevel + ") ")
                    .severityLevel(severityLevel)
                    .metricType(rule.getMetricType())
                    .currentValue(currentValue)
                    .thresholdValue(BigDecimal.valueOf(1000000)) // 임시값
                    .message(String.format("%s가 임계치를 초과했습니다 (현재: %s)",
                            rule.getMetricType(), currentValue))
                    .occurredAt(now)
                    .isResolved(false)
                    .isRead(false)
                    .acknowledged(false)
                    .build();

            alarmFeedMapper.insertAlarmFeed(feed);

            Long feedId = feed.getAlarmFeedId();
            if (feedId == null) {
                result.put("success", false);
                result.put("error", "알람 피드 생성 실패");
                return result;
            }

            // 3. 지표 히스토리 저장 (차트용)
            alarmFeedMapper.insertMetricHistory(
                    feedId,
                    currentValue,
                    now
            );

            // 4. 관련 객체 저장 (DB 연결 필요)
            try {
                Instance instance = instanceRepository.findAllWithSecrets(List.of(rule.getInstanceId())).stream()
                        .findFirst()
                        .orElse(null);

                if (instance != null) {
                    List<Database> databases = databaseRepository.findDatabaseEntitiesByInstanceId(rule.getInstanceId());
                    String databaseName = databases.stream()
                            .filter(db -> db.getDatabaseId().equals(rule.getDatabaseId()))
                            .findFirst()
                            .map(Database::getDatabaseName)
                            .orElse(null);

                    if (databaseName != null) {
                        try (Connection conn = alarmTestService.createConnection(instance, databaseName)) {
                            alarmTestService.saveRelatedObjects(conn, feedId, alarmRuleId, rule.getMetricType());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("관련 객체 저장 실패 (무시): {}", e.getMessage());
            }

            // 5. Slack 알림 전송
            try {
                Instance instance = instanceRepository.findAllWithSecrets(List.of(rule.getInstanceId())).stream()
                        .findFirst()
                        .orElse(null);

                String instanceName = instance != null ? instance.getInstanceName() : "Unknown";

                List<Database> databases = databaseRepository.findDatabaseEntitiesByInstanceId(rule.getInstanceId());
                String databaseName = databases.stream()
                        .filter(db -> db.getDatabaseId().equals(rule.getDatabaseId()))
                        .findFirst()
                        .map(Database::getDatabaseName)
                        .orElse("Unknown");

                String description = String.format(
                        "%s가 임계치를 초과했습니다.\n• 현재값: %s\n• 임계치: %s\n• 발생 횟수: %d회",
                        rule.getMetricType(),
                        currentValue,
                        feed.getThresholdValue(),
                        tracking.getConsecutiveCount()
                );

                log.info("수동 알람 Slack 알림 전송 시도: instanceId={}, instanceName={}",
                        rule.getInstanceId(), instanceName);

                slackNotificationService.sendAlarmNotification(
                        rule.getInstanceId(),
                        rule.getMetricType() + " 임계치 초과 (수동 발생)",
                        severityLevel,
                        description,
                        instanceName,
                        databaseName
                );

                log.info("수동 알람 Slack 알림 전송 요청 완료");
            } catch (Exception e) {
                log.error("수동 알람 Slack 알림 전송 실패: {}", e.getMessage(), e);
            }

            result.put("success", true);
            result.put("message", "알람이 수동으로 발생되었습니다.");
            result.put("trackingId", tracking.getAlarmTrackingId());
            result.put("feedId", feedId);
            result.put("severityLevel", severityLevel);
            result.put("currentValue", currentValue);

            log.info("수동 알람 발생: ruleId={}, trackingId={}, feedId={}, level={}",
                    alarmRuleId, tracking.getAlarmTrackingId(), feedId, severityLevel);

        } catch (Exception e) {
            log.error("수동 알람 발생 실패", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 테스트용 알람 규칙 생성
     */
    @PostMapping("/create-test-rule")
    public Map<String, Object> createTestRule(
            @RequestParam Long instanceId,
            @RequestParam Long databaseId,
            @RequestParam(defaultValue = "dead_tuples") String metricType
    ) {
        Map<String, Object> result = new HashMap<>();

        try {
            AlarmRule testRule = AlarmRule.builder()
                    .instanceId(instanceId)
                    .databaseId(databaseId)
                    .metricType(metricType)
                    .operator("gt")
                    .enabled(true)
                    .levels("""
                        {
                            "critical": {"threshold": 10, "occurCount": 2, "minDurationMin": 1},
                            "warning": {"threshold": 5, "occurCount": 3, "minDurationMin": 2},
                            "notice": {"threshold": 1, "occurCount": 5, "minDurationMin": 3}
                        }
                        """)
                    .build();

            alarmRuleMapper.insertRule(testRule);

            result.put("success", true);
            result.put("message", "테스트 규칙 생성 완료");
            result.put("ruleId", testRule.getAlarmRuleId());

            log.info("테스트 규칙 생성: ruleId={}, metric={}",
                    testRule.getAlarmRuleId(), metricType);

        } catch (Exception e) {
            log.error("테스트 규칙 생성 실패", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 테스트용으로 임계치를 낮춘 규칙 생성 (autovacuum_worker_utilization 등 퍼센트 지표용)
     */
    @PostMapping("/create-test-rule-percent")
    public Map<String, Object> createTestRuleForPercentMetric(
            @RequestParam Long instanceId,
            @RequestParam Long databaseId,
            @RequestParam(defaultValue = "autovacuum_worker_utilization") String metricType
    ) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 퍼센트 지표용 임계치 (0-100 범위)
            AlarmRule testRule = AlarmRule.builder()
                    .instanceId(instanceId)
                    .databaseId(databaseId)
                    .metricType(metricType)
                    .operator("gt")
                    .enabled(true)
                    .levels("""
                        {
                            "critical": {
                                "threshold": 80,
                                "occurCount": 1,
                                "minDurationMin": 1,
                                "resolveThreshold": 70,
                                "resolveDurationMin": 2,
                                "cooldownMin": 5
                            },
                            "warning": {
                                "threshold": 60,
                                "occurCount": 1,
                                "minDurationMin": 1,
                                "resolveThreshold": 50,
                                "resolveDurationMin": 2,
                                "cooldownMin": 5
                            },
                            "notice": {
                                "threshold": 40,
                                "occurCount": 1,
                                "minDurationMin": 1,
                                "resolveThreshold": 30,
                                "resolveDurationMin": 2,
                                "cooldownMin": 5
                            }
                        }
                        """)
                    .build();

            alarmRuleMapper.insertRule(testRule);

            result.put("success", true);
            result.put("message", "테스트 규칙 생성 완료 (퍼센트 지표용)");
            result.put("ruleId", testRule.getAlarmRuleId());
            result.put("thresholds", Map.of(
                    "CRITICAL", 80,
                    "WARNING", 60,
                    "NOTICE", 40
            ));

            log.info("테스트 규칙 생성 (퍼센트): ruleId={}, metric={}, thresholds={}",
                    testRule.getAlarmRuleId(), metricType, Map.of("CRITICAL", 80, "WARNING", 60, "NOTICE", 40));

        } catch (Exception e) {
            log.error("테스트 규칙 생성 실패", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 기존 규칙의 임계치를 테스트용으로 임시 수정
     */
    @PostMapping("/adjust-thresholds")
    public Map<String, Object> adjustThresholdsForTest(
            @RequestParam Long alarmRuleId,
            @RequestParam(required = false) Double criticalThreshold,
            @RequestParam(required = false) Double warningThreshold,
            @RequestParam(required = false) Double noticeThreshold
    ) {
        Map<String, Object> result = new HashMap<>();

        try {
            AlarmRule rule = alarmRuleMapper.selectRuleDetail(alarmRuleId);
            if (rule == null) {
                result.put("success", false);
                result.put("error", "알람 규칙을 찾을 수 없습니다: " + alarmRuleId);
                return result;
            }

            Map<String, Map<String, Object>> levels = alarmTestService.parseJsonLevels(rule.getLevels());

            // 현재 값 확인
            Instance instance = instanceRepository.findAllWithSecrets(List.of(rule.getInstanceId())).stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Instance not found"));

            List<Database> databases = databaseRepository.findDatabaseEntitiesByInstanceId(rule.getInstanceId());
            String databaseName = databases.stream()
                    .filter(db -> db.getDatabaseId().equals(rule.getDatabaseId()))
                    .findFirst()
                    .map(Database::getDatabaseName)
                    .orElseThrow(() -> new RuntimeException("Database not found"));

            BigDecimal currentValue = null;
            try (Connection conn = alarmTestService.createConnection(instance, databaseName)) {
                String sql = metricConfig.getMetricQuery(rule.getMetricType());
                if (sql != null) {
                    boolean needsParams = sql.contains("?");
                    if (needsParams) {
                        try (var pstmt = conn.prepareStatement(sql)) {
                            pstmt.setLong(1, rule.getInstanceId());
                            pstmt.setLong(2, rule.getDatabaseId());
                            try (var rs = pstmt.executeQuery()) {
                                if (rs.next()) {
                                    currentValue = rs.getBigDecimal(1);
                                }
                            }
                        }
                    } else {
                        try (var stmt = conn.createStatement();
                             var rs = stmt.executeQuery(sql)) {
                            if (rs.next()) {
                                currentValue = rs.getBigDecimal(1);
                            }
                        }
                    }
                }
            }

            // 현재 값보다 약간 높은 값으로 임계치 설정 (테스트용)
            if (currentValue != null) {
                double current = currentValue.doubleValue();

                // autovacuum_worker_utilization 같은 퍼센트 지표인 경우
                if (rule.getMetricType().contains("utilization") || rule.getMetricType().contains("ratio") ||
                        rule.getMetricType().contains("progress")) {
                    // 퍼센트 값이므로 0-100 범위
                    if (criticalThreshold == null) criticalThreshold = Math.min(current + 10, 100.0);
                    if (warningThreshold == null) warningThreshold = Math.min(current + 5, 95.0);
                    if (noticeThreshold == null) noticeThreshold = Math.min(current + 2, 90.0);
                } else {
                    // 일반 지표
                    if (criticalThreshold == null) criticalThreshold = current * 1.1;
                    if (warningThreshold == null) warningThreshold = current * 1.05;
                    if (noticeThreshold == null) noticeThreshold = current * 1.02;
                }
            } else {
                // 현재 값을 알 수 없는 경우 기본값 사용
                if (criticalThreshold == null) criticalThreshold = 80.0;
                if (warningThreshold == null) warningThreshold = 60.0;
                if (noticeThreshold == null) noticeThreshold = 40.0;
            }

            // levels 업데이트
            for (String level : List.of("critical", "warning", "notice")) {
                Map<String, Object> levelConfig = levels.get(level);
                if (levelConfig != null) {
                    double threshold = switch (level) {
                        case "critical" -> criticalThreshold;
                        case "warning" -> warningThreshold;
                        case "notice" -> noticeThreshold;
                        default -> 0.0;
                    };

                    levelConfig.put("threshold", threshold);

                    // 복구 임계치도 자동 설정 (발생 임계치의 80%)
                    if (rule.getOperator().equals("gt") || rule.getOperator().equals("gte")) {
                        levelConfig.put("resolveThreshold", threshold * 0.8);
                    } else {
                        levelConfig.put("resolveThreshold", threshold * 1.2);
                    }

                    // 테스트용으로 발생 조건 완화
                    levelConfig.put("occurCount", 1);
                    levelConfig.put("minDurationMin", 1);
                    levelConfig.put("resolveDurationMin", 2);
                    levelConfig.put("cooldownMin", 5);
                }
            }

            // JSON으로 변환
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String levelsJson = mapper.writeValueAsString(levels);

            // 규칙 업데이트
            AlarmRule updatedRule = AlarmRule.builder()
                    .alarmRuleId(alarmRuleId)
                    .levels(levelsJson)
                    .enabled(rule.getEnabled())
                    .aggregationType(rule.getAggregationType())
                    .operator(rule.getOperator())
                    .build();

            alarmRuleMapper.updateRuleLevels(updatedRule);

            result.put("success", true);
            result.put("message", "임계치가 테스트용으로 수정되었습니다.");
            result.put("ruleId", alarmRuleId);
            result.put("currentValue", currentValue != null ? currentValue.toString() : "N/A");
            result.put("newThresholds", Map.of(
                    "CRITICAL", criticalThreshold,
                    "WARNING", warningThreshold,
                    "NOTICE", noticeThreshold
            ));
            result.put("note", "테스트 후 원래 값으로 복구하세요.");

            log.info("임계치 수정: ruleId={}, currentValue={}, newThresholds={}",
                    alarmRuleId, currentValue, Map.of("CRITICAL", criticalThreshold, "WARNING", warningThreshold, "NOTICE", noticeThreshold));

        } catch (Exception e) {
            log.error("임계치 수정 실패", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 복구 임계치 테스트
     */
    @PostMapping("/test-resolve-threshold")
    public Map<String, Object> testResolveThreshold(
            @RequestParam Long alarmRuleId,
            @RequestParam(defaultValue = "1000000") BigDecimal triggerValue,
            @RequestParam(defaultValue = "500000") BigDecimal resolveValue
    ) {
        Map<String, Object> result = new HashMap<>();

        try {
            AlarmRule rule = alarmRuleMapper.selectRuleDetail(alarmRuleId);
            if (rule == null) {
                result.put("success", false);
                result.put("error", "알람 규칙을 찾을 수 없습니다: " + alarmRuleId);
                return result;
            }

            if (!rule.getEnabled()) {
                result.put("success", false);
                result.put("error", "알람 규칙이 비활성화되어 있습니다.");
                return result;
            }

            result.put("ruleInfo", Map.of(
                    "ruleId", rule.getAlarmRuleId(),
                    "metricType", rule.getMetricType(),
                    "operator", rule.getOperator(),
                    "levels", rule.getLevels()
            ));

            // 복구 임계치 정보 조회
            AlarmTracking tracking = alarmTrackingMapper.findByRuleId(alarmRuleId).orElse(null);
            if (tracking != null) {
                String currentLevel = tracking.getCurrentLevel();
                Map<String, Map<String, Object>> levels = alarmTestService.parseJsonLevels(rule.getLevels());
                Map<String, Object> levelConfig = levels.get(currentLevel.toLowerCase());

                BigDecimal resolveThreshold = null;
                Integer resolveDuration = null;
                if (levelConfig != null) {
                    Object resolveThresholdObj = levelConfig.get("resolveThreshold");
                    if (resolveThresholdObj != null) {
                        if (resolveThresholdObj instanceof Number) {
                            resolveThreshold = BigDecimal.valueOf(((Number) resolveThresholdObj).doubleValue());
                        } else if (resolveThresholdObj instanceof String) {
                            resolveThreshold = new BigDecimal((String) resolveThresholdObj);
                        }
                    }

                    Object resolveDurationObj = levelConfig.get("resolveDurationMin");
                    if (resolveDurationObj != null) {
                        if (resolveDurationObj instanceof Number) {
                            resolveDuration = ((Number) resolveDurationObj).intValue();
                        } else if (resolveDurationObj instanceof String) {
                            resolveDuration = Integer.parseInt((String) resolveDurationObj);
                        }
                    }
                }

                result.put("resolveThresholdInfo", Map.of(
                        "resolveThreshold", resolveThreshold != null ? resolveThreshold.toString() : "기본값 (발생 임계치의 80%)",
                        "resolveDurationMin", resolveDuration != null ? resolveDuration : "기본값 (3분)",
                        "currentLevel", currentLevel,
                        "note", "실제 복구는 다음 알람 체크 시점에 자동으로 처리됩니다."
                ));
            }

            result.put("success", true);
            result.put("message", "복구 임계치 테스트 정보 조회 완료");

        } catch (Exception e) {
            log.error("복구 임계치 테스트 실패", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 쿨다운 테스트
     */
    @PostMapping("/test-cooldown")
    public Map<String, Object> testCooldown(@RequestParam Long alarmRuleId) {
        Map<String, Object> result = new HashMap<>();

        try {
            AlarmRule rule = alarmRuleMapper.selectRuleDetail(alarmRuleId);
            if (rule == null) {
                result.put("success", false);
                result.put("error", "알람 규칙을 찾을 수 없습니다: " + alarmRuleId);
                return result;
            }

            // 쿨다운 정보 조회
            Map<String, Map<String, Object>> levels = alarmTestService.parseJsonLevels(rule.getLevels());
            Map<String, Object> cooldownInfo = new HashMap<>();
            for (String level : List.of("critical", "warning", "notice")) {
                Map<String, Object> levelConfig = levels.get(level);
                if (levelConfig != null) {
                    Object cooldownObj = levelConfig.get("cooldownMin");
                    Integer cooldown = null;
                    if (cooldownObj != null) {
                        if (cooldownObj instanceof Number) {
                            cooldown = ((Number) cooldownObj).intValue();
                        } else if (cooldownObj instanceof String) {
                            cooldown = Integer.parseInt((String) cooldownObj);
                        }
                    }
                    cooldownInfo.put(level, cooldown != null ? cooldown + "분" : "설정 안됨");
                }
            }

            result.put("success", true);
            result.put("cooldownInfo", cooldownInfo);
            result.put("message", "쿨다운 정보 조회 완료");

        } catch (Exception e) {
            log.error("쿨다운 테스트 실패", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Slack 테스트
     */
    @PostMapping("/slack-test")
    public ResponseEntity<String> testSlackNotification() {
        slackNotificationService.sendAlarmNotification(
                "테스트 알람",
                "WARNING",
                "이것은 Slack 연동 테스트 메시지입니다.",
                "test-instance",
                "test-database"
        );
        return ResponseEntity.ok("Slack 테스트 알림 전송됨");
    }
}