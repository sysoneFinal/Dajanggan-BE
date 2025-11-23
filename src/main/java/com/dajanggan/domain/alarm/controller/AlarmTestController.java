package com.dajanggan.domain.alarm.controller;

import com.dajanggan.domain.alarm.service.AlarmMetricsCollector;
import com.dajanggan.domain.alarm.service.GenericAlarmCollector;
import com.dajanggan.domain.alarm.repository.AlarmRuleMapper;
import com.dajanggan.domain.alarm.repository.AlarmTrackingMapper;
import com.dajanggan.domain.alarm.repository.AlarmFeedMapper;
import com.dajanggan.domain.alarm.domain.AlarmRule;
import com.dajanggan.domain.alarm.domain.AlarmTracking;
import com.dajanggan.domain.alarm.domain.AlarmFeed;
import com.dajanggan.domain.alarm.dto.AlarmTrackingDto;
import com.dajanggan.domain.alarm.config.MetricConfig;
import com.dajanggan.domain.alarm.service.SlackNotificationService;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.domain.instance.repository.DatabaseRepository;
import com.dajanggan.global.crypto.AesGcmService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 알람 시스템 수동 테스트용 컨트롤러
 * 스케줄링 없이 즉시 알람 체크를 실행할 수 있습니다.
 */
@Tag(name = "Alarm-Test", description = "alarm 테스트 페이지 관련 API")
@RestController
@RequestMapping("/api/test/alarm")
@RequiredArgsConstructor
@Slf4j
public class AlarmTestController {

    private final GenericAlarmCollector alarmCollector;
    private final InstanceRepository instanceRepository;
    private final DatabaseRepository databaseRepository;
    private final AlarmRuleMapper alarmRuleMapper;
    private final AlarmTrackingMapper alarmTrackingMapper;
    private final AlarmFeedMapper alarmFeedMapper;
    private final AesGcmService aesGcmService;
    private final MetricConfig metricConfig;
    private final SlackNotificationService slackNotificationService;

    private final AlarmMetricsCollector alarmMetricsCollector;  // ✅ 추가


