package com.dajanggan.domain.event.detector;

import com.dajanggan.domain.event.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 쿼리 이벤트 감지기
 * - SessionEventDetector와 동일한 패턴
 * - 5가지 이벤트만 감지
 */
@Slf4j
@Component
public class QueryEventDetector {

    // 임계값
    private static final double SLOW_QUERY_THRESHOLD_MS = 1000.0;
    private static final double VERY_SLOW_QUERY_THRESHOLD_MS = 5000.0;
    private static final double HIGH_CUMULATIVE_LOAD_THRESHOLD_MS = 30000.0;

    /**
     * 쿼리 통계에서 이벤트 감지
     */
    public List<EventLog> detectEvents(List<Map<String, Object>> queryStats,
                                       Long databaseId, Long instanceId,
                                       String databaseName, String instanceName) {
        List<EventLog> events = new ArrayList<>();

        if (queryStats == null || queryStats.isEmpty()) {
            return events;
        }

        for (Map<String, Object> stat : queryStats) {
            try {
                String queryId = getQueryId(stat);
                String shortQuery = getShortQuery(stat);

                // 1. SLOW_QUERY 감지
                Double avgExecTime = getDouble(stat, "avgExecutionTimeMs");
                if (avgExecTime != null) {
                    if (avgExecTime >= VERY_SLOW_QUERY_THRESHOLD_MS) {
                        events.add(buildEvent(databaseId, instanceId, databaseName, instanceName,
                                EventType.SLOW_QUERY.name(), EventLevel.CRITICAL.name(),
                                queryId, shortQuery, avgExecTime,
                                String.format("매우 느린 쿼리 감지: 평균 %.1f초", avgExecTime / 1000.0)));
                    } else if (avgExecTime >= SLOW_QUERY_THRESHOLD_MS) {
                        events.add(buildEvent(databaseId, instanceId, databaseName, instanceName,
                                EventType.SLOW_QUERY.name(), EventLevel.WARN.name(),
                                queryId, shortQuery, avgExecTime,
                                String.format("슬로우 쿼리 감지: 평균 %.1f초", avgExecTime / 1000.0)));
                    }
                }

                // 2. HIGH_CUMULATIVE_LOAD_QUERY 감지
                Double totalTime = getDouble(stat, "totalExecutionTimeMs");
                Integer execCount = getInteger(stat, "executionCount");

                if (totalTime != null && execCount != null && execCount > 0) {
                    if (totalTime >= HIGH_CUMULATIVE_LOAD_THRESHOLD_MS * 2) {
                        events.add(buildEvent(databaseId, instanceId, databaseName, instanceName,
                                EventType.HIGH_CUMULATIVE_LOAD_QUERY.name(), EventLevel.CRITICAL.name(),
                                queryId, shortQuery, totalTime / 1000.0,
                                String.format("높은 누적 부하: 5분간 총 %.1f초 (%d회)", totalTime / 1000.0, execCount)));
                    } else if (totalTime >= HIGH_CUMULATIVE_LOAD_THRESHOLD_MS) {
                        events.add(buildEvent(databaseId, instanceId, databaseName, instanceName,
                                EventType.HIGH_CUMULATIVE_LOAD_QUERY.name(), EventLevel.WARN.name(),
                                queryId, shortQuery, totalTime / 1000.0,
                                String.format("높은 누적 부하: 5분간 총 %.1f초 (%d회)", totalTime / 1000.0, execCount)));
                    }
                }
            } catch (Exception e) {
                log.error("쿼리 이벤트 감지 중 오류: {}", e.getMessage());
            }
        }

        return events;
    }

    /**
     * 급증 이벤트 생성 (알림과 함께 사용)
     */
    public EventLog createSpikeEvent(String spikeType, Double value, Double threshold,
                                     Long databaseId, Long instanceId,
                                     String databaseName, String instanceName) {
        EventType eventType;
        String description;

        switch (spikeType) {
            case "slow_query_spike":
                eventType = EventType.SLOW_QUERY_SPIKE;
                description = String.format("슬로우 쿼리 급증: %.0f개 (임계값: %.0f)", value, threshold);
                break;
            case "avg_execution_spike":
                eventType = EventType.AVG_EXECUTION_SPIKE;
                description = String.format("평균 실행시간 급증: %.1fms (임계값: %.1fms)", value, threshold);
                break;
            case "qps_spike":
                eventType = EventType.QPS_SPIKE;
                description = String.format("QPS 급증: %.0f (임계값: %.0f)", value, threshold);
                break;
            default:
                log.warn("알 수 없는 급증 타입: {}", spikeType);
                return null;
        }

        return EventLog.builder()
                .instanceId(instanceId)
                .databaseId(databaseId)
                .instanceName(instanceName)
                .databaseName(databaseName)
                .category(EventCategory.QUERY.name())
                .eventType(eventType.name())
                .level(EventLevel.CRITICAL.name())
                .userName(null)
                .resourceType(ResourceType.QUERY.name())
                .detectedAt(OffsetDateTime.now())
                .duration(value)
                .description(description)
                .build();
    }

    /** 이벤트 빌더 */
    private EventLog buildEvent(Long databaseId, Long instanceId,
                                String databaseName, String instanceName,
                                String eventType, String level,
                                String queryId, String shortQuery,
                                Double duration, String description) {
        String query = shortQuery != null && shortQuery.length() > 50
                ? shortQuery.substring(0, 50) + "..."
                : shortQuery;

        return EventLog.builder()
                .instanceId(instanceId)
                .databaseId(databaseId)
                .instanceName(instanceName)
                .databaseName(databaseName)
                .category(EventCategory.QUERY.name())
                .eventType(eventType)
                .level(level)
                .userName(null)
                .resourceType(ResourceType.QUERY.name())
                .detectedAt(OffsetDateTime.now())
                .duration(duration)
                .description(String.format("[%s] %s - %s", queryId, query, description))
                .build();
    }

    /** Helper 메서드 */
    private String getQueryId(Map<String, Object> map) {
        Object id = map.get("queryMetricId");
        return id != null ? "#" + id.toString() : "Unknown";
    }

    private String getShortQuery(Map<String, Object> map) {
        Object query = map.get("shortQuery");
        return query != null ? query.toString() : "N/A";
    }

    private Double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer getInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}