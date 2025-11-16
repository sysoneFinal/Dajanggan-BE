package com.dajanggan.domain.overview.service;

import com.dajanggan.domain.instance.domain.Instance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EventMetricsService {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public EventMetricsService(NamedParameterJdbcTemplate namedJdbcTemplate) {
        this.namedJdbcTemplate = namedJdbcTemplate;
    }

    public record EventStats(
            int infoCount,
            int warningCount,
            int criticalCount,
            String recentEventType,
            String recentEventLevel,
            long recentEventAgeMin
    ) {}

    public EventStats aggregate(Instance instance, String databaseName,
                                Long instanceId, Long databaseId,
                                java.time.OffsetDateTime collectedAt) {

        // Event counts (최근 5분)
        String eventCountSql = """
            SELECT 
                COALESCE(COUNT(*) FILTER (WHERE level='INFO'), 0) as info_count,
                COALESCE(COUNT(*) FILTER (WHERE level='WARN'), 0) as warning_count,
                COALESCE(COUNT(*) FILTER (WHERE level='CRITICAL'), 0) as critical_count
            FROM event_log
            WHERE instance_id = :instanceId
              AND database_id = :databaseId
              AND detected_at >= :fiveMinutesAgo
        """;

        MapSqlParameterSource eventCountParams = new MapSqlParameterSource()
                .addValue("instanceId", instanceId)
                .addValue("databaseId", databaseId)
                .addValue("fiveMinutesAgo", collectedAt.minusMinutes(5));

        var eventCountResult = namedJdbcTemplate.queryForMap(eventCountSql, eventCountParams);

        int infoCount = ((Number) eventCountResult.get("info_count")).intValue();
        int warningCount = ((Number) eventCountResult.get("warning_count")).intValue();
        int criticalCount = ((Number) eventCountResult.get("critical_count")).intValue();

        // Recent event
        String recentEventSql = """
            SELECT event_type, level, detected_at
            FROM event_log
            WHERE instance_id = :instanceId
              AND database_id = :databaseId
            ORDER BY detected_at DESC
            LIMIT 1
        """;

        MapSqlParameterSource recentEventParams = new MapSqlParameterSource()
                .addValue("instanceId", instanceId)
                .addValue("databaseId", databaseId);

        String recentEventType = null;
        String recentEventLevel = null;
        long recentEventAgeMin = 0;

        try {
            var recentEventResult = namedJdbcTemplate.queryForMap(recentEventSql, recentEventParams);
            recentEventType = (String) recentEventResult.get("event_type");
            recentEventLevel = (String) recentEventResult.get("level");
            
            java.time.OffsetDateTime detectedAt = (java.time.OffsetDateTime) recentEventResult.get("detected_at");
            if (detectedAt != null) {
                recentEventAgeMin = java.time.Duration.between(detectedAt, collectedAt).toMinutes();
            }
        } catch (Exception e) {
            log.debug("최근 이벤트 없음: instanceId={}, databaseId={}", instanceId, databaseId);
        }

        return new EventStats(
                infoCount, warningCount, criticalCount,
                recentEventType, recentEventLevel, recentEventAgeMin
        );
    }
}
