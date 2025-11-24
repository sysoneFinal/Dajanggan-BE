package com.dajanggan.domain.alarm.config;

import org.springframework.stereotype.Component;

@Component
public class MetricConfig {

    /**
     * 지표별 수집 쿼리 매핑
     */
    public String getMetricQuery(String metricType) {
        return switch (metricType) {
            // vacuum
            case "autovacuum_worker_utilization" ->
                    "SELECT (COUNT(*) FILTER (WHERE query LIKE 'autovacuum:%')::NUMERIC / " +
                            "NULLIF((SELECT setting::NUMERIC FROM pg_settings WHERE name = 'autovacuum_max_workers'), 0) * 100) " +
                            "FROM pg_stat_activity";

            case "transaction_age" ->
                    "SELECT COALESCE(MAX(EXTRACT(EPOCH FROM (NOW() - xact_start))), 0)::NUMERIC " +
                            "FROM pg_stat_activity WHERE xact_start IS NOT NULL AND state != 'idle'";

            case "wraparound_progress" ->
                    "SELECT COALESCE(MAX(age(relfrozenxid)::NUMERIC / " +
                            "(SELECT setting::NUMERIC FROM pg_settings WHERE name = 'autovacuum_freeze_max_age') * 100), 0) " +
                            "FROM pg_class WHERE relkind = 'r'";


            // 세션
            case "long_running_queries" ->
                    "SELECT COUNT(*)::NUMERIC FROM pg_stat_activity " +
                            "WHERE state = 'active' AND query_start < NOW() - INTERVAL '5 minutes'";

            case "lock_waits" ->
                    "SELECT COUNT(*)::NUMERIC FROM pg_locks WHERE NOT granted";

            case "long_idle_sessions" ->
                    "SELECT COUNT(*)::NUMERIC FROM pg_stat_activity " +
                            "WHERE state = 'idle in transaction' " +
                            "AND state_change < NOW() - INTERVAL '10 minutes'";

            case "blocking_sessions" ->
                    "SELECT COUNT(DISTINCT blocking.pid)::NUMERIC FROM pg_stat_activity blocked " +
                            "JOIN pg_stat_activity blocking ON blocking.pid = ANY(pg_blocking_pids(blocked.pid)) " +
                            "WHERE blocked.wait_event_type IS NOT NULL";

            // 쿼리
            case "slow_query_spike" ->
                    "SELECT COALESCE(SUM(slow_query_count), 0) " +
                            "FROM query_metrics_agg_1m " +
                            "WHERE instance_id = ? AND database_id = ? " +
                            "AND collected_at >= CURRENT_TIMESTAMP - INTERVAL '5 minutes'";

            case "avg_execution_spike" ->
                    "SELECT AVG(avg_execution_time_ms) " +
                            "FROM query_metrics_agg_1m " +
                            "WHERE instance_id = ? AND database_id = ? " +
                            "AND collected_at >= CURRENT_TIMESTAMP - INTERVAL '5 minutes'";


            case "qps_spike" ->
                    "SELECT CAST(COALESCE(SUM(total_queries), 0) / 300.0 AS INTEGER) " +
                            "FROM query_metrics_agg_1m " +
                            "WHERE instance_id = ? AND database_id = ? " +
                            "AND collected_at >= CURRENT_TIMESTAMP - INTERVAL '5 minutes'";

            default -> null;
        };
    }

    /**
     * 집계 타입별 지표 쿼리
     * 집계 기능 제거 - 항상 null 반환 (실시간 값만 사용)
     */
    public String getAggregatedMetricQuery(String metricType, String aggregationType) {
        return null;
    }

