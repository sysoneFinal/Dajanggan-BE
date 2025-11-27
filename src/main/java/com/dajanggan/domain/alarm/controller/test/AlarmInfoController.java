package com.dajanggan.domain.alarm.controller.test;

import com.dajanggan.domain.alarm.service.test.AlarmTestService;
import com.dajanggan.domain.alarm.repository.AlarmRuleMapper;
import com.dajanggan.domain.alarm.repository.AlarmTrackingMapper;
import com.dajanggan.domain.alarm.repository.AlarmFeedMapper;
import com.dajanggan.domain.alarm.domain.AlarmRule;
import com.dajanggan.domain.alarm.domain.AlarmTracking;
import com.dajanggan.domain.alarm.dto.AlarmTrackingDto;
import com.dajanggan.domain.alarm.config.MetricConfig;
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
 * 알람 정보 조회 전용 컨트롤러
 * - 트래킹 조회
 * - 알람 피드 조회
 * - 규칙 정보 조회
 * - 현재 지표 값 조회
 */
@Tag(name = "Alarm-Info", description = "알람 정보 조회 API")
@RestController
@RequestMapping("/api/test/alarm/info")
@RequiredArgsConstructor
@Slf4j
public class AlarmInfoController {

    private final AlarmRuleMapper alarmRuleMapper;
    private final AlarmTrackingMapper alarmTrackingMapper;
    private final AlarmFeedMapper alarmFeedMapper;
    private final InstanceRepository instanceRepository;
    private final DatabaseRepository databaseRepository;
    private final MetricConfig metricConfig;
    private final AlarmTestService alarmTestService;

    /**
     * 현재 추적 중인 알람 조회
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
     * 모든 알람 피드 조회 (해결된 것 포함)
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
     * 특정 규칙의 트래킹 초기화
     */
    @DeleteMapping("/tracking/{ruleId}")
    public Map<String, Object> resetTracking(@PathVariable Long ruleId) {
        Map<String, Object> result = new HashMap<>();

        try {
            alarmTrackingMapper.findByRuleId(ruleId)
                    .ifPresent(tracking -> {
                        alarmTrackingMapper.delete(tracking.getAlarmTrackingId());
                        log.info("트래킹 초기화: ruleId={}", ruleId);
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
     * 알람 규칙의 복구 임계치와 쿨다운 정보 조회
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

            Map<String, Map<String, Object>> levels = alarmTestService.parseJsonLevels(rule.getLevels());
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
            Map<String, Map<String, Object>> levels = alarmTestService.parseJsonLevels(rule.getLevels());

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
                        boolean triggered = alarmTestService.compareValue(currentValue, threshold, operator);
                        comparison.put(level, Map.of(
                                "threshold", threshold,
                                "currentValue", currentValue,
                                "operator", operator,
                                "triggered", triggered,
                                "message", triggered
                                        ? String.format("임계치 초과: %s %s %s", currentValue, operator, threshold)
                                        : String.format("임계치 미달: %s %s %s", currentValue, operator, threshold)
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
     * 특정 metricType의 모든 활성화된 규칙 조회
     */
    @GetMapping("/list-rules-by-metric")
    public Map<String, Object> listRulesByMetric(@RequestParam String metricType) {
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
                for (var rule : allRules) {
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

            log.info("규칙 조회: metricType={}, 개수={}", metricType, rules.size());

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

            log.info("규칙 체크: instanceId={}, databaseId={}, metricType={}, 조회된 규칙={}개, 전체 활성 규칙={}개",
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
}