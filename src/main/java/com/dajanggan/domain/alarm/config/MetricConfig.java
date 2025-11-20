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

            case "blockers_per_hour" ->
                    "SELECT COUNT(*)::NUMERIC FROM pg_locks WHERE NOT granted";

            case "transaction_age" ->
                    "SELECT COALESCE(MAX(EXTRACT(EPOCH FROM (NOW() - xact_start))), 0)::NUMERIC " +
                            "FROM pg_stat_activity WHERE xact_start IS NOT NULL AND state != 'idle'";

            case "block_duration" ->
                    "SELECT COALESCE(MAX(EXTRACT(EPOCH FROM (NOW() - query_start))), 0)::NUMERIC " +
                            "FROM pg_stat_activity WHERE wait_event_type = 'Lock'";

            case "wraparound_progress" ->
                    "SELECT COALESCE(MAX(age(relfrozenxid)::NUMERIC / " +
                            "(SELECT setting::NUMERIC FROM pg_settings WHERE name = 'autovacuum_freeze_max_age') * 100), 0) " +
                            "FROM pg_class WHERE relkind = 'r'";

            case "total_table_bloat" ->
                    "SELECT COALESCE(SUM(CASE WHEN n_live_tup + n_dead_tup > 0 " +
                            "THEN (n_dead_tup::NUMERIC / (n_live_tup + n_dead_tup)) * pg_total_relation_size(relid) " +
                            "ELSE 0 END), 0) FROM pg_stat_user_tables";

            case "bloat_percent" ->
                    "SELECT COALESCE(AVG(CASE WHEN n_live_tup + n_dead_tup > 0 " +
                            "THEN (n_dead_tup::NUMERIC / (n_live_tup + n_dead_tup)) * 100 " +
                            "ELSE 0 END), 0) FROM pg_stat_user_tables";

            case "dead_tuples" ->
                    "SELECT COALESCE(SUM(n_dead_tup), 0)::NUMERIC FROM pg_stat_user_tables";

            case "table_size" ->
                    "SELECT COALESCE(SUM(pg_total_relation_size(relid)), 0)::NUMERIC FROM pg_stat_user_tables";

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
     * - avg_5m: 5분 집계 테이블에서 평균
     * - avg_15m: 1분 집계 테이블에서 15분 평균
     * - p95_15m: 1분 집계 테이블에서 15분 95퍼센타일
     */
    public String getAggregatedMetricQuery(String metricType, String aggregationType) {
        // 집계 테이블 컬럼 매핑
        String columnName = getAggregationColumn(metricType);
        if (columnName == null) {
            return null; // 집계 테이블에 없는 지표
        }

        return switch (aggregationType) {
            case "avg_5m" -> {
                String tableName = getAggregationTable(metricType, "5m");
                if (tableName == null) {
                    yield null;
                }
                yield String.format(
                    "SELECT AVG(%s)::NUMERIC FROM %s " +
                    "WHERE instance_id = ? AND database_id = ? " +
                    "AND collected_at >= CURRENT_TIMESTAMP - INTERVAL '5 minutes'",
                    columnName, tableName
                );
            }
            case "avg_15m" -> {
                String tableName = getAggregationTable(metricType, "1m");
                if (tableName == null) {
                    yield null;
                }
                yield String.format(
                    "SELECT AVG(%s)::NUMERIC FROM %s " +
                    "WHERE instance_id = ? AND database_id = ? " +
                    "AND collected_at >= CURRENT_TIMESTAMP - INTERVAL '15 minutes'",
                    columnName, tableName
                );
            }
            case "p95_15m" -> {
                String tableName = getAggregationTable(metricType, "1m");
                if (tableName == null) {
                    yield null;
                }
                yield String.format(
                    "SELECT PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY %s)::NUMERIC " +
                    "FROM %s " +
                    "WHERE instance_id = ? AND database_id = ? " +
                    "AND collected_at >= CURRENT_TIMESTAMP - INTERVAL '15 minutes' " +
                    "AND %s IS NOT NULL",
                    columnName, tableName, columnName
                );
            }
            default -> null;
        };
    }

    /**
     * 지표 타입에 따른 집계 테이블 컬럼명 반환
     */
    private String getAggregationColumn(String metricType) {
        return switch (metricType) {
            // Vacuum 관련 - 집계 테이블에 있는 지표들
            case "dead_tuples" -> "total_dead_tuples";
            case "bloat_percent" -> "avg_bloat_ratio";
            case "total_table_bloat" -> "total_bloat_bytes";
            case "autovacuum_worker_utilization" -> "worker_utilization_pct";
            
            // 쿼리 관련 - 이미 집계 테이블 사용
            case "slow_query_spike", "avg_execution_spike", "qps_spike" -> null; // 이미 집계 테이블 쿼리 사용
            
            // 세션 관련 - 집계 테이블 확인 필요
            // case "long_running_queries" -> ... (session_metrics_agg 테이블 확인 필요)
            
            default -> null; // 집계 테이블에 없는 지표
        };
    }

    /**
     * 지표 타입과 집계 주기에 따른 집계 테이블명 반환
     */
    private String getAggregationTable(String metricType, String interval) {
        // 지표 타입에 따라 적절한 집계 테이블 선택
        if (metricType.startsWith("dead_tuples") || 
            metricType.startsWith("bloat") || 
            metricType.equals("autovacuum_worker_utilization") ||
            metricType.equals("total_table_bloat")) {
            // Vacuum 관련 지표
            return interval.equals("5m") ? "vacuum_metrics_agg_5m" : "vacuum_metrics_agg_1m";
        }
        
        // 다른 지표 타입들은 추후 추가
        return null;
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
