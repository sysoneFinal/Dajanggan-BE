package com.dajanggan.domain.event.detector;

import com.dajanggan.domain.event.dto.*;
import com.dajanggan.domain.session.dto.raw.SessionRawMetricDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class SessionEventDetector {

    private static final double LOCK_WAIT_THRESHOLD_SEC = 5.0;
    private static final double LONG_TX_THRESHOLD_SEC = 600.0;
    private static final double CONNECTION_WARNING_RATIO = 0.80;
    private static final double CONNECTION_CRITICAL_RATIO = 0.90;
    private static final double WAITING_SESSION_RATIO = 0.30;
    private static final int IDLE_IN_TX_THRESHOLD = 10;

    /**
     * 세션 데이터에서 이벤트 감지
     */
    public List<EventLog>detectEvents(List<SessionRawMetricDto> sessions, Long databaseId,
                                      Long instanceId, String databaseName, String instanceName) {
        List<EventLog> events = new ArrayList<>();

        if (sessions == null || sessions.isEmpty()) {
            return events;
        }

        // 개별 세션 이벤트 감지
        for (SessionRawMetricDto s : sessions) {
            String levelStr = s.getImpactLevel(); // 이미 계산된 값 사용

            if (Boolean.TRUE.equals(s.getIsDeadlock())) {
                events.add(buildEvent(s, databaseId, instanceId, databaseName, instanceName,
                        EventType.DEADLOCK.name(), ResourceType.TABLE.name(), s.getLockDurationMs(), levelStr,
                        String.format("데드락 발생: 사용자 '%s' PID %d가 PID %d에 의해 차단됨",
                                s.getUsername(), s.getPid(), s.getBlockingPid())));
            }
            if ("Lock".equals(s.getWaitEventType()) && s.getWaitDurationSec() != null
                    && s.getWaitDurationSec() >= LOCK_WAIT_THRESHOLD_SEC) {
                events.add(buildEvent(s, databaseId, instanceId, databaseName, instanceName,
                        EventType.LOCK_WAIT.name(), ResourceType.LOCK.name(), s.getWaitDurationSec(), levelStr,
                        String.format("락 대기 %.1f초: 사용자 '%s' PID %d가 PID %d를 대기 중",
                                s.getWaitDurationSec(), s.getUsername(), s.getPid(),
                                Optional.ofNullable(s.getBlockingPid()).orElse(0))));
            }
            if (s.getQueryAgeSec() != null && s.getQueryAgeSec() >= LONG_TX_THRESHOLD_SEC) {
                events.add(buildEvent(s, databaseId, instanceId, databaseName, instanceName,
                        EventType.LONG_TRANSACTION.name(), ResourceType.SESSION.name(), s.getQueryAgeSec(), levelStr,
                        String.format("장시간 트랜잭션 %.0f분: 사용자 '%s' PID %d 상태 '%s'",
                                s.getQueryAgeSec() / 60, s.getUsername(), s.getPid(), s.getState())));
            }
        }

        // 커넥션 부족 감지
        Integer maxConnections = sessions.get(0).getMaxConnections();
        if (maxConnections != null && maxConnections > 0) {
            int usedConnections = sessions.size();
            double ratio = (double) usedConnections / maxConnections;
            if (ratio >= CONNECTION_WARNING_RATIO) {
                String levelStr = ratio >= CONNECTION_CRITICAL_RATIO ? "CRITICAL" : "WARNING";
                events.add(buildEvent(
                        databaseId, instanceId, databaseName, instanceName,
                        EventType.CONNECTION_HIGH_USAGE.name(),
                        ResourceType.CONNECTION_POOL.name(),
                        levelStr,
                        String.format("커넥션 사용량 높음: %d/%d (%d%%)",
                                usedConnections, maxConnections, (int)(ratio*100))
                ));
            }
        }

        // 과도한 대기 세션 감지
        long totalSessions = sessions.size();
        long waitingSessions = sessions.stream().filter(s -> s.getWaitEventType() != null).count();
        double waitingRatio = totalSessions > 0 ? (double) waitingSessions / totalSessions : 0;
        if (waitingRatio >= WAITING_SESSION_RATIO) {
            events.add(buildEvent(
                    databaseId, instanceId, databaseName, instanceName,
                    EventType.TOO_MANY_WAITING.name(),
                    ResourceType.SESSION.name(),
                    EventLevel.WARN.name(),
                    String.format("과도한 대기 세션: %d/%d (%d%%)",
                            waitingSessions, totalSessions, (int)(waitingRatio*100))
            ));
        }

        // Idle in Transaction 급증 감지
        long idleInTxCount = sessions.stream()
                .filter(s -> "idle in transaction".equals(s.getState())
                        || "idle in transaction (aborted)".equals(s.getState()))
                .count();
        if (idleInTxCount >= IDLE_IN_TX_THRESHOLD) {
            events.add(buildEvent(
                    databaseId, instanceId, databaseName, instanceName,
                    EventType.IDLE_IN_TRANSACTION_SURGE.name(),
                    ResourceType.SESSION.name(),
                    EventLevel.INFO.name(),
                    String.format("유휴 트랜잭션 급증: %d개 세션 감지됨", idleInTxCount)
            ));
        }

        return events;
    }

    /** 개별 세션 이벤트 빌더 (userName 포함) */
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

    /** 전체 통계 이벤트 빌더 (userName 없음) */
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

}
