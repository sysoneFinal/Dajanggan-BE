-- Vacuum 집계 데이터 확인 쿼리
-- 실제 데이터가 있는지, 집계가 제대로 되고 있는지 확인

-- 1. 최근 24시간 vacuum_raw_metrics 데이터 확인
SELECT 
    COUNT(*) as total_rows,
    COUNT(DISTINCT database_id) as database_count,
    COUNT(DISTINCT instance_id) as instance_count,
    MIN(collected_at) as earliest_collection,
    MAX(collected_at) as latest_collection,
    COUNT(*) FILTER (WHERE session_phase IS NOT NULL AND session_phase != 'not_running') as active_vacuum_count,
    COUNT(*) FILTER (WHERE blocker_pid IS NOT NULL) as blocked_count,
    SUM(n_dead_tup) as total_dead_tuples,
    AVG(elapsed_seconds) FILTER (WHERE elapsed_seconds IS NOT NULL AND elapsed_seconds > 0) as avg_elapsed_seconds
FROM vacuum_raw_metrics
WHERE collected_at >= NOW() - INTERVAL '24 hours';

-- 2. 최근 24시간 vacuum_metrics_agg_1m 데이터 확인
SELECT 
    COUNT(*) as agg_1m_rows,
    COUNT(DISTINCT database_id) as database_count,
    COUNT(DISTINCT instance_id) as instance_count,
    MIN(collected_at) as earliest_agg,
    MAX(collected_at) as latest_agg,
    SUM(active_vacuum_sessions) as total_active_sessions,
    SUM(blocked_vacuum_count) as total_blocked_count,
    SUM(total_dead_tuples) as total_dead_tuples,
    AVG(avg_elapsed_seconds) FILTER (WHERE avg_elapsed_seconds IS NOT NULL AND avg_elapsed_seconds > 0) as avg_elapsed_seconds,
    AVG(avg_blocked_seconds) FILTER (WHERE avg_blocked_seconds IS NOT NULL AND avg_blocked_seconds > 0) as avg_blocked_seconds
FROM vacuum_metrics_agg_1m
WHERE collected_at >= NOW() - INTERVAL '24 hours';

-- 3. 특정 database_id, instance_id의 최근 데이터 확인 (예시: database_id=1, instance_id=1)
-- 실제 사용하는 값으로 변경 필요
SELECT 
    database_id,
    instance_id,
    collected_at,
    active_vacuum_sessions,
    blocked_vacuum_count,
    total_dead_tuples,
    avg_elapsed_seconds,
    avg_blocked_seconds,
    max_workers_configured
FROM vacuum_metrics_agg_1m
WHERE database_id = 1  -- 실제 값으로 변경
  AND instance_id = 1   -- 실제 값으로 변경
  AND collected_at >= NOW() - INTERVAL '24 hours'
ORDER BY collected_at DESC
LIMIT 10;

-- 4. 집계 데이터가 없는 시간대 확인
-- 최근 24시간 동안 집계가 누락된 시간대 찾기
WITH time_slots AS (
    SELECT generate_series(
        DATE_TRUNC('minute', NOW() - INTERVAL '24 hours'),
        DATE_TRUNC('minute', NOW()),
        INTERVAL '1 minute'
    ) AS slot
),
existing_agg AS (
    SELECT DISTINCT DATE_TRUNC('minute', collected_at) AS collected_at
    FROM vacuum_metrics_agg_1m
    WHERE collected_at >= NOW() - INTERVAL '24 hours'
)
SELECT 
    ts.slot as missing_time,
    COUNT(ea.collected_at) as has_data
FROM time_slots ts
LEFT JOIN existing_agg ea ON ts.slot = ea.collected_at
WHERE ea.collected_at IS NULL
ORDER BY ts.slot DESC
LIMIT 20;

-- 5. 원시 데이터는 있지만 집계 데이터가 없는 경우 확인
SELECT 
    DATE_TRUNC('minute', vrm.collected_at) as minute_slot,
    COUNT(*) as raw_count,
    COUNT(DISTINCT vrm.database_id) as database_count,
    COUNT(DISTINCT vrm.instance_id) as instance_count
FROM vacuum_raw_metrics vrm
LEFT JOIN vacuum_metrics_agg_1m vma 
    ON DATE_TRUNC('minute', vrm.collected_at) = DATE_TRUNC('minute', vma.collected_at)
    AND vrm.database_id = vma.database_id
    AND vrm.instance_id = vma.instance_id
WHERE vrm.collected_at >= NOW() - INTERVAL '24 hours'
  AND vma.collected_at IS NULL
GROUP BY DATE_TRUNC('minute', vrm.collected_at)
ORDER BY minute_slot DESC
LIMIT 20;

