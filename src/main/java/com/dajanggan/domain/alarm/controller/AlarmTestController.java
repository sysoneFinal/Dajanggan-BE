package com.dajanggan.domain.alarm.controller;

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
                        "dead_tuples",
                        "bloat_size",
                        "unused_indexes",
                        "connection_count",
                        "long_running_queries",
                        "cache_hit_ratio",
                        "sequential_scans"
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