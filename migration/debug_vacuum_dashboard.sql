-- ============================================================
-- Vacuum 대시보드 디버깅 쿼리
-- 실제 집계 데이터와 KPI 쿼리 결과를 비교
-- ============================================================

-- 1. 최근 24시간 집계 데이터 요약 (실제 집계 테이블)
SELECT 
    '집계 데이터 요약' as check_type,
    COUNT(*) as total_rows,
    COUNT(DISTINCT database_id || '_' || instance_id) as unique_instances,
    SUM(active_vacuum_sessions) as total_active_sessions,
    SUM(blocked_vacuum_count) as total_blocked_count,
    SUM(total_dead_tuples) as total_dead_tuples,
    AVG(avg_elapsed_seconds) FILTER (WHERE avg_elapsed_seconds > 0) as avg_elapsed_seconds,
    MAX(max_workers_configured) as max_workers_configured
FROM vacuum_metrics_agg_1m
WHERE collected_at >= NOW() - INTERVAL '24 hours';

-- 2. 특정 database_id, instance_id의 최근 24시간 집계 데이터
-- 아래 쿼리의 database_id와 instance_id를 실제 값으로 변경하여 실행
/*
SELECT 
    database_id,
    instance_id,
    COUNT(*) as agg_count,
    SUM(active_vacuum_sessions) as total_active,
    SUM(blocked_vacuum_count) as total_blocked,
    SUM(total_dead_tuples) as total_dead_tuples,
    AVG(avg_elapsed_seconds) FILTER (WHERE avg_elapsed_seconds > 0) as avg_elapsed,
    MAX(max_workers_configured) as max_workers
FROM vacuum_metrics_agg_1m
WHERE database_id = 1  -- 실제 값으로 변경
  AND instance_id = 1   -- 실제 값으로 변경
  AND collected_at >= NOW() - INTERVAL '24 hours'
GROUP BY database_id, instance_id;
*/

-- 3. KPI 쿼리와 동일한 로직으로 집계 (실제 KPI 쿼리 테스트)
-- 아래 쿼리의 database_id와 instance_id를 실제 값으로 변경하여 실행
/*
SELECT
    database_id,
    instance_id,
    MAX(collected_at) as collected_at,
    AVG(avg_elapsed_seconds) as avg_elapsed_seconds,
    AVG(avg_blocked_seconds) as avg_blocked_seconds,
    AVG(avg_cost_delay_ms) as avg_cost_delay_ms,
    SUM(total_dead_tuples) as total_dead_tuples,
    AVG(worker_utilization_pct) as worker_utilization_pct,
    SUM(active_vacuum_sessions) as active_vacuum_sessions,
    SUM(blocked_vacuum_count) as blocked_vacuum_count,
    MAX(max_workers_configured) as max_workers_configured
FROM vacuum_metrics_agg_1m
WHERE database_id = 1  -- 실제 값으로 변경
  AND instance_id = 1   -- 실제 값으로 변경
  AND collected_at BETWEEN (NOW() - INTERVAL '24 hours') AND NOW()
GROUP BY database_id, instance_id;
*/

-- 4. 최근 집계 데이터 상세 (최근 10개)
SELECT 
    database_id,
    instance_id,
    collected_at,
    active_vacuum_sessions,
    blocked_vacuum_count,
    total_dead_tuples,
    avg_elapsed_seconds,
    max_workers_configured
FROM vacuum_metrics_agg_1m
WHERE collected_at >= NOW() - INTERVAL '24 hours'
ORDER BY collected_at DESC
LIMIT 10;

-- 5. max_workers_configured가 NULL인 경우 확인
SELECT 
    COUNT(*) as total_rows,
    COUNT(*) FILTER (WHERE max_workers_configured IS NULL) as null_max_workers,
    COUNT(*) FILTER (WHERE max_workers_configured = 0) as zero_max_workers,
    COUNT(*) FILTER (WHERE max_workers_configured > 0) as valid_max_workers
FROM vacuum_metrics_agg_1m
WHERE collected_at >= NOW() - INTERVAL '24 hours';

-- 6. active_vacuum_sessions가 0인 이유 확인
SELECT 
    COUNT(*) as total_rows,
    COUNT(*) FILTER (WHERE active_vacuum_sessions > 0) as rows_with_active_sessions,
    SUM(active_vacuum_sessions) as total_active_sessions,
    AVG(active_vacuum_sessions) as avg_active_sessions
FROM vacuum_metrics_agg_1m
WHERE collected_at >= NOW() - INTERVAL '24 hours';

