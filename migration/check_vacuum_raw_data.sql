-- ============================================================
-- 원시 데이터 확인 - 왜 elapsed_seconds가 없는지 확인
-- ============================================================

-- 1. 최근 1시간 원시 데이터 요약
SELECT 
    COUNT(*) as total_rows,
    COUNT(*) FILTER (WHERE session_phase IS NOT NULL AND session_phase != 'not_running') as active_sessions,
    COUNT(*) FILTER (WHERE elapsed_seconds IS NOT NULL AND elapsed_seconds > 0) as rows_with_elapsed,
    COUNT(*) FILTER (WHERE elapsed_seconds IS NULL) as rows_null_elapsed,
    COUNT(*) FILTER (WHERE elapsed_seconds = 0) as rows_zero_elapsed,
    MIN(elapsed_seconds) FILTER (WHERE elapsed_seconds > 0) as min_elapsed,
    MAX(elapsed_seconds) FILTER (WHERE elapsed_seconds > 0) as max_elapsed,
    AVG(elapsed_seconds) FILTER (WHERE elapsed_seconds > 0) as avg_elapsed
FROM vacuum_raw_metrics
WHERE collected_at >= NOW() - INTERVAL '1 hour';

-- 2. session_phase별 분포
SELECT 
    session_phase,
    COUNT(*) as count,
    COUNT(*) FILTER (WHERE elapsed_seconds IS NOT NULL AND elapsed_seconds > 0) as with_elapsed,
    COUNT(*) FILTER (WHERE elapsed_seconds IS NULL) as null_elapsed,
    COUNT(*) FILTER (WHERE elapsed_seconds = 0) as zero_elapsed,
    AVG(elapsed_seconds) FILTER (WHERE elapsed_seconds > 0) as avg_elapsed
FROM vacuum_raw_metrics
WHERE collected_at >= NOW() - INTERVAL '1 hour'
GROUP BY session_phase
ORDER BY count DESC;

-- 3. 최근 수집된 원시 데이터 샘플 (상위 20개)
SELECT 
    collected_at,
    table_name,
    session_phase,
    elapsed_seconds,
    n_dead_tup,
    max_workers
FROM vacuum_raw_metrics
WHERE collected_at >= NOW() - INTERVAL '1 hour'
ORDER BY collected_at DESC
LIMIT 20;

-- 4. 실행 중인 세션이 있는데 elapsed_seconds가 없는 경우
SELECT 
    collected_at,
    table_name,
    session_phase,
    elapsed_seconds,
    n_dead_tup,
    max_workers
FROM vacuum_raw_metrics
WHERE collected_at >= NOW() - INTERVAL '1 hour'
  AND session_phase IS NOT NULL 
  AND session_phase != 'not_running'
  AND (elapsed_seconds IS NULL OR elapsed_seconds = 0)
ORDER BY collected_at DESC
LIMIT 20;

