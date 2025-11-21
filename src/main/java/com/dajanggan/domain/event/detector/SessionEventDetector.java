package com.dajanggan.domain.event.detector;

import com.dajanggan.domain.event.dto.*;
import com.dajanggan.domain.session.dto.raw.SessionRawMetricDto;
import com.dajanggan.domain.event.detector.AlarmRuleCache;
import com.dajanggan.domain.alarm.dto.AlarmRuleDto;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SessionEventDetector {

    // 기본 임계치
    private static final double DEFAULT_LOCK_WAIT_THRESHOLD_SEC = 5.0;
    private static final double DEFAULT_LONG_TX_THRESHOLD_SEC = 600.0;
    private static final double DEFAULT_CONNECTION_WARNING_RATIO = 0.80;
    private static final double DEFAULT_CONNECTION_CRITICAL_RATIO = 0.90;
    private static final double DEFAULT_WAITING_SESSION_RATIO = 0.30;
    private static final int DEFAULT_IDLE_IN_TX_THRESHOLD = 10;

    // 중복 제거 설정
    private static final int DEDUP_WINDOW_MINUTES = 5;
    private final ConcurrentHashMap<String, OffsetDateTime> eventCache = new ConcurrentHashMap<>();

    private final AlarmRuleCache alarmRuleCache;

    public SessionEventDetector(AlarmRuleCache alarmRuleCache) {
        this.alarmRuleCache = alarmRuleCache;
    }

    /**
     * 세션 데이터에서 이벤트 감지
     */
    public List<EventLog> detectEvents(List<SessionRawMetricDto> sessions, Long databaseId,
                                       Long instanceId, String databaseName, String instanceName) {
        List<EventLog> events = new ArrayList<>();

        if (sessions == null || sessions.isEmpty()) {
            return events;
        }

        cleanupExpiredCache();

        // 캐시된 알람 룰에서 임계치 맵 생성
        Map<String, ThresholdConfig> thresholds = buildThresholds(instanceId, databaseId);

        // 개별 세션 이벤트 감지
        for (SessionRawMetricDto s : sessions) {

            // 데드락 이벤트
            if (Boolean.TRUE.equals(s.getIsDeadlock())) {
                String eventKey = buildEventKey(databaseId, instanceId,
                        EventType.DEADLOCK.name(), s.getPid(), s.getBlockingPid());

                if (shouldLogEvent(eventKey)) {
                    events.add(buildEvent(s, databaseId, instanceId, databaseName, instanceName,
                            EventType.DEADLOCK.name(), ResourceType.TABLE.name(),
                            s.getLockDurationMs(), "CRITICAL",  // 데드락은 항상 CRITICAL
                            String.format("데드락 발생: 사용자 '%s' PID %d가 PID %d에 의해 차단됨",
                                    s.getUsername(), s.getPid(), s.getBlockingPid())));
                }
            }

            // 락 대기 이벤트
            if ("Lock".equals(s.getWaitEventType()) && s.getWaitDurationSec() != null) {
                ThresholdConfig lockConfig = thresholds.get("LOCK_WAIT");
                String lockLevel = detectLevel(s.getWaitDurationSec(), lockConfig);
                if (lockLevel != null) {
                    String eventKey = buildEventKey(databaseId, instanceId,
                            EventType.LOCK_WAIT.name() + "_" + lockLevel, s.getPid(), s.getBlockingPid());

                    if (shouldLogEvent(eventKey)) {
                        events.add(buildEvent(s, databaseId, instanceId, databaseName, instanceName,
                                EventType.LOCK_WAIT.name(), ResourceType.LOCK.name(),
                                s.getWaitDurationSec(), lockLevel,
                                String.format("락 대기 %.1f초: 사용자 '%s' PID %d가 PID %d를 대기 중",
                                        s.getWaitDurationSec(), s.getUsername(), s.getPid(),
                                        Optional.ofNullable(s.getBlockingPid()).orElse(0))));
                    }
                }
            }


            // 장시간 트랜잭션 이벤트
            if (s.getQueryAgeSec() != null) {
                String state = s.getState();

                // idle 상태는 정상이므로 제외
                boolean shouldCheck = "active".equalsIgnoreCase(state) ||
                        "idle in transaction".equalsIgnoreCase(state) ||
                        "idle in transaction (aborted)".equalsIgnoreCase(state);

                if (shouldCheck) {
                    ThresholdConfig txConfig = thresholds.get("LONG_TRANSACTION");
                    String txLevel = detectLevel(s.getQueryAgeSec(), txConfig);

                    if (txLevel != null) {
                        String eventKey = buildEventKey(databaseId, instanceId,
                                EventType.LONG_TRANSACTION.name() + "_" + txLevel, s.getPid(), null);

                        if (shouldLogEvent(eventKey)) {
                            events.add(buildEvent(s, databaseId, instanceId, databaseName, instanceName,
                                    EventType.LONG_TRANSACTION.name(), ResourceType.SESSION.name(),
                                    s.getQueryAgeSec(), txLevel,
                                    String.format("장시간 트랜잭션 %.0f분: 사용자 '%s' PID %d 상태 '%s'",
                                            s.getQueryAgeSec() / 60, s.getUsername(), s.getPid(), state)));
                        }
                    }
                }
            }
        }

        // 커넥션 부족 감지
        Integer maxConnections = sessions.get(0).getMaxConnections();
        if (maxConnections != null && maxConnections > 0) {
            int usedConnections = sessions.size();
            double ratio = (double) usedConnections / maxConnections;

            ThresholdConfig connConfig = thresholds.get("CONNECTION_HIGH_USAGE");
            String connLevel = detectLevel(ratio, connConfig);

            if (connLevel != null) {
                String eventKey = buildEventKey(databaseId, instanceId,
                        EventType.CONNECTION_HIGH_USAGE.name() + "_" + connLevel, null, null);

                if (shouldLogEvent(eventKey)) {
                    events.add(buildEvent(
                            databaseId, instanceId, databaseName, instanceName,
                            EventType.CONNECTION_HIGH_USAGE.name(),
                            ResourceType.CONNECTION_POOL.name(),
                            connLevel,
                            String.format("커넥션 사용량 높음: %d/%d (%d%%)",
                                    usedConnections, maxConnections, (int)(ratio*100))
                    ));
                }
            }
        }

        // 과도한 대기 세션 감지
        long totalSessions = sessions.size();
        long waitingSessions = sessions.stream().filter(s -> s.getWaitEventType() != null).count();
        double waitingRatio = totalSessions > 0 ? (double) waitingSessions / totalSessions : 0;

        ThresholdConfig waitingConfig = thresholds.get("TOO_MANY_WAITING");
        String waitingLevel = detectLevel(waitingRatio, waitingConfig);

        if (waitingLevel != null) {
            String eventKey = buildEventKey(databaseId, instanceId,
                    EventType.TOO_MANY_WAITING.name() + "_" + waitingLevel, null, null);

            if (shouldLogEvent(eventKey)) {
                events.add(buildEvent(
                        databaseId, instanceId, databaseName, instanceName,
                        EventType.TOO_MANY_WAITING.name(),
                        ResourceType.SESSION.name(),
                        waitingLevel,
                        String.format("과도한 대기 세션: %d/%d (%d%%)",
                                waitingSessions, totalSessions, (int)(waitingRatio*100))
                ));
            }
        }

        // Idle in Transaction 급증 감지
        long idleInTxCount = sessions.stream()
                .filter(s -> "idle in transaction".equals(s.getState())
                        || "idle in transaction (aborted)".equals(s.getState()))
                .count();

        ThresholdConfig idleConfig = thresholds.get("IDLE_IN_TRANSACTION_SURGE");
        String idleLevel = detectLevel((double)idleInTxCount, idleConfig);

        if (idleLevel != null) {
            String eventKey = buildEventKey(databaseId, instanceId,
                    EventType.IDLE_IN_TRANSACTION_SURGE.name() + "_" + idleLevel, null, null);

            if (shouldLogEvent(eventKey)) {
                events.add(buildEvent(
                        databaseId, instanceId, databaseName, instanceName,
                        EventType.IDLE_IN_TRANSACTION_SURGE.name(),
                        ResourceType.SESSION.name(),
                        idleLevel,
                        String.format("유휴 트랜잭션 급증: %d개 세션 감지됨", idleInTxCount)
                ));
            }
        }

        return events;
    }
    /**
     * 알람 룰에서 임계치 맵 생성
     */
    private Map<String, ThresholdConfig> buildThresholds(Long instanceId, Long databaseId) {
        Map<String, ThresholdConfig> thresholdMap = new HashMap<>();

        // ⭐ 캐시에서 알람 룰 조회 (RuleWithLevels 사용)
        List<AlarmRuleCache.RuleWithLevels> rules = alarmRuleCache.getRulesByDatabase(instanceId, databaseId);

        // 알람 룰을 임계치 맵으로 변환
        for (AlarmRuleCache.RuleWithLevels rule : rules) {
            ThresholdConfig config = convertToThresholdConfig(rule.getLevels());
            thresholdMap.put(rule.getMetricType(), config);
        }

        // 기본값 설정 (룰이 없는 경우)
        setDefaultThresholds(thresholdMap);

        return thresholdMap;
    }

    /**
     * Levels DTO를 ThresholdConfig로 변환
     */
    private ThresholdConfig convertToThresholdConfig(AlarmRuleDto.Levels levels) {
        ThresholdConfig config = new ThresholdConfig();
        Map<String, Double> levelMap = new HashMap<>();

        if (levels != null) {
            if (levels.getInfo() != null && levels.getInfo().getThreshold() != null) {
                levelMap.put("INFO", levels.getInfo().getThreshold().doubleValue());
            }
            if (levels.getWarn() != null && levels.getWarn().getThreshold() != null) {
                levelMap.put("WARN", levels.getWarn().getThreshold().doubleValue());
            }
            if (levels.getCritical() != null && levels.getCritical().getThreshold() != null) {
                levelMap.put("CRITICAL", levels.getCritical().getThreshold().doubleValue());
            }
        }

        config.setLevels(levelMap);
        return config;
    }

    /**
     * 기본 임계치 설정
     */
    private void setDefaultThresholds(Map<String, ThresholdConfig> thresholdMap) {
        thresholdMap.putIfAbsent("LOCK_WAIT",
                createDefaultConfig(Map.of("WARN", DEFAULT_LOCK_WAIT_THRESHOLD_SEC)));
        thresholdMap.putIfAbsent("LONG_TRANSACTION",
                createDefaultConfig(Map.of("WARN", DEFAULT_LONG_TX_THRESHOLD_SEC)));
        thresholdMap.putIfAbsent("CONNECTION_HIGH_USAGE",
                createDefaultConfig(Map.of(
                        "WARN", DEFAULT_CONNECTION_WARNING_RATIO,
                        "CRITICAL", DEFAULT_CONNECTION_CRITICAL_RATIO
                )));
        thresholdMap.putIfAbsent("TOO_MANY_WAITING",
                createDefaultConfig(Map.of("WARN", DEFAULT_WAITING_SESSION_RATIO)));
        thresholdMap.putIfAbsent("IDLE_IN_TRANSACTION_SURGE",
                createDefaultConfig(Map.of("INFO", (double)DEFAULT_IDLE_IN_TX_THRESHOLD)));
    }

    /**
     * 측정값이 어느 레벨에 해당하는지 판단
     * CRITICAL > WARN > INFO 순서로 체크
     */
    private String detectLevel(Double value, ThresholdConfig config) {
        if (value == null || config == null) {
            return null;
        }

        Map<String, Double> levels = config.getLevels();

        // CRITICAL 체크
        if (levels.containsKey("CRITICAL") && value >= levels.get("CRITICAL")) {
            return "CRITICAL";
        }

        // WARN 체크
        if (levels.containsKey("WARN") && value >= levels.get("WARN")) {
            return "WARN";
        }

        // INFO 체크
        if (levels.containsKey("INFO") && value >= levels.get("INFO")) {
            return "INFO";
        }

        return null;
    }

    private ThresholdConfig createDefaultConfig(Map<String, Double> levels) {
        ThresholdConfig config = new ThresholdConfig();
        config.setLevels(levels);
        return config;
    }

    private String buildEventKey(Long databaseId, Long instanceId, String eventType,
                                 Integer pid, Integer blockingPid) {
        StringBuilder key = new StringBuilder();
        key.append(instanceId).append("_")
                .append(databaseId).append("_")
                .append(eventType);

        if (pid != null) {
            key.append("_").append(pid);
            if (blockingPid != null) {
                key.append("_").append(blockingPid);
            }
        }

        return key.toString();
    }

    private boolean shouldLogEvent(String eventKey) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime lastLogged = eventCache.get(eventKey);

        if (lastLogged == null || lastLogged.plusMinutes(DEDUP_WINDOW_MINUTES).isBefore(now)) {
            eventCache.put(eventKey, now);
            return true;
        }

        return false;
    }

    private void cleanupExpiredCache() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(1);
        eventCache.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    private EventLog buildEvent(SessionRawMetricDto dto, Long databaseId, Long instanceId,
                                String databaseName, String instanceName, String eventType,
                                String resourceType, Double duration, String level, String description) {
        return EventLog.builder()
                .instanceId(instanceId)
                .databaseId(databaseId)
                .instanceName(instanceName)
                .databaseName(databaseName)
                .category(EventCategory.SESSION.name())
                .eventType(eventType)
                .level(level)
                .userName(dto.getUsername())
                .resourceType(resourceType)
                .detectedAt(OffsetDateTime.now())
                .duration(duration)
                .description(description)
                .build();
    }

    private EventLog buildEvent(Long databaseId, Long instanceId,
                                String databaseName, String instanceName, String eventType,
                                String resourceType, String level, String description) {
        return EventLog.builder()
                .instanceId(instanceId)
                .databaseId(databaseId)
                .instanceName(instanceName)
                .databaseName(databaseName)
                .category(EventCategory.SESSION.name())
                .eventType(eventType)
                .level(level)
                .userName(null)
                .resourceType(resourceType)
                .detectedAt(OffsetDateTime.now())
                .duration(null)
                .description(description)
                .build();
    }

    @Data
    private static class ThresholdConfig {
        private Map<String, Double> levels = new HashMap<>();
    }
}