-- Index Bloat 컬럼 추가 마이그레이션
-- vacuum_metrics_agg_1m 테이블에 total_index_bloat_bytes 컬럼 추가

ALTER TABLE vacuum_metrics_agg_1m 
ADD COLUMN IF NOT EXISTS total_index_bloat_bytes BIGINT DEFAULT 0;

-- vacuum_metrics_agg_5m 테이블에 total_index_bloat_bytes 컬럼 추가

ALTER TABLE vacuum_metrics_agg_5m 
ADD COLUMN IF NOT EXISTS total_index_bloat_bytes BIGINT DEFAULT 0;

-- 기존 데이터 업데이트 (필요시)
-- UPDATE vacuum_metrics_agg_1m SET total_index_bloat_bytes = 0 WHERE total_index_bloat_bytes IS NULL;
-- UPDATE vacuum_metrics_agg_5m SET total_index_bloat_bytes = 0 WHERE total_index_bloat_bytes IS NULL;

