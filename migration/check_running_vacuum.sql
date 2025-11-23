-- =====================================================
-- 실행 중인 Vacuum 세션 확인
-- =====================================================

-- 1. 현재 실행 중인 vacuum 세션 확인
SELECT 
    pv.pid AS vacuum_pid,
    pv.datname AS database_name,
    c.relname AS table_name,
    pv.phase AS session_phase,
    pv.heap_blks_total,
    pv.heap_blks_scanned,
    CASE 
        WHEN pv.heap_blks_total > 0 
        THEN (pv.heap_blks_scanned::NUMERIC / pv.heap_blks_total::NUMERIC * 100)
        ELSE 0 
    END AS progress_pct,
    act.xact_start AS transaction_start,
    EXTRACT(EPOCH FROM (NOW() - act.xact_start))::BIGINT AS transaction_age_seconds,
    EXTRACT(EPOCH FROM (NOW() - act.query_start))::BIGINT AS query_age_seconds,
    act.state AS query_state,
    CASE WHEN act.query LIKE 'autovacuum:%' THEN true ELSE false END AS is_autovacuum
FROM pg_stat_progress_vacuum pv
JOIN pg_stat_activity act ON pv.pid = act.pid
JOIN pg_class c ON pv.relid = c.oid
ORDER BY act.xact_start;

-- 2. 실행 중인 vacuum 세션 개수
SELECT 
    COUNT(*) AS running_vacuum_count
FROM pg_stat_progress_vacuum;

-- 3. 실행 중인 vacuum이 있는 테이블 목록
SELECT DISTINCT
    c.relname AS table_name,
    COUNT(*) AS vacuum_count
FROM pg_stat_progress_vacuum pv
JOIN pg_class c ON pv.relid = c.oid
GROUP BY c.relname
ORDER BY vacuum_count DESC;

-- 4. 최근 수집된 데이터에서 실행 중인 vacuum 확인
SELECT 
    collected_at,
    table_name,
    session_phase,
    elapsed_seconds,
    transaction_age,
    is_blocked,
    blocker_pid
FROM vacuum_raw_metrics
WHERE collected_at >= NOW() - INTERVAL '1 hour'
  AND session_phase != 'not_running'
ORDER BY collected_at DESC, table_name
LIMIT 50;

