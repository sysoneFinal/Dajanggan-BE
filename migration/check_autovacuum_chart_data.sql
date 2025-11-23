-- ============================================================
-- Autovacuum 차트 데이터 확인
-- avg_cost_delay_ms와 active_vacuum_sessions 확인
-- ============================================================

-- 1. 5분 집계 데이터의 autovacuum 관련 필드 확인
SELECT 
    collected_at,
    avg_cost_delay_ms,
    active_vacuum_sessions,
    autovacuum_sessions,
    max_workers_configured
FROM vacuum_metrics_agg_5m
WHERE collected_at >= NOW() - INTERVAL '24 hours'
ORDER BY collected_at DESC
LIMIT 20;

-- 2. avg_cost_delay_ms가 NULL인 경우 확인
SELECT 
    COUNT(*) as total_rows,
    COUNT(*) FILTER (WHERE avg_cost_delay_ms IS NULL) as null_cost_delay,
    COUNT(*) FILTER (WHERE avg_cost_delay_ms > 0) as valid_cost_delay,
    AVG(avg_cost_delay_ms) FILTER (WHERE avg_cost_delay_ms > 0) as avg_cost_delay,
    MIN(avg_cost_delay_ms) FILTER (WHERE avg_cost_delay_ms > 0) as min_cost_delay,
    MAX(avg_cost_delay_ms) FILTER (WHERE avg_cost_delay_ms > 0) as max_cost_delay
FROM vacuum_metrics_agg_5m
WHERE collected_at >= NOW() - INTERVAL '24 hours';

-- 3. 원시 데이터의 autovacuum_cost_delay_ms 확인
SELECT 
    collected_at,
    autovacuum_cost_delay_ms,
    max_workers,
    COUNT(*) as row_count
FROM vacuum_raw_metrics
WHERE collected_at >= NOW() - INTERVAL '1 hour'
GROUP BY collected_at, autovacuum_cost_delay_ms, max_workers
ORDER BY collected_at DESC
LIMIT 20;

-- 4. 원시 데이터의 autovacuum_cost_delay_ms 분포
SELECT 
    COUNT(*) as total_rows,
    COUNT(*) FILTER (WHERE autovacuum_cost_delay_ms IS NULL) as null_cost_delay,
    COUNT(*) FILTER (WHERE autovacuum_cost_delay_ms = 0) as zero_cost_delay,
    COUNT(*) FILTER (WHERE autovacuum_cost_delay_ms > 0) as valid_cost_delay,
    AVG(autovacuum_cost_delay_ms) FILTER (WHERE autovacuum_cost_delay_ms > 0) as avg_cost_delay,
    MIN(autovacuum_cost_delay_ms) FILTER (WHERE autovacuum_cost_delay_ms > 0) as min_cost_delay,
    MAX(autovacuum_cost_delay_ms) FILTER (WHERE autovacuum_cost_delay_ms > 0) as max_cost_delay
FROM vacuum_raw_metrics
WHERE collected_at >= NOW() - INTERVAL '1 hour';

