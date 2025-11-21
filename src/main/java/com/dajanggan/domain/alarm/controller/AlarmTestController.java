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
}