-- =====================================================
-- Vacuum 차단 데이터 확인 쿼리
-- =====================================================

-- 1. 최근 수집된 데이터에서 차단 정보 확인
SELECT 
    collected_at,
    table_name,
    blocker_pid,
    blocked_seconds,
    transaction_age,
    blocker_lock_mode,
    query_state,
    blocker_query,
    is_blocked
FROM vacuum_raw_metrics
WHERE collected_at >= NOW() - INTERVAL '1 hour'
  AND (blocker_pid IS NOT NULL 
       OR blocked_seconds IS NOT NULL 
       OR transaction_age IS NOT NULL
       OR is_blocked = true)
ORDER BY collected_at DESC, table_name
LIMIT 50;

-- 2. 차단 정보가 NULL인 경우 확인
SELECT 
    COUNT(*) AS total_count,
    COUNT(blocker_pid) AS blocker_pid_count,
    COUNT(blocked_seconds) AS blocked_seconds_count,
    COUNT(transaction_age) AS transaction_age_count,
    COUNT(CASE WHEN is_blocked = true THEN 1 END) AS is_blocked_true_count
FROM vacuum_raw_metrics
WHERE collected_at >= NOW() - INTERVAL '1 hour';

-- 3. 실제 PostgreSQL에서 현재 차단 상황 확인
SELECT 
    waiting_l.relation::regclass::text AS table_name,
    waiting_l.pid AS waiting_pid,
    waiting_act.state AS waiting_state,
    pg_blocking_pids(waiting_l.pid) AS blocking_pids,
    blocker_act.pid AS blocker_pid,
    blocker_act.xact_start AS blocker_xact_start,
    EXTRACT(EPOCH FROM (NOW() - blocker_act.xact_start))::BIGINT AS transaction_age_seconds,
    blocker_act.state AS blocker_state,
    blocker_act.query AS blocker_query
FROM pg_locks waiting_l
JOIN pg_stat_activity waiting_act ON waiting_l.pid = waiting_act.pid
LEFT JOIN LATERAL (
    SELECT unnest(pg_blocking_pids(waiting_l.pid)) AS blocker_pid
    WHERE pg_blocking_pids(waiting_l.pid) IS NOT NULL
      AND array_length(pg_blocking_pids(waiting_l.pid), 1) > 0
) blockers ON true
LEFT JOIN pg_stat_activity blocker_act ON blockers.blocker_pid = blocker_act.pid
WHERE waiting_l.granted = false
  AND waiting_l.relation IS NOT NULL
ORDER BY waiting_l.pid;

-- 4. 차단이 발생하지 않는 경우 확인
SELECT 
    '현재 차단 상황 없음' AS status,
    COUNT(*) AS waiting_locks_count
FROM pg_locks
WHERE granted = false
  AND relation IS NOT NULL;

