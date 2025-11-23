-- ============================================================
-- 원시 데이터의 max_workers 값 확인
-- 집계가 0인 이유 파악
-- ============================================================

-- 1. 원시 데이터의 max_workers 분포 확인
SELECT 
    COUNT(*) as total_rows,
    COUNT(*) FILTER (WHERE max_workers IS NULL) as null_max_workers,
    COUNT(*) FILTER (WHERE max_workers = 0) as zero_max_workers,
    COUNT(*) FILTER (WHERE max_workers > 0) as valid_max_workers,
    MIN(max_workers) FILTER (WHERE max_workers > 0) as min_max_workers,
    MAX(max_workers) FILTER (WHERE max_workers > 0) as max_max_workers,
    AVG(max_workers) FILTER (WHERE max_workers > 0) as avg_max_workers
FROM vacuum_raw_metrics
WHERE collected_at >= NOW() - INTERVAL '24 hours';

-- 2. 최근 수집된 원시 데이터에서 max_workers 확인
SELECT 
    database_id,
    instance_id,
    collected_at,
    table_name,
    max_workers,
    session_phase,
    autovacuum_cost_delay_ms
FROM vacuum_raw_metrics
WHERE collected_at >= NOW() - INTERVAL '1 hour'
ORDER BY collected_at DESC, database_id, instance_id
LIMIT 50;

-- 3. database_id, instance_id별 max_workers 분포
SELECT 
    database_id,
    instance_id,
    COUNT(*) as row_count,
    COUNT(DISTINCT max_workers) as distinct_max_workers,
    MIN(max_workers) as min_max_workers,
    MAX(max_workers) as max_max_workers,
    COUNT(*) FILTER (WHERE max_workers = 0) as zero_count,
    COUNT(*) FILTER (WHERE max_workers > 0) as valid_count
FROM vacuum_raw_metrics
WHERE collected_at >= NOW() - INTERVAL '24 hours'
GROUP BY database_id, instance_id
ORDER BY database_id, instance_id;

-- 4. 실제 PostgreSQL 설정값 확인 (참고용)
-- 아래 쿼리는 모니터링 대상 PostgreSQL 인스턴스에 직접 연결하여 실행해야 함
/*
SELECT 
    name,
    setting,
    unit,
    context
FROM pg_settings
WHERE name = 'autovacuum_max_workers';
*/