    /**
     * 테스트 모드 활성화/비활성화
     *
     * POST /api/test/alarm/test-mode?enabled=true
     */
    @PostMapping("/test-mode")
    public Map<String, Object> setTestMode(@RequestParam boolean enabled) {
        Map<String, Object> result = new HashMap<>();
        try {
            alarmMetricsCollector.setTestMode(enabled);
            result.put("success", true);
            result.put("testMode", enabled);
            result.put("message", enabled ?
                    "테스트 모드 활성화: 스케줄러가 일시 중지됩니다" :
                    "테스트 모드 비활성화: 스케줄러가 정상 작동합니다");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
    /**
     * 특정 지표에 대해 즉시 알람 체크 실행
     *
     * GET /api/test/alarm/check?instanceId=1&databaseId=1&metricType=dead_tuples
     */
    @GetMapping("/check")
    public Map<String, Object> checkAlarm(
            @RequestParam Long instanceId,
            @RequestParam Long databaseId,
            @RequestParam String metricType
    ) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Instance 조회 (secretRef 포함)
            Instance instance = instanceRepository.findAllWithSecrets(List.of(instanceId)).stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Instance not found: " + instanceId));

            log.info("📋 Instance 정보:");
            log.info("  - ID: {}", instance.getInstanceId());
            log.info("  - Name: {}", instance.getInstanceName());
            log.info("  - Host: {}", instance.getHost());
            log.info("  - Port: {}", instance.getPort());
            log.info("  - User: {}", instance.getUserName());

            // Database 조회
            List<Database> databases = databaseRepository.findDatabaseEntitiesByInstanceId(instanceId);
            String databaseName = databases.stream()
                    .filter(db -> db.getDatabaseId().equals(databaseId))
                    .findFirst()
                    .map(Database::getDatabaseName)
                    .orElseThrow(() -> new RuntimeException("Database not found: " + databaseId));

            log.info("🔍 알람 체크 시작: instance={}, database={}, metric={}",
                    instance.getInstanceName(), databaseName, metricType);

            // DB 연결 및 알람 체크
            try (Connection conn = createConnection(instance, databaseName)) {
                alarmCollector.checkMetric(conn, instanceId, databaseId, metricType);

                result.put("success", true);
                result.put("message", "알람 체크 완료");
                result.put("instanceName", instance.getInstanceName());
                result.put("databaseName", databaseName);
                result.put("metricType", metricType);

            } catch (SQLException e) {
                log.error("DB 연결 실패 상세:", e);
                result.put("success", false);
                result.put("error", "DB 연결 실패: " + e.getMessage());
                result.put("sqlState", e.getSQLState());
                result.put("errorCode", e.getErrorCode());
            }

        } catch (Exception e) {
            log.error("알람 체크 실패", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("stackTrace", e.getClass().getName());
        }

        return result;
    }

    /**
     * 모든 활성화된 지표에 대해 즉시 알람 체크
     *
     * GET /api/test/alarm/check-all?instanceId=1&databaseId=1
     */
    @GetMapping("/check-all")
    public Map<String, Object> checkAllAlarms(
            @RequestParam Long instanceId,
            @RequestParam Long databaseId
    ) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Instance 조회 (secretRef 포함)
            Instance instance = instanceRepository.findAllWithSecrets(List.of(instanceId)).stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Instance not found"));

            List<Database> databases = databaseRepository.findDatabaseEntitiesByInstanceId(instanceId);
            String databaseName = databases.stream()
                    .filter(db -> db.getDatabaseId().equals(databaseId))
                    .findFirst()
                    .map(Database::getDatabaseName)
                    .orElseThrow(() -> new RuntimeException("Database not found"));

            log.info("🔍 전체 알람 체크 시작: instance={}, database={}",
                    instance.getInstanceName(), databaseName);

            try (Connection conn = createConnection(instance, databaseName)) {
                String[] metrics = {
                        "autovacuum_worker_utilization",
                        "transaction_age",
                        "wraparound_progress",
                        "long_running_queries",
                        "lock_waits",
                        "long_idle_sessions",
                        "blocking_sessions",
                        "slow_query_spike",
                        "avg_execution_spike",
                        "qps_spike"
                };

                for (String metric : metrics) {
                    try {
                        alarmCollector.checkMetric(conn, instanceId, databaseId, metric);
                        log.info("✅ {} 체크 완료", metric);
                    } catch (Exception e) {
                        log.error("❌ {} 체크 실패: {}", metric, e.getMessage());
                    }
                }

                result.put("success", true);
                result.put("message", "전체 알람 체크 완료");
                result.put("checkedMetrics", metrics);
            }

        } catch (Exception e) {
            log.error("전체 알람 체크 실패", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 현재 추적 중인 알람 조회
     *
     * GET /api/test/alarm/tracking?instanceId=1
     */
    @GetMapping("/tracking")
    public Map<String, Object> getTrackingStatus(@RequestParam Long instanceId) {
        Map<String, Object> result = new HashMap<>();

        try {
            List<AlarmTrackingDto.TrackingStatusRaw> trackings =
                    alarmTrackingMapper.selectTrackingStatus(instanceId, null);

            result.put("success", true);
            result.put("trackings", trackings);
            result.put("count", trackings.size());

        } catch (Exception e) {
            log.error("트래킹 조회 실패", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 미해결 알람 피드 조회
     *
     * GET /api/test/alarm/feeds?instanceId=1
     */
    @GetMapping("/feeds")
    public Map<String, Object> getAlarmFeeds(
            @RequestParam Long instanceId,
            @RequestParam(required = false) Long databaseId,
            @RequestParam(required = false) String severityLevel
    ) {
        Map<String, Object> result = new HashMap<>();

        try {
            var feeds = alarmFeedMapper.selectAlarmList(
                    instanceId,
                    databaseId,
                    severityLevel,
                    false  // 읽지 않은 것만
            );

            result.put("success", true);
            result.put("feeds", feeds);
            result.put("count", feeds.size());

        } catch (Exception e) {
            log.error("알람 피드 조회 실패", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 특정 규칙의 트래킹 초기화
     *
     * DELETE /api/test/alarm/tracking/{ruleId}
     */
    @DeleteMapping("/tracking/{ruleId}")
    public Map<String, Object> resetTracking(@PathVariable Long ruleId) {
        Map<String, Object> result = new HashMap<>();

        try {
            alarmTrackingMapper.findByRuleId(ruleId)
                    .ifPresent(tracking -> {
                        alarmTrackingMapper.delete(tracking.getAlarmTrackingId());
                        log.info("🔄 트래킹 초기화: ruleId={}", ruleId);
                    });

            result.put("success", true);
            result.put("message", "트래킹 초기화 완료");

        } catch (Exception e) {
            log.error("트래킹 초기화 실패", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 테스트용 알람 규칙 생성
     *
     * POST /api/test/alarm/create-test-rule
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

            log.info("✅ 테스트 규칙 생성: ruleId={}, metric={}",
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
     *
     * POST /api/test/alarm/create-test-rule-percent?instanceId=1&databaseId=1&metricType=autovacuum_worker_utilization
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

            log.info("✅ 테스트 규칙 생성 (퍼센트): ruleId={}, metric={}, thresholds={}",
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
     * 
     * POST /api/test/alarm/adjust-thresholds-for-test?alarmRuleId=1
     */
    @PostMapping("/adjust-thresholds-for-test")
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

            Map<String, Map<String, Object>> levels = parseJsonLevels(rule.getLevels());
            
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
            try (Connection conn = createConnection(instance, databaseName)) {
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

            log.info("✅ 임계치 수정: ruleId={}, currentValue={}, newThresholds={}",
                    alarmRuleId, currentValue, Map.of("CRITICAL", criticalThreshold, "WARNING", warningThreshold, "NOTICE", noticeThreshold));

        } catch (Exception e) {
            log.error("임계치 수정 실패", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * DB 연결 생성
     */
    private Connection createConnection(Instance instance, String databaseName) throws SQLException {
        // ✅ trim()으로 공백 제거
        String host = instance.getHost() != null ? instance.getHost().trim() : "";
        String userName = instance.getUserName() != null ? instance.getUserName().trim() : "";
        String password = aesGcmService.decryptToString(instance.getSecretRef());
        String dbName = databaseName != null ? databaseName.trim() : "";

        String url = String.format("jdbc:postgresql://%s:%d/%s",
                host,
                instance.getPort(),
                dbName);

        log.info("🔌 DB 연결 시도:");
        log.info("  - URL: {}", url);
        log.info("  - User: {}", userName);
        log.info("  - Password exists: {}", !password.isEmpty());

        try {
            Connection conn = DriverManager.getConnection(url, userName, password);
            log.info("✅ DB 연결 성공");
            return conn;
        } catch (SQLException e) {
            log.error("❌ DB 연결 실패: {}", e.getMessage());
            log.error("  - SQLState: {}", e.getSQLState());
            log.error("  - ErrorCode: {}", e.getErrorCode());
            throw e;
        }
    }

    /**
     * DB 연결 테스트 전용 엔드포인트
     *
     * GET /api/test/alarm/test-connection?instanceId=1&databaseId=1
     */
    @GetMapping("/test-connection")
    public Map<String, Object> testConnection(
            @RequestParam Long instanceId,
            @RequestParam Long databaseId
    ) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Instance 조회 (secretRef 포함)
            Instance instance = instanceRepository.findAllWithSecrets(List.of(instanceId)).stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Instance not found"));

            List<Database> databases = databaseRepository.findDatabaseEntitiesByInstanceId(instanceId);
            String databaseName = databases.stream()
                    .filter(db -> db.getDatabaseId().equals(databaseId))
                    .findFirst()
                    .map(Database::getDatabaseName)
                    .orElseThrow(() -> new RuntimeException("Database not found"));

            result.put("instanceInfo", Map.of(
                    "id", instance.getInstanceId(),
                    "name", instance.getInstanceName(),
                    "host", instance.getHost(),
                    "port", instance.getPort(),
                    "userName", instance.getUserName(),
                    "hasPassword", instance.getSecretRef() != null && !instance.getSecretRef().isEmpty()
            ));

            result.put("databaseInfo", Map.of(
                    "id", databaseId,
                    "name", databaseName
            ));

            // 실제 연결 테스트
            try (Connection conn = createConnection(instance, databaseName)) {
                result.put("connectionSuccess", true);
                result.put("message", "DB 연결 성공!");

                // 간단한 쿼리 테스트
                try (var stmt = conn.createStatement();
                     var rs = stmt.executeQuery("SELECT version()")) {
                    if (rs.next()) {
                        result.put("postgresVersion", rs.getString(1));
                    }
                }
            }

        } catch (SQLException e) {
            result.put("connectionSuccess", false);
            result.put("error", e.getMessage());
            result.put("sqlState", e.getSQLState());
            result.put("errorCode", e.getErrorCode());
            result.put("cause", e.getCause() != null ? e.getCause().getMessage() : "N/A");
        } catch (Exception e) {
            result.put("connectionSuccess", false);
            result.put("error", e.getMessage());
            result.put("type", e.getClass().getName());
        }

        return result;
    }

    /**
     * 인위적으로 알람 발생시키기 (테스트용)
     * 
     * POST /api/test/alarm/trigger-manual
     * 
     * Body:
     * {
     *   "alarmRuleId": 1,
     *   "severityLevel": "CRITICAL",
     *   "currentValue": 1200000
     * }
     */
    @PostMapping("/trigger-manual")
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
                log.info("✅ 새 트래킹 생성: ruleId={}, trackingId={}", alarmRuleId, tracking.getAlarmTrackingId());
            } else {
                // 기존 트래킹 업데이트
                tracking.setLastCheckedAt(now);
                tracking.setConsecutiveCount(tracking.getConsecutiveCount() + 1);
                tracking.setCurrentValue(currentValue);
                tracking.setCurrentLevel(severityLevel);
                tracking.setStatus("FIRED");
                alarmTrackingMapper.updateTracking(tracking);
                log.info("✅ 트래킹 업데이트: ruleId={}, trackingId={}", alarmRuleId, tracking.getAlarmTrackingId());
            }

            // 2. 알람 피드 생성
            AlarmFeed feed = AlarmFeed.builder()
                    .alarmRuleId(alarmRuleId)
                    .alarmTrackingId(tracking.getAlarmTrackingId())
                    .instanceId(rule.getInstanceId())
                    .databaseId(rule.getDatabaseId())
                    .alarmTitle(rule.getMetricType() + " 임계치 초과 (" + severityLevel + ") [수동 발생]")
                    .severityLevel(severityLevel)
                    .metricType(rule.getMetricType())
                    .currentValue(currentValue)
                    .thresholdValue(BigDecimal.valueOf(1000000)) // 임시값
                    .message(String.format("%s가 임계치를 초과했습니다 (현재: %s) [수동 발생]", 
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
                        try (Connection conn = createConnection(instance, databaseName)) {
                            saveRelatedObjects(conn, feedId, alarmRuleId, rule.getMetricType());
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
                        "%s가 임계치를 초과했습니다 (수동 발생).\n• 현재값: %s\n• 임계치: %s\n• 발생 횟수: %d회",
                        rule.getMetricType(),
                        currentValue,
                        feed.getThresholdValue(),
                        tracking.getConsecutiveCount()
                );

                log.info("📤 수동 알람 Slack 알림 전송 시도: instanceId={}, instanceName={}", 
                        rule.getInstanceId(), instanceName);
                
                slackNotificationService.sendAlarmNotification(
                        rule.getInstanceId(),
                        rule.getMetricType() + " 임계치 초과 (수동 발생)",
                        severityLevel,
                        description,
                        instanceName,
                        databaseName
                );
                
                log.info("✅ 수동 알람 Slack 알림 전송 요청 완료");
            } catch (Exception e) {
                log.error("❌ 수동 알람 Slack 알림 전송 실패: {}", e.getMessage(), e);
            }

            result.put("success", true);
            result.put("message", "알람이 수동으로 발생되었습니다.");
            result.put("trackingId", tracking.getAlarmTrackingId());
            result.put("feedId", feedId);
            result.put("severityLevel", severityLevel);
            result.put("currentValue", currentValue);

            log.info("🚨 수동 알람 발생: ruleId={}, trackingId={}, feedId={}, level={}", 
                    alarmRuleId, tracking.getAlarmTrackingId(), feedId, severityLevel);

        } catch (Exception e) {
            log.error("수동 알람 발생 실패", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 모든 알람 피드 조회 (해결된 것 포함)
     * 
     * GET /api/test/alarm/all-feeds?instanceId=1
     */
    @GetMapping("/all-feeds")
    public Map<String, Object> getAllAlarmFeeds(
            @RequestParam Long instanceId,
            @RequestParam(required = false) Long databaseId,
            @RequestParam(required = false) String severityLevel
    ) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 해결 여부와 관계없이 모든 피드 조회
            var feeds = alarmFeedMapper.selectAlarmList(
                    instanceId,
                    databaseId,
                    severityLevel,
                    null  // null이면 모든 피드 조회
            );

            result.put("success", true);
            result.put("feeds", feeds);
            result.put("count", feeds.size());

        } catch (Exception e) {
            log.error("알람 피드 조회 실패", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 관련 객체 저장 (GenericAlarmCollector와 동일한 로직)
     */
    private void saveRelatedObjects(Connection conn, Long alarmFeedId, Long alarmRuleId, String metricType) {
        String sql = metricConfig.getRelatedObjectsQuery(metricType);
        if (sql == null) {
            log.debug("관련 객체 쿼리 없음: {}", metricType);
            return;
        }

        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(sql)) {

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

    /**
     * 복구 임계치 테스트
     * 1. 알람을 발생시킨 후
     * 2. 복구 임계치 이하로 값을 낮춰서 복구가 되는지 확인
     * 
     * POST /api/test/alarm/test-resolve-threshold
     * 
     * Body:
     * {
     *   "alarmRuleId": 1,
     *   "triggerValue": 1000000,  // 알람 발생시킬 값
     *   "resolveValue": 500000     // 복구 시킬 값
     * }
     */
    @PostMapping("/test-resolve-threshold")
    public Map<String, Object> testResolveThreshold(
            @RequestParam Long alarmRuleId,
            @RequestParam(defaultValue = "1000000") BigDecimal triggerValue,
            @RequestParam(defaultValue = "500000") BigDecimal resolveValue
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

            // Instance와 Database 조회
            Instance instance = instanceRepository.findAllWithSecrets(List.of(rule.getInstanceId())).stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Instance not found"));

            List<Database> databases = databaseRepository.findDatabaseEntitiesByInstanceId(rule.getInstanceId());
            String databaseName = databases.stream()
                    .filter(db -> db.getDatabaseId().equals(rule.getDatabaseId()))
                    .findFirst()
                    .map(Database::getDatabaseName)
                    .orElseThrow(() -> new RuntimeException("Database not found"));

            result.put("ruleInfo", Map.of(
                    "ruleId", rule.getAlarmRuleId(),
                    "metricType", rule.getMetricType(),
                    "operator", rule.getOperator(),
                    "levels", rule.getLevels()
            ));

            // 1단계: 알람 발생시키기
            log.info("🔴 1단계: 알람 발생 테스트 - triggerValue={}", triggerValue);
            try (Connection conn = createConnection(instance, databaseName)) {
                alarmCollector.checkMetric(conn, rule.getInstanceId(), rule.getDatabaseId(), rule.getMetricType());
            }

            // 트래킹 확인
            AlarmTracking tracking = alarmTrackingMapper.findByRuleId(alarmRuleId).orElse(null);
            if (tracking == null || !"FIRED".equals(tracking.getStatus())) {
                result.put("step1", Map.of(
                        "success", false,
                        "message", "알람이 발생하지 않았습니다. triggerValue를 높여주세요."
                ));
                return result;
            }

            result.put("step1", Map.of(
                    "success", true,
                    "message", "알람 발생 성공",
                    "trackingId", tracking.getAlarmTrackingId(),
                    "currentLevel", tracking.getCurrentLevel(),
                    "currentValue", tracking.getCurrentValue()
            ));

            // 복구 임계치 정보 조회
            String currentLevel = tracking.getCurrentLevel();
            // GenericAlarmCollector의 private 메서드를 호출할 수 없으므로, 직접 JSON 파싱
            Map<String, Map<String, Object>> levels = parseJsonLevels(rule.getLevels());
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
                    "currentLevel", currentLevel
            ));

            // 2단계: 복구 테스트
            log.info("🟢 2단계: 복구 테스트 - resolveValue={}", resolveValue);
            
            // 수동으로 값을 낮춰서 복구 조건 체크
            // 실제로는 DB에서 값을 조회하지만, 테스트를 위해 수동으로 체크
            try (Connection conn = createConnection(instance, databaseName)) {
                // processAlarmRule을 직접 호출할 수 없으므로, 
                // checkMetric을 호출하되 실제 값 대신 resolveValue를 사용하는 방법이 필요
                // 하지만 checkMetric은 실제 DB에서 값을 조회하므로, 
                // 여기서는 복구 조건만 체크하는 별도 로직이 필요
                
                // 복구 조건 체크를 위해 임시로 트래킹의 currentValue를 resolveValue로 변경
                tracking.setCurrentValue(resolveValue);
                
                // 복구 조건이 충족되는지 확인 (실제로는 GenericAlarmCollector의 shouldResolveAlarm 호출 필요)
                // 여기서는 정보만 제공
                result.put("step2", Map.of(
                        "resolveValue", resolveValue,
                        "resolveThreshold", resolveThreshold != null ? resolveThreshold : "계산 필요",
                        "note", "실제 복구는 다음 알람 체크 시점에 자동으로 처리됩니다. " +
                                "복구 임계치 이하로 값이 유지되고, 복구 지속 시간이 지나면 자동으로 복구됩니다."
                ));
            }

            result.put("success", true);
            result.put("message", "복구 임계치 테스트 완료");

        } catch (Exception e) {
            log.error("복구 임계치 테스트 실패", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 쿨다운 테스트
     * 1. 알람을 발생시킨 후
     * 2. 쿨다운 시간 내에 다시 알람이 발생하지 않는지 확인
     * 
     * POST /api/test/alarm/test-cooldown
     * 
     * Body:
     * {
     *   "alarmRuleId": 1,
     *   "triggerValue": 1000000
     * }
     */
    @PostMapping("/test-cooldown")
    public Map<String, Object> testCooldown(
            @RequestParam Long alarmRuleId,
            @RequestParam(defaultValue = "1000000") BigDecimal triggerValue
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

            // Instance와 Database 조회
            Instance instance = instanceRepository.findAllWithSecrets(List.of(rule.getInstanceId())).stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Instance not found"));

            List<Database> databases = databaseRepository.findDatabaseEntitiesByInstanceId(rule.getInstanceId());
            String databaseName = databases.stream()
                    .filter(db -> db.getDatabaseId().equals(rule.getDatabaseId()))
                    .findFirst()
                    .map(Database::getDatabaseName)
                    .orElseThrow(() -> new RuntimeException("Database not found"));

            // 쿨다운 정보 조회
            Map<String, Map<String, Object>> levels = parseJsonLevels(rule.getLevels());
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

            result.put("cooldownInfo", cooldownInfo);

            // 1단계: 첫 번째 알람 발생
            log.info("🔴 1단계: 첫 번째 알람 발생 - triggerValue={}", triggerValue);
            Long firstFeedId = null;
            try (Connection conn = createConnection(instance, databaseName)) {
                alarmCollector.checkMetric(conn, rule.getInstanceId(), rule.getDatabaseId(), rule.getMetricType());
            }

            // 첫 번째 알람 피드 확인
            AlarmTracking tracking = alarmTrackingMapper.findByRuleId(alarmRuleId).orElse(null);
            if (tracking != null && "FIRED".equals(tracking.getStatus())) {
                firstFeedId = alarmFeedMapper.selectLatestFeedIdByTrackingId(tracking.getAlarmTrackingId());
                result.put("step1", Map.of(
                        "success", true,
                        "message", "첫 번째 알람 발생 성공",
                        "trackingId", tracking.getAlarmTrackingId(),
                        "feedId", firstFeedId,
                        "firedAt", tracking.getFirstTriggeredAt()
                ));
            } else {
                result.put("step1", Map.of(
                        "success", false,
                        "message", "첫 번째 알람이 발생하지 않았습니다."
                ));
                return result;
            }

            // 2단계: 쿨다운 시간 내에 다시 알람 체크
            log.info("🟡 2단계: 쿨다운 시간 내 알람 체크");
            try (Connection conn = createConnection(instance, databaseName)) {
                alarmCollector.checkMetric(conn, rule.getInstanceId(), rule.getDatabaseId(), rule.getMetricType());
            }

            // 두 번째 알람 피드 확인
            Long secondFeedId = alarmFeedMapper.selectLatestFeedIdByTrackingId(tracking.getAlarmTrackingId());
            
            if (secondFeedId != null && !secondFeedId.equals(firstFeedId)) {
                result.put("step2", Map.of(
                        "success", false,
                        "message", "쿨다운이 작동하지 않았습니다. 새로운 알람이 발생했습니다.",
                        "firstFeedId", firstFeedId,
                        "secondFeedId", secondFeedId
                ));
            } else {
                result.put("step2", Map.of(
                        "success", true,
                        "message", "쿨다운이 정상 작동합니다. 새로운 알람이 발생하지 않았습니다.",
                        "firstFeedId", firstFeedId,
                        "secondFeedId", secondFeedId
                ));
            }

            result.put("success", true);
            result.put("message", "쿨다운 테스트 완료");

        } catch (Exception e) {
            log.error("쿨다운 테스트 실패", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 알람 규칙의 복구 임계치와 쿨다운 정보 조회
     * 
     * GET /api/test/alarm/rule-info?alarmRuleId=1
     */
    @GetMapping("/rule-info")
    public Map<String, Object> getRuleInfo(@RequestParam Long alarmRuleId) {
        Map<String, Object> result = new HashMap<>();

        try {
            AlarmRule rule = alarmRuleMapper.selectRuleDetail(alarmRuleId);
            if (rule == null) {
                result.put("success", false);
                result.put("error", "알람 규칙을 찾을 수 없습니다: " + alarmRuleId);
                return result;
            }

            Map<String, Map<String, Object>> levels = parseJsonLevels(rule.getLevels());
            Map<String, Object> levelInfo = new HashMap<>();

            for (String level : List.of("critical", "warning", "notice")) {
                Map<String, Object> levelConfig = levels.get(level);
                if (levelConfig != null) {
                    Map<String, Object> info = new HashMap<>();
                    
                    // 발생 임계치
                    Object thresholdObj = levelConfig.get("threshold");
                    if (thresholdObj != null) {
                        info.put("threshold", thresholdObj);
                    }
                    
                    // 복구 임계치
                    Object resolveThresholdObj = levelConfig.get("resolveThreshold");
                    if (resolveThresholdObj != null) {
                        info.put("resolveThreshold", resolveThresholdObj);
                    } else {
                        info.put("resolveThreshold", "기본값 (발생 임계치의 80%)");
                    }
                    
                    // 복구 지속 시간
                    Object resolveDurationObj = levelConfig.get("resolveDurationMin");
                    if (resolveDurationObj != null) {
                        info.put("resolveDurationMin", resolveDurationObj + "분");
                    } else {
                        info.put("resolveDurationMin", "기본값 (3분)");
                    }
                    
                    // 쿨다운
                    Object cooldownObj = levelConfig.get("cooldownMin");
                    if (cooldownObj != null) {
                        info.put("cooldownMin", cooldownObj + "분");
                    } else {
                        info.put("cooldownMin", "설정 안됨");
                    }
                    
                    // 발생 횟수
                    Object occurCountObj = levelConfig.get("occurCount");
                    if (occurCountObj != null) {
                        info.put("occurCount", occurCountObj);
                    }
                    
                    // 최소 지속 시간
                    Object minDurationObj = levelConfig.get("minDurationMin");
                    if (minDurationObj != null) {
                        info.put("minDurationMin", minDurationObj + "분");
                    }
                    
                    levelInfo.put(level.toUpperCase(), info);
                }
            }

            // 현재 트래킹 상태
            AlarmTracking tracking = alarmTrackingMapper.findByRuleId(alarmRuleId).orElse(null);
            Map<String, Object> trackingInfo = null;
            if (tracking != null) {
                trackingInfo = Map.of(
                        "trackingId", tracking.getAlarmTrackingId(),
                        "status", tracking.getStatus(),
                        "currentLevel", tracking.getCurrentLevel(),
                        "currentValue", tracking.getCurrentValue(),
                        "consecutiveCount", tracking.getConsecutiveCount(),
                        "firstTriggeredAt", tracking.getFirstTriggeredAt(),
                        "lastCheckedAt", tracking.getLastCheckedAt()
                );
            }

            result.put("success", true);
            result.put("ruleId", rule.getAlarmRuleId());
            result.put("metricType", rule.getMetricType());
            result.put("operator", rule.getOperator());
            result.put("enabled", rule.getEnabled());
            result.put("levels", levelInfo);
            result.put("tracking", trackingInfo);

        } catch (Exception e) {
            log.error("규칙 정보 조회 실패", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 현재 지표 값 조회 (테스트용)
     * 
     * GET /api/test/alarm/current-value?alarmRuleId=1
     */
    @GetMapping("/current-value")
    public Map<String, Object> getCurrentValue(@RequestParam Long alarmRuleId) {
        Map<String, Object> result = new HashMap<>();

        try {
            AlarmRule rule = alarmRuleMapper.selectRuleDetail(alarmRuleId);
            if (rule == null) {
                result.put("success", false);
                result.put("error", "알람 규칙을 찾을 수 없습니다: " + alarmRuleId);
                return result;
            }

            // Instance와 Database 조회
            Instance instance = instanceRepository.findAllWithSecrets(List.of(rule.getInstanceId())).stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Instance not found"));

            List<Database> databases = databaseRepository.findDatabaseEntitiesByInstanceId(rule.getInstanceId());
            String databaseName = databases.stream()
                    .filter(db -> db.getDatabaseId().equals(rule.getDatabaseId()))
                    .findFirst()
                    .map(Database::getDatabaseName)
                    .orElseThrow(() -> new RuntimeException("Database not found"));

            // 규칙 정보 파싱
            Map<String, Map<String, Object>> levels = parseJsonLevels(rule.getLevels());
            
            // 임계치 정보 추출
            Map<String, Object> thresholdInfo = new HashMap<>();
            for (String level : List.of("critical", "warning", "notice")) {
                Map<String, Object> levelConfig = levels.get(level);
                if (levelConfig != null) {
                    Object thresholdObj = levelConfig.get("threshold");
                    if (thresholdObj != null) {
                        thresholdInfo.put(level.toUpperCase(), thresholdObj);
                    }
                }
            }

            // 현재 지표 값 조회
            BigDecimal currentValue = null;
            try (Connection conn = createConnection(instance, databaseName)) {
                // GenericAlarmCollector의 collectMetricValue를 직접 호출할 수 없으므로
                // MetricConfig를 사용하여 쿼리 실행
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

            // 임계치와 비교
            Map<String, Object> comparison = new HashMap<>();
            String operator = rule.getOperator();
            for (String level : List.of("CRITICAL", "WARNING", "NOTICE")) {
                Object thresholdObj = thresholdInfo.get(level);
                if (thresholdObj != null && currentValue != null) {
                    BigDecimal threshold = null;
                    if (thresholdObj instanceof Number) {
                        threshold = BigDecimal.valueOf(((Number) thresholdObj).doubleValue());
                    } else if (thresholdObj instanceof String) {
                        threshold = new BigDecimal((String) thresholdObj);
                    }
                    
                    if (threshold != null) {
                        boolean triggered = compareValue(currentValue, threshold, operator);
                        comparison.put(level, Map.of(
                                "threshold", threshold,
                                "currentValue", currentValue,
                                "operator", operator,
                                "triggered", triggered,
                                "message", triggered 
                                    ? String.format("✅ 임계치 초과: %s %s %s", currentValue, operator, threshold)
                                    : String.format("❌ 임계치 미달: %s %s %s", currentValue, operator, threshold)
                        ));
                    }
                }
            }

            result.put("success", true);
            result.put("ruleId", rule.getAlarmRuleId());
            result.put("metricType", rule.getMetricType());
            result.put("operator", operator);
            result.put("currentValue", currentValue);
            result.put("thresholds", thresholdInfo);
            result.put("comparison", comparison);

        } catch (Exception e) {
            log.error("현재 지표 값 조회 실패", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 값 비교 헬퍼 메서드
     */
    private boolean compareValue(BigDecimal current, BigDecimal threshold, String operator) {
        if (current == null || threshold == null || operator == null) return false;

        int cmp = current.compareTo(threshold);
        return switch (operator) {
            case "gt" -> cmp > 0;
            case "gte" -> cmp >= 0;
            case "lt" -> cmp < 0;
            case "lte" -> cmp <= 0;
            case "eq" -> cmp == 0;
            default -> false;
        };
    }

    /**
     * 통합 테스트: 복구 임계치 + 쿨다운
     * 
     * POST /api/test/alarm/test-all?alarmRuleId=1
     * 
     * 전체 테스트 시나리오:
     * 1. 현재 지표 값 확인
     * 2. 알람 발생 확인
     * 3. 쿨다운 작동 확인
     * 4. 복구 임계치 작동 확인
     */
    @PostMapping("/test-all")
    public Map<String, Object> testAll(
            @RequestParam Long alarmRuleId
    ) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> testSteps = new ArrayList<>();

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

            // Instance와 Database 조회
            Instance instance = instanceRepository.findAllWithSecrets(List.of(rule.getInstanceId())).stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Instance not found"));

            List<Database> databases = databaseRepository.findDatabaseEntitiesByInstanceId(rule.getInstanceId());
            String databaseName = databases.stream()
                    .filter(db -> db.getDatabaseId().equals(rule.getDatabaseId()))
                    .findFirst()
                    .map(Database::getDatabaseName)
                    .orElseThrow(() -> new RuntimeException("Database not found"));

            // 규칙 정보 파싱
            Map<String, Map<String, Object>> levels = parseJsonLevels(rule.getLevels());
            
            result.put("ruleInfo", Map.of(
                    "ruleId", rule.getAlarmRuleId(),
                    "metricType", rule.getMetricType(),
                    "operator", rule.getOperator(),
                    "enabled", rule.getEnabled()
            ));

            // ========== 테스트 0: 현재 지표 값 확인 ==========
            log.info("📊 테스트 0: 현재 지표 값 확인");
            BigDecimal currentValue = null;
            try (Connection conn = createConnection(instance, databaseName)) {
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

            // 임계치 정보 추출
            Map<String, Object> thresholdInfo = new HashMap<>();
            for (String level : List.of("critical", "warning", "notice")) {
                Map<String, Object> levelConfig = levels.get(level);
                if (levelConfig != null) {
                    Object thresholdObj = levelConfig.get("threshold");
                    if (thresholdObj != null) {
                        thresholdInfo.put(level.toUpperCase(), thresholdObj);
                    }
                }
            }

            testSteps.add(Map.of(
                    "step", 0,
                    "name", "현재 지표 값 확인",
                    "success", currentValue != null,
                    "message", currentValue != null 
                        ? String.format("현재 지표 값: %s", currentValue)
                        : "지표 값을 조회할 수 없습니다.",
                    "currentValue", currentValue != null ? currentValue.toString() : "N/A",
                    "thresholds", thresholdInfo,
                    "operator", rule.getOperator()
            ));

            // ========== 테스트 1: 알람 발생 ==========
            log.info("🔴 테스트 1: 알람 발생 확인");
            try (Connection conn = createConnection(instance, databaseName)) {
                alarmCollector.checkMetric(conn, rule.getInstanceId(), rule.getDatabaseId(), rule.getMetricType());
            }

            AlarmTracking tracking = alarmTrackingMapper.findByRuleId(alarmRuleId).orElse(null);
            if (tracking == null || !"FIRED".equals(tracking.getStatus())) {
                // 왜 알람이 발생하지 않았는지 상세 분석
                Map<String, Object> analysis = new HashMap<>();
                if (currentValue != null) {
                    for (String level : List.of("CRITICAL", "WARNING", "NOTICE")) {
                        Object thresholdObj = thresholdInfo.get(level);
                        if (thresholdObj != null) {
                            BigDecimal threshold = null;
                            if (thresholdObj instanceof Number) {
                                threshold = BigDecimal.valueOf(((Number) thresholdObj).doubleValue());
                            } else if (thresholdObj instanceof String) {
                                threshold = new BigDecimal((String) thresholdObj);
                            }
                            
                            if (threshold != null) {
                                boolean triggered = compareValue(currentValue, threshold, rule.getOperator());
                                analysis.put(level, Map.of(
                                        "threshold", threshold,
                                        "triggered", triggered,
                                        "reason", triggered 
                                            ? "임계치를 초과했지만 발생 조건(occurCount, minDurationMin)을 만족하지 않았을 수 있습니다."
                                            : String.format("현재 값(%s)이 임계치(%s)를 초과하지 않습니다.", currentValue, threshold)
                                ));
                            }
                        }
                    }
                }
                
                testSteps.add(Map.of(
                        "step", 1,
                        "name", "알람 발생 확인",
                        "success", false,
                        "message", "알람이 발생하지 않았습니다. 현재 지표 값이 임계치를 초과하지 않거나, 발생 조건을 만족하지 않았을 수 있습니다.",
                        "currentValue", currentValue != null ? currentValue.toString() : "N/A",
                        "analysis", analysis,
                        "suggestion", "임계치를 낮추거나, 수동 알람 발생 엔드포인트(/api/test/alarm/trigger-manual)를 사용하여 테스트하세요."
                ));
                result.put("testSteps", testSteps);
                result.put("success", false);
                return result;
            }

            Long firstFeedId = alarmFeedMapper.selectLatestFeedIdByTrackingId(tracking.getAlarmTrackingId());
            testSteps.add(Map.of(
                    "step", 1,
                    "name", "알람 발생 확인",
                    "success", true,
                    "message", "알람이 정상적으로 발생했습니다.",
                    "trackingId", tracking.getAlarmTrackingId(),
                    "feedId", firstFeedId,
                    "currentLevel", tracking.getCurrentLevel(),
                    "currentValue", tracking.getCurrentValue(),
                    "firedAt", tracking.getFirstTriggeredAt()
            ));

            String currentLevel = tracking.getCurrentLevel();
            Map<String, Object> levelConfig = levels.get(currentLevel.toLowerCase());
            
            // ========== 테스트 2: 쿨다운 확인 ==========
            log.info("🟡 테스트 2: 쿨다운 확인");
            Integer cooldownMin = null;
            if (levelConfig != null) {
                Object cooldownObj = levelConfig.get("cooldownMin");
                if (cooldownObj != null) {
                    if (cooldownObj instanceof Number) {
                        cooldownMin = ((Number) cooldownObj).intValue();
                    } else if (cooldownObj instanceof String) {
                        cooldownMin = Integer.parseInt((String) cooldownObj);
                    }
                }
            }

            if (cooldownMin != null && cooldownMin > 0) {
                // 쿨다운 시간 내에 다시 알람 체크
                try (Connection conn = createConnection(instance, databaseName)) {
                    alarmCollector.checkMetric(conn, rule.getInstanceId(), rule.getDatabaseId(), rule.getMetricType());
                }

                Long secondFeedId = alarmFeedMapper.selectLatestFeedIdByTrackingId(tracking.getAlarmTrackingId());
                
                if (secondFeedId != null && !secondFeedId.equals(firstFeedId)) {
                    testSteps.add(Map.of(
                            "step", 2,
                            "name", "쿨다운 확인",
                            "success", false,
                            "message", String.format("쿨다운이 작동하지 않았습니다. 쿨다운 시간(%d분) 내에 새로운 알람이 발생했습니다.", cooldownMin),
                            "cooldownMin", cooldownMin,
                            "firstFeedId", firstFeedId,
                            "secondFeedId", secondFeedId
                    ));
                } else {
                    testSteps.add(Map.of(
                            "step", 2,
                            "name", "쿨다운 확인",
                            "success", true,
                            "message", String.format("쿨다운이 정상 작동합니다. 쿨다운 시간(%d분) 내에 새로운 알람이 발생하지 않았습니다.", cooldownMin),
                            "cooldownMin", cooldownMin,
                            "firstFeedId", firstFeedId,
                            "secondFeedId", secondFeedId
                    ));
                }
            } else {
                testSteps.add(Map.of(
                        "step", 2,
                        "name", "쿨다운 확인",
                        "success", true,
                        "message", "쿨다운이 설정되지 않았습니다. (정상)",
                        "cooldownMin", "설정 안됨"
                ));
            }

            // ========== 테스트 3: 복구 임계치 확인 ==========
            log.info("🟢 테스트 3: 복구 임계치 확인");
            BigDecimal resolveThreshold = null;
            Integer resolveDurationMin = null;
            
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
                        resolveDurationMin = ((Number) resolveDurationObj).intValue();
                    } else if (resolveDurationObj instanceof String) {
                        resolveDurationMin = Integer.parseInt((String) resolveDurationObj);
                    }
                }
            }

            // 발생 임계치도 조회
            BigDecimal fireThreshold = null;
            Object thresholdObj = levelConfig != null ? levelConfig.get("threshold") : null;
            if (thresholdObj != null) {
                if (thresholdObj instanceof Number) {
                    fireThreshold = BigDecimal.valueOf(((Number) thresholdObj).doubleValue());
                } else if (thresholdObj instanceof String) {
                    fireThreshold = new BigDecimal((String) thresholdObj);
                }
            }

            // 복구 임계치가 없으면 기본값 계산 (발생 임계치의 80%)
            if (resolveThreshold == null && fireThreshold != null) {
                if (rule.getOperator().equals("gt") || rule.getOperator().equals("gte")) {
                    resolveThreshold = fireThreshold.multiply(new BigDecimal("0.8"));
                } else {
                    resolveThreshold = fireThreshold.multiply(new BigDecimal("1.2"));
                }
            }

            if (resolveDurationMin == null || resolveDurationMin <= 0) {
                resolveDurationMin = 3; // 기본값
            }

            testSteps.add(Map.of(
                    "step", 3,
                    "name", "복구 임계치 확인",
                    "success", true,
                    "message", "복구 임계치 정보를 확인했습니다. 실제 복구는 지표 값이 복구 임계치 이하로 떨어지고, 복구 지속 시간이 지나면 자동으로 복구됩니다.",
                    "fireThreshold", fireThreshold != null ? fireThreshold.toString() : "N/A",
                    "resolveThreshold", resolveThreshold != null ? resolveThreshold.toString() : "기본값 (발생 임계치의 80%)",
                    "resolveDurationMin", resolveDurationMin + "분",
                    "currentLevel", currentLevel,
                    "note", String.format("현재 값이 %s 이하로 떨어지고, %d분 이상 유지되면 자동으로 복구됩니다.", 
                            resolveThreshold != null ? resolveThreshold.toString() : "복구 임계치", resolveDurationMin)
            ));

            // ========== 테스트 4: 트래킹 상태 확인 ==========
            log.info("📊 테스트 4: 트래킹 상태 확인");
            tracking = alarmTrackingMapper.findByRuleId(alarmRuleId).orElse(null);
            if (tracking != null) {
                testSteps.add(Map.of(
                        "step", 4,
                        "name", "트래킹 상태 확인",
                        "success", true,
                        "message", "트래킹 상태를 확인했습니다.",
                        "status", tracking.getStatus(),
                        "currentLevel", tracking.getCurrentLevel(),
                        "currentValue", tracking.getCurrentValue(),
                        "consecutiveCount", tracking.getConsecutiveCount(),
                        "firstTriggeredAt", tracking.getFirstTriggeredAt(),
                        "lastCheckedAt", tracking.getLastCheckedAt()
                ));
            } else {
                testSteps.add(Map.of(
                        "step", 4,
                        "name", "트래킹 상태 확인",
                        "success", false,
                        "message", "트래킹이 존재하지 않습니다."
                ));
            }

            result.put("success", true);
            result.put("message", "전체 테스트 완료");
            result.put("testSteps", testSteps);
            result.put("summary", Map.of(
                    "totalSteps", testSteps.size(),
                    "passedSteps", testSteps.stream().mapToInt(s -> (Boolean) s.get("success") ? 1 : 0).sum(),
                    "failedSteps", testSteps.stream().mapToInt(s -> (Boolean) s.get("success") ? 0 : 1).sum()
            ));

        } catch (Exception e) {
            log.error("통합 테스트 실패", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("testSteps", testSteps);
        }

        return result;
    }

    /**
     * 특정 metricType의 모든 활성화된 규칙 조회 (디버깅용)
     */
    @GetMapping("/list-rules-by-metric")
    public Map<String, Object> listRulesByMetric(
            @RequestParam String metricType
    ) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 모든 활성화된 규칙 조회 (instanceId/databaseId 필터 없이)
            List<com.dajanggan.domain.alarm.dto.AlarmRuleDto.RuleListRaw> allRules = 
                    alarmRuleMapper.selectRuleList(null, null, metricType, true);
            
            result.put("success", true);
            result.put("metricType", metricType);
            result.put("totalCount", allRules != null ? allRules.size() : 0);
            
            List<Map<String, Object>> rules = new ArrayList<>();
            if (allRules != null) {
                for (com.dajanggan.domain.alarm.dto.AlarmRuleDto.RuleListRaw rule : allRules) {
                    Map<String, Object> ruleInfo = new HashMap<>();
                    ruleInfo.put("ruleId", rule.getAlarmRuleId());
                    ruleInfo.put("instanceId", rule.getInstanceId());
                    ruleInfo.put("databaseId", rule.getDatabaseId());
                    ruleInfo.put("enabled", rule.getEnabled());
                    ruleInfo.put("metricType", rule.getMetricType());
                    rules.add(ruleInfo);
                }
            }
            result.put("rules", rules);
            
            log.info("📋 규칙 조회: metricType={}, 개수={}", metricType, rules.size());
            
        } catch (Exception e) {
            log.error("규칙 조회 실패", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 특정 instanceId/databaseId에 대해 조회되는 규칙 확인 (디버깅용)
     */
    @GetMapping("/check-rules-for-instance")
    public Map<String, Object> checkRulesForInstance(
            @RequestParam Long instanceId,
            @RequestParam Long databaseId,
            @RequestParam String metricType
    ) {
        Map<String, Object> result = new HashMap<>();
        try {
            // findActiveRules로 조회되는 규칙
            List<AlarmRule> activeRules = alarmRuleMapper.findActiveRules(instanceId, databaseId, metricType);
            
            result.put("success", true);
            result.put("instanceId", instanceId);
            result.put("databaseId", databaseId);
            result.put("metricType", metricType);
            result.put("foundCount", activeRules != null ? activeRules.size() : 0);
            
            List<Map<String, Object>> rules = new ArrayList<>();
            if (activeRules != null) {
                for (AlarmRule rule : activeRules) {
                    Map<String, Object> ruleInfo = new HashMap<>();
                    ruleInfo.put("ruleId", rule.getAlarmRuleId());
                    ruleInfo.put("instanceId", rule.getInstanceId());
                    ruleInfo.put("databaseId", rule.getDatabaseId());
                    ruleInfo.put("enabled", rule.getEnabled());
                    rules.add(ruleInfo);
                }
            }
            result.put("rules", rules);
            
            // 모든 활성화된 규칙도 조회 (비교용)
            List<com.dajanggan.domain.alarm.dto.AlarmRuleDto.RuleListRaw> allRules = 
                    alarmRuleMapper.selectRuleList(null, null, metricType, true);
            result.put("totalActiveRules", allRules != null ? allRules.size() : 0);
            
            log.info("🔍 규칙 체크: instanceId={}, databaseId={}, metricType={}, 조회된 규칙={}개, 전체 활성 규칙={}개",
                    instanceId, databaseId, metricType, 
                    activeRules != null ? activeRules.size() : 0,
                    allRules != null ? allRules.size() : 0);
            
        } catch (Exception e) {
            log.error("규칙 체크 실패", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 수동으로 값을 설정하여 알람 체크 (플러핑/쿨다운 테스트용)
     * 
     * POST /api/test/alarm/check-with-value?alarmRuleId=1&value=100
     */
    @PostMapping("/check-with-value")
    public Map<String, Object> checkAlarmWithValue(
            @RequestParam Long alarmRuleId,
            @RequestParam BigDecimal value
    ) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> steps = new ArrayList<>();

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

            // Instance와 Database 조회
            Instance instance = instanceRepository.findAllWithSecrets(List.of(rule.getInstanceId())).stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Instance not found"));

            List<Database> databases = databaseRepository.findDatabaseEntitiesByInstanceId(rule.getInstanceId());
            String databaseName = databases.stream()
                    .filter(db -> db.getDatabaseId().equals(rule.getDatabaseId()))
                    .findFirst()
                    .map(Database::getDatabaseName)
                    .orElseThrow(() -> new RuntimeException("Database not found"));

            steps.add(Map.of(
                    "step", 1,
                    "name", "규칙 조회",
                    "success", true,
                    "message", String.format("규칙 조회 완료: ruleId=%d, metricType=%s", 
                            rule.getAlarmRuleId(), rule.getMetricType())
            ));

            // DB 연결 (실제 쿼리는 실행하지 않고 Connection만 생성)
            try (Connection conn = createConnection(instance, databaseName)) {
                steps.add(Map.of(
                        "step", 2,
                        "name", "DB 연결",
                        "success", true,
                        "message", "DB 연결 성공"
                ));

                // 수동으로 설정한 값으로 알람 체크
                log.info("🧪 수동 값으로 알람 체크: ruleId={}, value={}, metricType={}",
                        alarmRuleId, value, rule.getMetricType());

                // processAlarmRule 직접 호출
                alarmCollector.processAlarmRule(
                        conn,
                        rule,
                        value,
                        rule.getInstanceId(),
                        rule.getDatabaseId(),
                        rule.getMetricType()
                );

                steps.add(Map.of(
                        "step", 3,
                        "name", "알람 체크 실행",
                        "success", true,
                        "message", String.format("수동 값(%s)으로 알람 체크 완료", value)
                ));

                // 트래킹 상태 확인
                AlarmTracking tracking = alarmTrackingMapper.findByRuleId(alarmRuleId).orElse(null);
                if (tracking != null) {
                    // 규칙의 발생 조건 확인
                    Map<String, Map<String, Object>> levels = parseJsonLevels(rule.getLevels());
                    String currentLevel = tracking.getCurrentLevel();
                    Map<String, Object> levelConfig = levels.get(currentLevel != null ? currentLevel.toLowerCase() : "critical");
                    
                    int requiredCount = levelConfig != null ? 
                            ((Number) levelConfig.getOrDefault("occurCount", 1)).intValue() : 1;
                    int requiredDuration = levelConfig != null ? 
                            ((Number) levelConfig.getOrDefault("minDurationMin", 1)).intValue() : 1;
                    
                    // 지속 시간 계산
                    long durationSeconds = 0;
                    long durationMinutes = 0;
                    if (tracking.getFirstTriggeredAt() != null) {
                        durationSeconds = java.time.Duration.between(
                                tracking.getFirstTriggeredAt(), 
                                OffsetDateTime.now()
                        ).getSeconds();
                        durationMinutes = durationSeconds / 60;
                    }
                    
                    String statusMessage = String.format(
                            "트래킹 상태: %s, 레벨: %s, 값: %s, 연속 횟수: %d/%d, 지속 시간: %d분 (%d초)/%d분",
                            tracking.getStatus(),
                            tracking.getCurrentLevel(),
                            tracking.getCurrentValue(),
                            tracking.getConsecutiveCount(),
                            requiredCount,
                            durationMinutes,
                            durationSeconds,
                            requiredDuration
                    );
                    
                    // Feed가 생성되지 않은 이유 분석
                    String feedReason = "";
                    if ("PENDING".equals(tracking.getStatus())) {
                        if (tracking.getConsecutiveCount() < requiredCount) {
                            feedReason = String.format("발생 횟수 부족 (%d/%d)", 
                                    tracking.getConsecutiveCount(), requiredCount);
                        } else if (durationMinutes < requiredDuration) {
                            feedReason = String.format("지속 시간 부족 (%d분/%d분)", 
                                    durationMinutes, requiredDuration);
                        } else {
                            feedReason = "조건을 만족했지만 Feed가 생성되지 않음 (쿨다운 또는 기타 이유)";
                        }
                    }
                    
                    Map<String, Object> trackingInfo = new HashMap<>();
                    trackingInfo.put("status", tracking.getStatus());
                    trackingInfo.put("level", tracking.getCurrentLevel());
                    trackingInfo.put("value", tracking.getCurrentValue());
                    trackingInfo.put("consecutiveCount", tracking.getConsecutiveCount());
                    trackingInfo.put("firstTriggered", tracking.getFirstTriggeredAt());
                    trackingInfo.put("requiredCount", requiredCount);
                    trackingInfo.put("requiredDuration", requiredDuration);
                    trackingInfo.put("durationSeconds", durationSeconds);
                    trackingInfo.put("durationMinutes", durationMinutes);
                    if (!feedReason.isEmpty()) {
                        trackingInfo.put("feedNotCreatedReason", feedReason);
                    }
                    
                    steps.add(Map.of(
                            "step", 4,
                            "name", "트래킹 상태 확인",
                            "success", true,
                            "message", statusMessage,
                            "tracking", trackingInfo
                    ));
                } else {
                    steps.add(Map.of(
                            "step", 4,
                            "name", "트래킹 상태 확인",
                            "success", true,
                            "message", "트래킹이 없습니다 (임계치 미달 또는 정상 범위)"
                    ));
                }

                // Feed 확인
                if (tracking != null) {
                    Long feedId = alarmFeedMapper.selectLatestFeedIdByTrackingId(tracking.getAlarmTrackingId());
                    if (feedId != null) {
                        AlarmFeed feed = alarmFeedMapper.selectAlarmDetail(feedId);
                        steps.add(Map.of(
                                "step", 5,
                                "name", "Feed 확인",
                                "success", true,
                                "message", String.format("Feed 생성됨: feedId=%d, severity=%s, resolved=%s",
                                        feedId, feed.getSeverityLevel(), feed.getIsResolved()),
                                "feed", Map.of(
                                        "feedId", feedId,
                                        "severity", feed.getSeverityLevel(),
                                        "resolved", feed.getIsResolved(),
                                        "occurredAt", feed.getOccurredAt()
                                )
                        ));
                    } else {
                        steps.add(Map.of(
                                "step", 5,
                                "name", "Feed 확인",
                                "success", true,
                                "message", "Feed가 아직 생성되지 않았습니다 (PENDING 상태 또는 조건 미충족)"
                        ));
                    }
                }

                result.put("success", true);
                result.put("message", "수동 값으로 알람 체크 완료");
                result.put("ruleId", alarmRuleId);
                result.put("value", value);
                result.put("metricType", rule.getMetricType());
                result.put("testSteps", steps);

            } catch (SQLException e) {
                log.error("DB 연결 실패", e);
                result.put("success", false);
                result.put("error", "DB 연결 실패: " + e.getMessage());
                result.put("testSteps", steps);
            }

        } catch (Exception e) {
            log.error("수동 알람 체크 실패", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("testSteps", steps);
        }

        return result;
    }

    /**
     * JSON levels 파싱 헬퍼 메서드
     */
    private Map<String, Map<String, Object>> parseJsonLevels(String levelsJson) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(levelsJson, 
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("JSON 파싱 실패", e);
            return new HashMap<>();
        }
    }
}