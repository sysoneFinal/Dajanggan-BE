package com.dajanggan.domain.overview.service;

import com.dajanggan.domain.instance.domain.Instance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
public class SessionMetricsService {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public SessionMetricsService(NamedParameterJdbcTemplate namedJdbcTemplate) {
        this.namedJdbcTemplate = namedJdbcTemplate;
    }

    public record SessionStats(
            long avgActiveSessions,
            long usedConnections,
            int maxConnections,
            BigDecimal connectionUsagePercent
    ) {}

    public SessionStats aggregate(Instance instance, String databaseName,
                                  Long instanceId, Long databaseId,
                                  java.time.OffsetDateTime from, java.time.OffsetDateTime to) {

        String sql = """
            SELECT 
                COALESCE(ROUND(AVG(active_sessions))::bigint, 0) AS avg_active_sessions,
                COALESCE(ROUND(AVG(used_connections))::bigint, 0) AS avg_used_connections,
                COALESCE(MAX(max_connections)::int, 0) AS max_connections_val
            FROM session_metrics_agg_1m
            WHERE instance_id = :instanceId
              AND database_id = :databaseId
              AND collected_at BETWEEN :from AND :to
        """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("instanceId", instanceId)
                .addValue("databaseId", databaseId)
                .addValue("from", from)
                .addValue("to", to);

        var result = namedJdbcTemplate.queryForMap(sql, params);

        long avgActive = ((Number) result.get("avg_active_sessions")).longValue();
        long avgUsed = ((Number) result.get("avg_used_connections")).longValue();
        int maxConn = ((Number) result.get("max_connections_val")).intValue();

        BigDecimal usagePercent = maxConn > 0
                ? BigDecimal.valueOf((double) avgUsed / maxConn * 100)  // avgActive → avgUsed로 변경
                .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new SessionStats(avgActive, avgUsed, maxConn, usagePercent);
    }
}
