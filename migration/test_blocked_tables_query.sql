-- =====================================================
-- blocked_tables CTE 테스트 쿼리
-- =====================================================
-- 실제로 차단이 발생했는지 확인하는 테스트 쿼리

-- 1. 현재 차단 상황 확인
SELECT 
    waiting_l.relation::regclass::text AS table_name,
    waiting_l.pid AS waiting_pid,
    waiting_act.state AS waiting_state,
    waiting_act.query AS waiting_query,
    pg_blocking_pids(waiting_l.pid) AS blocking_pids,
    blocker_act.pid AS blocker_pid,
    blocker_act.xact_start AS blocker_xact_start,
    EXTRACT(EPOCH FROM (NOW() - blocker_act.xact_start))::BIGINT AS transaction_age,
    blocker_act.state AS blocker_state,
    blocker_act.query AS blocker_query
FROM pg_locks waiting_l
JOIN pg_stat_activity waiting_act ON waiting_l.pid = waiting_act.pid
LEFT JOIN LATERAL (
    SELECT unnest(pg_blocking_pids(waiting_l.pid)) AS blocker_pid
) blockers ON true
LEFT JOIN pg_stat_activity blocker_act ON blockers.blocker_pid = blocker_act.pid
WHERE waiting_l.granted = false
  AND waiting_l.relation IS NOT NULL
ORDER BY waiting_l.pid;

-- 2. blocked_tables CTE 테스트 (실제 쿼리와 동일)
WITH blocked_tables AS (
    SELECT DISTINCT
        waiting_l.relation::regclass::text AS table_name,
        blocker_act.pid AS blocker_pid,
        blocker_l.mode AS lock_mode,
        EXTRACT(EPOCH FROM (NOW() - blocker_act.xact_start))::INT AS blocked_seconds,
        EXTRACT(EPOCH FROM (NOW() - blocker_act.xact_start))::BIGINT AS transaction_age,
        blocker_act.state AS query_state,
        blocker_act.query AS blocker_query
    FROM pg_locks waiting_l
    JOIN pg_stat_activity waiting_act ON waiting_l.pid = waiting_act.pid
    CROSS JOIN LATERAL (
        SELECT unnest(pg_blocking_pids(waiting_l.pid)) AS blocker_pid
    ) blockers
    JOIN pg_stat_activity blocker_act ON blockers.blocker_pid = blocker_act.pid
    LEFT JOIN pg_locks blocker_l ON blocker_act.pid = blocker_l.pid
        AND blocker_l.relation = waiting_l.relation
        AND blocker_l.granted = true
    WHERE waiting_l.granted = false
      AND waiting_l.relation IS NOT NULL
      AND blocker_act.xact_start IS NOT NULL
      AND pg_blocking_pids(waiting_l.pid) IS NOT NULL
      AND array_length(pg_blocking_pids(waiting_l.pid), 1) > 0
)
SELECT * FROM blocked_tables;

-- 3. 차단이 없는 경우 확인
SELECT 
    COUNT(*) AS waiting_locks_count,
    COUNT(DISTINCT waiting_l.relation) AS blocked_tables_count
FROM pg_locks waiting_l
WHERE waiting_l.granted = false
  AND waiting_l.relation IS NOT NULL;