    /**
     * 지표별 관련 객체 조회 쿼리
     */
    public String getRelatedObjectsQuery(String metricType) {
        return switch (metricType) {
            

            case "long_running_queries" -> """
                SELECT 
                    'query' AS object_type,
                    LEFT(query, 100) AS object_name,
                    EXTRACT(EPOCH FROM (NOW() - query_start)) AS metric_value,
                    CASE 
                        WHEN query_start < NOW() - INTERVAL '30 minutes' THEN '위험'
                        WHEN query_start < NOW() - INTERVAL '10 minutes' THEN '경고'
                        WHEN query_start < NOW() - INTERVAL '5 minutes' THEN '주의'
                        ELSE '정상'
                    END AS status
                FROM pg_stat_activity
                WHERE state = 'active'
                  AND query_start < NOW() - INTERVAL '5 minutes'
                ORDER BY query_start ASC
                LIMIT 10
                """;

            case "sequential_scans" -> """
                SELECT 
                    'table' AS object_type,
                    relname AS object_name,
                    seq_scan AS metric_value,
                    CASE 
                        WHEN seq_scan > 10000 THEN '위험'
                        WHEN seq_scan > 5000 THEN '경고'
                        WHEN seq_scan > 1000 THEN '주의'
                        ELSE '정상'
                    END AS status
                FROM pg_stat_user_tables
                WHERE seq_scan > 1000
                ORDER BY seq_scan DESC
                LIMIT 10
                """;

            case "wraparound_progress" -> """
                SELECT 
                    'table' AS object_type,
                    n.nspname || '.' || c.relname AS object_name,
                    ROUND(
                        (age(c.relfrozenxid)::NUMERIC / 
                         NULLIF((SELECT setting::NUMERIC FROM pg_settings WHERE name = 'autovacuum_freeze_max_age'), 0) * 100)::NUMERIC,
                        2
                    ) AS metric_value,
                    CASE 
                        WHEN age(c.relfrozenxid) > (SELECT setting::BIGINT FROM pg_settings WHERE name = 'autovacuum_freeze_max_age') * 0.9 THEN '위험'
                        WHEN age(c.relfrozenxid) > (SELECT setting::BIGINT FROM pg_settings WHERE name = 'autovacuum_freeze_max_age') * 0.7 THEN '경고'
                        WHEN age(c.relfrozenxid) > (SELECT setting::BIGINT FROM pg_settings WHERE name = 'autovacuum_freeze_max_age') * 0.5 THEN '주의'
                        ELSE '정상'
                    END AS status
                FROM pg_class c
                JOIN pg_namespace n ON c.relnamespace = n.oid
                WHERE c.relkind = 'r'
                  AND n.nspname NOT IN ('pg_catalog', 'information_schema', 'pg_toast')
                  AND age(c.relfrozenxid) > (SELECT setting::BIGINT FROM pg_settings WHERE name = 'autovacuum_freeze_max_age') * 0.1
                ORDER BY age(c.relfrozenxid) DESC
                LIMIT 10
                """;

            case "autovacuum_worker_utilization" -> """
                SELECT 
                    'table' AS object_type,
                    schemaname || '.' || relname AS object_name,
                    ROUND(
                        (n_dead_tup::NUMERIC / NULLIF(n_live_tup + n_dead_tup, 0) * 100)::NUMERIC,
                        2
                    ) AS metric_value,
                    CASE 
                        WHEN n_dead_tup > 1000000 THEN '위험'
                        WHEN n_dead_tup > 500000 THEN '경고'
                        WHEN n_dead_tup > 100000 THEN '주의'
                        ELSE '정상'
                    END AS status
                FROM pg_stat_all_tables
                WHERE schemaname NOT IN ('pg_catalog', 'information_schema', 'pg_toast')
                  AND n_dead_tup > 100000
                  AND (last_autovacuum IS NOT NULL OR last_vacuum IS NOT NULL)
                ORDER BY n_dead_tup DESC
                LIMIT 10
                """;

            case "transaction_age" -> """
                SELECT 
                    'session' AS object_type,
                    COALESCE(LEFT(query, 50), 'N/A') AS object_name,
                    EXTRACT(EPOCH FROM (NOW() - xact_start))::NUMERIC AS metric_value,
                    CASE 
                        WHEN xact_start < NOW() - INTERVAL '1 hour' THEN '위험'
                        WHEN xact_start < NOW() - INTERVAL '30 minutes' THEN '경고'
                        WHEN xact_start < NOW() - INTERVAL '10 minutes' THEN '주의'
                        ELSE '정상'
                    END AS status
                FROM pg_stat_activity
                WHERE xact_start IS NOT NULL 
                  AND state != 'idle'
                  AND xact_start < NOW() - INTERVAL '5 minutes'
                ORDER BY xact_start ASC
                LIMIT 10
                """;

            case "lock_waits" -> """
                SELECT 
                    'session' AS object_type,
                    COALESCE(LEFT(query, 50), 'N/A') AS object_name,
                    EXTRACT(EPOCH FROM (NOW() - state_change))::NUMERIC AS metric_value,
                    CASE 
                        WHEN state_change < NOW() - INTERVAL '30 minutes' THEN '위험'
                        WHEN state_change < NOW() - INTERVAL '10 minutes' THEN '경고'
                        WHEN state_change < NOW() - INTERVAL '5 minutes' THEN '주의'
                        ELSE '정상'
                    END AS status
                FROM pg_stat_activity
                WHERE wait_event_type IS NOT NULL
                  AND wait_event_type != 'Client'
                ORDER BY state_change ASC
                LIMIT 10
                """;

            case "long_idle_sessions" -> """
                SELECT 
                    'session' AS object_type,
                    COALESCE(LEFT(query, 50), 'N/A') AS object_name,
                    EXTRACT(EPOCH FROM (NOW() - state_change))::NUMERIC AS metric_value,
                    CASE 
                        WHEN state_change < NOW() - INTERVAL '1 hour' THEN '위험'
                        WHEN state_change < NOW() - INTERVAL '30 minutes' THEN '경고'
                        WHEN state_change < NOW() - INTERVAL '10 minutes' THEN '주의'
                        ELSE '정상'
                    END AS status
                FROM pg_stat_activity
                WHERE state = 'idle in transaction'
                  AND state_change < NOW() - INTERVAL '10 minutes'
                ORDER BY state_change ASC
                LIMIT 10
                """;

            case "blocking_sessions" -> """
                SELECT 
                    'session' AS object_type,
                    COALESCE(LEFT(query, 50), 'N/A') AS object_name,
                    EXTRACT(EPOCH FROM (NOW() - xact_start))::NUMERIC AS metric_value,
                    CASE 
                        WHEN xact_start < NOW() - INTERVAL '1 hour' THEN '위험'
                        WHEN xact_start < NOW() - INTERVAL '30 minutes' THEN '경고'
                        WHEN xact_start < NOW() - INTERVAL '10 minutes' THEN '주의'
                        ELSE '정상'
                    END AS status
                FROM pg_stat_activity
                WHERE pid IN (
                    SELECT DISTINCT blocking.pid
                    FROM pg_stat_activity blocked
                    JOIN pg_stat_activity blocking ON blocking.pid = ANY(pg_blocking_pids(blocked.pid))
                    WHERE blocked.wait_event_type IS NOT NULL
                )
                ORDER BY xact_start ASC
                LIMIT 10
                """;

            case "slow_query_spike" -> """
                SELECT 
                    'query' AS object_type,
                    COALESCE(LEFT(query, 50), 'N/A') AS object_name,
                    EXTRACT(EPOCH FROM (NOW() - query_start))::NUMERIC AS metric_value,
                    CASE 
                        WHEN query_start < NOW() - INTERVAL '1 hour' THEN '위험'
                        WHEN query_start < NOW() - INTERVAL '30 minutes' THEN '경고'
                        WHEN query_start < NOW() - INTERVAL '10 minutes' THEN '주의'
                        ELSE '정상'
                    END AS status
                FROM pg_stat_activity
                WHERE state = 'active'
                  AND query_start < NOW() - INTERVAL '5 minutes'
                ORDER BY query_start ASC
                LIMIT 10
                """;

            case "avg_execution_spike" -> """
                SELECT 
                    'query' AS object_type,
                    COALESCE(LEFT(query, 50), 'N/A') AS object_name,
                    EXTRACT(EPOCH FROM (NOW() - query_start))::NUMERIC AS metric_value,
                    CASE 
                        WHEN query_start < NOW() - INTERVAL '1 hour' THEN '위험'
                        WHEN query_start < NOW() - INTERVAL '30 minutes' THEN '경고'
                        WHEN query_start < NOW() - INTERVAL '10 minutes' THEN '주의'
                        ELSE '정상'
                    END AS status
                FROM pg_stat_activity
                WHERE state = 'active'
                  AND query_start < NOW() - INTERVAL '5 minutes'
                ORDER BY query_start ASC
                LIMIT 10
                """;

            case "qps_spike" -> """
                SELECT 
                    'query' AS object_type,
                    COALESCE(LEFT(query, 50), 'N/A') AS object_name,
                    calls::NUMERIC AS metric_value,
                    CASE 
                        WHEN calls > 1000000 THEN '위험'
                        WHEN calls > 500000 THEN '경고'
                        WHEN calls > 100000 THEN '주의'
                        ELSE '정상'
                    END AS status
                FROM pg_stat_statements
                WHERE calls > 100000
                ORDER BY calls DESC
                LIMIT 10
                """;

            default -> null;
        };
    }
}
