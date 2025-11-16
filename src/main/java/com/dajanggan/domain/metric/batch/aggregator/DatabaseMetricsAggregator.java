package com.dajanggan.domain.metric.batch.aggregator;

import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.overview.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class DatabaseMetricsAggregator {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final TransactionMetricsService transactionMetricsService;
    private final SessionMetricsService sessionMetricsService;
    private final DiskMetricsService diskMetricsService;
    private final QueryMetricsService queryMetricsService;
    private final EventMetricsService eventMetricsService;

    public DatabaseMetricsAggregator(
            NamedParameterJdbcTemplate namedJdbcTemplate,
            TransactionMetricsService transactionMetricsService,
            SessionMetricsService sessionMetricsService,
            DiskMetricsService diskMetricsService,
            QueryMetricsService queryMetricsService,
            EventMetricsService eventMetricsService
    ) {
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.transactionMetricsService = transactionMetricsService;
        this.sessionMetricsService = sessionMetricsService;
        this.diskMetricsService = diskMetricsService;
        this.queryMetricsService = queryMetricsService;
        this.eventMetricsService = eventMetricsService;
    }

    private record DatabaseInfo(Instance instance, Long databaseId, String databaseName) {}

    public void aggregateAllDatabases() {
        String getDatabasesSql = """
                SELECT md.instance_id, md.database_id, md.database_name,
                       mi.instance_name, mi.host, mi.port, mi.username, mi.secret_ref, mi.sslmode
                FROM monitor_database md
                JOIN monitor_instance mi ON md.instance_id = mi.instance_id
                WHERE md.is_enabled = true
                ORDER BY md.instance_id, md.database_id
                """;

        List<DatabaseInfo> databaseInfos = namedJdbcTemplate.getJdbcTemplate()
                .query(getDatabasesSql, (rs, rowNum) -> {
                    Instance instance = Instance.builder()
                            .instanceId(rs.getLong("instance_id"))
                            .instanceName(rs.getString("instance_name"))
                            .host(rs.getString("host"))
                            .port(rs.getInt("port"))
                            .userName(rs.getString("username"))
                            .secretRef(rs.getString("secret_ref"))
                            .sslmode(rs.getString("sslmode"))
                            .build();

                    return new DatabaseInfo(instance, rs.getLong("database_id"), rs.getString("database_name"));
                });

        log.info("데이터베이스 메트릭 집계 시작: {}개 데이터베이스", databaseInfos.size());

        int successCount = 0;
        List<DatabaseInfo> failedDatabases = new ArrayList<>();

        for (DatabaseInfo dbInfo : databaseInfos) {
            try {
                aggregateDatabaseMetrics(dbInfo);
                successCount++;
            } catch (Exception e) {
                log.error("데이터베이스 메트릭 집계 실패: instanceId={}, databaseId={}, error={}",
                        dbInfo.instance().getInstanceId(), dbInfo.databaseId(), e.getMessage(), e);
                failedDatabases.add(dbInfo);
            }
        }

        log.info("데이터베이스 메트릭 집계 완료: 성공={}, 실패={}", successCount, failedDatabases.size());

        if (!failedDatabases.isEmpty()) {
            log.warn("실패한 데이터베이스 목록: {}", failedDatabases);
        }
    }

    @Transactional
    public void aggregateDatabaseMetrics(DatabaseInfo dbInfo) {
        OffsetDateTime collectedAt = OffsetDateTime.now().withSecond(0).withNano(0);
        OffsetDateTime oneMinuteAgo = collectedAt.minusMinutes(1);

        Long instanceId = dbInfo.instance().getInstanceId();
        Long databaseId = dbInfo.databaseId();
        String databaseName = dbInfo.databaseName();

        // 1. TPS 계산
        var txStats = transactionMetricsService.calculateTPS(
                dbInfo.instance(), databaseName, instanceId, databaseId);

        // 2. 세션 통계
        var sessionStats = sessionMetricsService.aggregate(
                dbInfo.instance(), databaseName, instanceId, databaseId, oneMinuteAgo, collectedAt);

        // 3. 디스크 통계
        var diskStats = diskMetricsService.aggregate(
                dbInfo.instance(), databaseName, instanceId, databaseId, oneMinuteAgo, collectedAt);

        // 4. 쿼리 통계
        var queryStats = queryMetricsService.aggregate(
                dbInfo.instance(), databaseName, instanceId, databaseId);

        // 5. 이벤트 통계
        var eventStats = eventMetricsService.aggregate(
                dbInfo.instance(), databaseName, instanceId, databaseId, collectedAt);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("instanceId", instanceId)
                .addValue("databaseId", databaseId)
                .addValue("collectedAt", collectedAt)
                .addValue("activeSessions", sessionStats.avgActiveSessions())
                .addValue("usedConnections", sessionStats.usedConnections())
                .addValue("maxConnections", sessionStats.maxConnections())
                .addValue("connectionUsagePercent", sessionStats.connectionUsagePercent())
                .addValue("tpsTotal", txStats.tps())
                .addValue("xactCommit", txStats.xactCommit())
                .addValue("xactRollback", txStats.xactRollback())
                .addValue("lockWaitCount", diskStats.lockWaitCount())
                .addValue("ioWaitCount", diskStats.ioWaitCount())
                .addValue("blksHit", diskStats.totalBlksHit())
                .addValue("blksRead", diskStats.totalBlksRead())
                .addValue("cacheHitRatio", diskStats.avgCacheHitRatio())
                .addValue("avgReadLatency", diskStats.avgReadLatency())
                .addValue("avgWriteLatency", diskStats.avgWriteLatency())
                .addValue("slowQueryCount", queryStats.slowQueryCount())
                .addValue("topSlowQuery1", queryStats.topSlowQuery1())
                .addValue("topSlowQuery1Time", queryStats.topSlowQuery1Time())
                .addValue("topSlowQuery2", queryStats.topSlowQuery2())
                .addValue("topSlowQuery2Time", queryStats.topSlowQuery2Time())
                .addValue("topSlowQuery3", queryStats.topSlowQuery3())
                .addValue("topSlowQuery3Time", queryStats.topSlowQuery3Time())
                .addValue("deadTupleTotal", queryStats.deadTupleTotal())
                .addValue("infoEventCount", eventStats.infoCount())
                .addValue("warningEventCount", eventStats.warningCount())
                .addValue("criticalEventCount", eventStats.criticalCount())
                .addValue("recentEventType", eventStats.recentEventType())
                .addValue("recentEventLevel", eventStats.recentEventLevel())
                .addValue("recentEventAgeMin", eventStats.recentEventAgeMin());

        String sql = """
            INSERT INTO database_metrics_agg (
                instance_id, database_id, collected_at,
                active_sessions, used_connections, max_connections, connection_usage_percent,
                tps_total, xact_commit, xact_rollback,
                lock_wait_count, io_wait_count,
                blks_hit, blks_read, cache_hit_ratio, avg_read_latency_ms, avg_write_latency_ms,
                slow_query_count, top_slow_query_1, top_slow_query_1_time,
                top_slow_query_2, top_slow_query_2_time,
                top_slow_query_3, top_slow_query_3_time,
                dead_tuple_total,
                info_event_count, warning_event_count, critical_event_count,
                recent_event_type, recent_event_level, recent_event_age_min
            )
            VALUES (
                :instanceId, :databaseId, :collectedAt,
                :activeSessions, :usedConnections, :maxConnections, :connectionUsagePercent,
                :tpsTotal, :xactCommit, :xactRollback,
                :lockWaitCount, :ioWaitCount,
                :blksHit, :blksRead, :cacheHitRatio, :avgReadLatency, :avgWriteLatency,
                :slowQueryCount, :topSlowQuery1, :topSlowQuery1Time,
                :topSlowQuery2, :topSlowQuery2Time,
                :topSlowQuery3, :topSlowQuery3Time,
                :deadTupleTotal,
                :infoEventCount, :warningEventCount, :criticalEventCount,
                :recentEventType, :recentEventLevel, :recentEventAgeMin
            )
            ON CONFLICT (instance_id, database_id, collected_at)
            DO UPDATE SET
                active_sessions = EXCLUDED.active_sessions,
                used_connections = EXCLUDED.used_connections,
                max_connections = EXCLUDED.max_connections,
                connection_usage_percent = EXCLUDED.connection_usage_percent,
                tps_total = EXCLUDED.tps_total,
                xact_commit = EXCLUDED.xact_commit,
                xact_rollback = EXCLUDED.xact_rollback,
                lock_wait_count = EXCLUDED.lock_wait_count,
                io_wait_count = EXCLUDED.io_wait_count,
                blks_hit = EXCLUDED.blks_hit,
                blks_read = EXCLUDED.blks_read,
                cache_hit_ratio = EXCLUDED.cache_hit_ratio,
                avg_read_latency_ms = EXCLUDED.avg_read_latency_ms,
                avg_write_latency_ms = EXCLUDED.avg_write_latency_ms,
                slow_query_count = EXCLUDED.slow_query_count,
                top_slow_query_1 = EXCLUDED.top_slow_query_1,
                top_slow_query_1_time = EXCLUDED.top_slow_query_1_time,
                top_slow_query_2 = EXCLUDED.top_slow_query_2,
                top_slow_query_2_time = EXCLUDED.top_slow_query_2_time,
                top_slow_query_3 = EXCLUDED.top_slow_query_3,
                top_slow_query_3_time = EXCLUDED.top_slow_query_3_time,
                dead_tuple_total = EXCLUDED.dead_tuple_total,
                info_event_count = EXCLUDED.info_event_count,
                warning_event_count = EXCLUDED.warning_event_count,
                critical_event_count = EXCLUDED.critical_event_count,
                recent_event_type = EXCLUDED.recent_event_type,
                recent_event_level = EXCLUDED.recent_event_level,
                recent_event_age_min = EXCLUDED.recent_event_age_min,
                created_at = now();
        """;

        int updated = namedJdbcTemplate.update(sql, params);

        if (updated > 0) {
            log.debug("데이터베이스 메트릭 집계 성공: instanceId={}, databaseId={}, TPS={}",
                    instanceId, databaseId, txStats.tps());
        } else {
            log.warn("데이터베이스 메트릭 집계 결과 없음: instanceId={}, databaseId={}", instanceId, databaseId);
        }
    }
}
