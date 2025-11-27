package com.dajanggan.domain.alarm.controller.test;

import com.dajanggan.domain.alarm.service.AlarmMetricsCollector;
import com.dajanggan.domain.alarm.service.GenericAlarmCollector;
import com.dajanggan.domain.alarm.service.test.AlarmTestService;
import com.dajanggan.domain.alarm.repository.AlarmRuleMapper;
import com.dajanggan.domain.alarm.repository.AlarmTrackingMapper;
import com.dajanggan.domain.alarm.repository.AlarmFeedMapper;
import com.dajanggan.domain.alarm.domain.AlarmRule;
import com.dajanggan.domain.alarm.domain.AlarmTracking;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.domain.instance.repository.DatabaseRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 알람 시스템 핵심 테스트 기능
 * - 테스트 모드 제어
 * - 알람 체크 실행
 * - DB 연결 테스트
 */
@Tag(name = "Alarm-Test", description = "알람 핵심 테스트 API")
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
    private final AlarmMetricsCollector alarmMetricsCollector;
    private final AlarmTestService alarmTestService;

    /**
     * 테스트 모드 활성화/비활성화
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

            log.info("Instance 정보:");
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

            log.info("알람 체크 시작: instance={}, database={}, metric={}",
                    instance.getInstanceName(), databaseName, metricType);

            // DB 연결 및 알람 체크
            try (Connection conn = alarmTestService.createConnection(instance, databaseName)) {
                alarmCollector.checkMetric(conn, instanceId, databaseId, metricType);

                result.put("success", true);
                result.put("message", "알람 체크 완료");
                result.put("instanceName", instance.getInstanceName());
                result.put("databaseName", databaseName);
                result.put("metricType", metricType);

            } catch (Exception e) {
                log.error("DB 연결 실패 상세:", e);
                result.put("success", false);
                result.put("error", "DB 연결 실패: " + e.getMessage());
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

            log.info("전체 알람 체크 시작: instance={}, database={}",
                    instance.getInstanceName(), databaseName);

            try (Connection conn = alarmTestService.createConnection(instance, databaseName)) {
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
                        log.info("{} 체크 완료", metric);
                    } catch (Exception e) {
                        log.error("{} 체크 실패: {}", metric, e.getMessage());
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
     * DB 연결 테스트 전용 엔드포인트
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
            try (Connection conn = alarmTestService.createConnection(instance, databaseName)) {
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

        } catch (Exception e) {
            result.put("connectionSuccess", false);
            result.put("error", e.getMessage());
            result.put("type", e.getClass().getName());
        }

        return result;
    }

    /**
     * 수동으로 값을 설정하여 알람 체크 (플러핑/쿨다운 테스트용)
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
            try (Connection conn = alarmTestService.createConnection(instance, databaseName)) {
                steps.add(Map.of(
                        "step", 2,
                        "name", "DB 연결",
                        "success", true,
                        "message", "DB 연결 성공"
                ));

                // 수동으로 설정한 값으로 알람 체크
                log.info("수동 값으로 알람 체크: ruleId={}, value={}, metricType={}",
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
                    Map<String, Map<String, Object>> levels = alarmTestService.parseJsonLevels(rule.getLevels());
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
                                java.time.OffsetDateTime.now()
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
                        var feed = alarmFeedMapper.selectAlarmDetail(feedId);
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

            }

        } catch (Exception e) {
            log.error("수동 알람 체크 실패", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("testSteps", steps);
        }

        return result;
    }
}