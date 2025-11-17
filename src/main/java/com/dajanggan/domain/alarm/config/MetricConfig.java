package com.dajanggan.domain.alarm.config;

import org.springframework.stereotype.Component;

@Component
public class MetricConfig {

    /**
     * 지표별 수집 쿼리 매핑
     */
    public String getMetricQuery(String metricType) {
        return switch (metricType) {
            case "dead_tuples" ->
                    "SELECT SUM(n_dead_tup)::NUMERIC FROM pg_stat_user_tables";

            case "bloat_size" ->
                    "SELECT SUM(pg_relation_size(schemaname||'.'||tablename))::NUMERIC " +
                            "FROM pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema')";

            case "unused_indexes" ->
                    "SELECT COUNT(*)::NUMERIC FROM pg_stat_user_indexes WHERE idx_scan = 0";

            case "connection_count" ->
                    "SELECT COUNT(*)::NUMERIC FROM pg_stat_activity WHERE state = 'active'";

            case "long_running_queries" ->
                    "SELECT COUNT(*)::NUMERIC FROM pg_stat_activity " +
                            "WHERE state = 'active' AND query_start < NOW() - INTERVAL '5 minutes'";

            case "cache_hit_ratio" ->
                    "SELECT ROUND((sum(blks_hit) * 100.0 / NULLIF(sum(blks_hit + blks_read), 0))::NUMERIC, 2) " +
                            "FROM pg_stat_database";

            case "table_bloat_ratio" ->
                    "SELECT ROUND(AVG((pg_stat_get_live_tuples(c.oid)::FLOAT / " +
                            "NULLIF(pg_stat_get_tuples_inserted(c.oid), 0)) * 100)::NUMERIC, 2) " +
                            "FROM pg_class c WHERE c.relkind = 'r'";

            case "lock_waits" ->
                    "SELECT COUNT(*)::NUMERIC FROM pg_locks WHERE NOT granted";

            case "sequential_scans" ->
                    "SELECT SUM(seq_scan)::NUMERIC FROM pg_stat_user_tables";

            case "temp_files" ->
                    "SELECT SUM(temp_files)::NUMERIC FROM pg_stat_database";

            case "replication_lag" ->
                    "SELECT EXTRACT(EPOCH FROM (NOW() - pg_last_xact_replay_timestamp()))::NUMERIC";

            case "long_idle_sessions" ->
                    "SELECT COUNT(*)::NUMERIC FROM pg_stat_activity " +
                            "WHERE state = 'idle in transaction' " +
                            "AND state_change < NOW() - INTERVAL '10 minutes'";

            case "blocking_sessions" ->
                    "SELECT COUNT(DISTINCT blocking.pid)::NUMERIC FROM pg_stat_activity blocked " +
                            "JOIN pg_stat_activity blocking ON blocking.pid = ANY(pg_blocking_pids(blocked.pid)) " +
                            "WHERE blocked.wait_event_type IS NOT NULL";

            default -> null;
        };
    }

    /**
     * 지표별 관련 객체 조회 쿼리
     */
    public String getRelatedObjectsQuery(String metricType) {
        return switch (metricType) {
            case "dead_tuples" -> """
                SELECT
                    'table' AS object_type,
                    relname AS object_name,
                    n_dead_tup AS metric_value,
                    CASE
                        WHEN n_dead_tup > 1000000 THEN '위험'
                        WHEN n_dead_tup > 500000 THEN '경고'
                        WHEN n_dead_tup > 100000 THEN '주의'
                        ELSE '정상'
                    END AS status
                FROM pg_stat_user_tables
                WHERE n_dead_tup > 100000
                ORDER BY n_dead_tup DESC
                LIMIT 10
                """;

            case "bloat_size" -> """
                SELECT 
                    'table' AS object_type,
                    schemaname||'.'||tablename AS object_name,
                    pg_relation_size(schemaname||'.'||tablename) AS metric_value,
                    CASE 
                        WHEN pg_relation_size(schemaname||'.'||tablename) > 5368709120 THEN '위험'
                        WHEN pg_relation_size(schemaname||'.'||tablename) > 1073741824 THEN '경고'
                        WHEN pg_relation_size(schemaname||'.'||tablename) > 536870912 THEN '주의'
                        ELSE '정상'
                    END AS status
                FROM pg_tables
                WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
                ORDER BY pg_relation_size(schemaname||'.'||tablename) DESC
                LIMIT 10
                """;

            case "unused_indexes" -> """
                SELECT 
                    'index' AS object_type,
                    schemaname||'.'||indexrelname AS object_name,
                    idx_scan AS metric_value,
                    CASE 
                        WHEN idx_scan = 0 THEN '위험'
                        WHEN idx_scan < 10 THEN '경고'
                        WHEN idx_scan < 100 THEN '주의'
                        ELSE '정상'
                    END AS status
                FROM pg_stat_user_indexes
                WHERE idx_scan < 100
                ORDER BY pg_relation_size(indexrelid) DESC
                LIMIT 10
                """;

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

            default -> null;
        };
    }
}
