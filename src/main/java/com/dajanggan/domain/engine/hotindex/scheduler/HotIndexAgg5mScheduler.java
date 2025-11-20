package com.dajanggan.domain.engine.hotindex.scheduler;

import com.dajanggan.domain.engine.hotindex.domain.HotIndexAgg5m;
import com.dajanggan.domain.engine.hotindex.repository.HotIndexMapper;
import com.dajanggan.domain.instance.domain.Database;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * HotIndex 5분 집계 스케줄러
 * 5분마다 실행하여 hot_index_agg 데이터를 5분 단위로 재집계
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotIndexAgg5mScheduler {

    private final DataSource dataSource;
    private final HotIndexMapper hotIndexMapper;

    @PostConstruct
    public void init() {
        log.info("========== HotIndexAgg5mScheduler 초기화 완료 ==========");
    }

    /**
     * 5분마다 실행 (0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55분)
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void aggregate5m() {
        log.info("========== HotIndex 5분 집계 시작 ==========");

        try {
            OffsetDateTime collectedAt = OffsetDateTime.now(ZoneOffset.UTC)
                    .truncatedTo(ChronoUnit.MINUTES);
            OffsetDateTime startTime = collectedAt.minusMinutes(5);

            // 활성 데이터베이스 조회
            List<Database> databases = hotIndexMapper.selectActiveDatabases();
            log.info("처리 대상 데이터베이스: {} 개", databases.size());

            int successCount = 0;
            int failCount = 0;

            for (Database database : databases) {
                try {
                    processDatabase5m(database, collectedAt, startTime);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("HotIndex 5분 집계 실패: databaseId={}", database.getDatabaseId(), e);
                }
            }

            log.info("========== HotIndex 5분 집계 완료: 성공={}, 실패={} ==========", 
                    successCount, failCount);

        } catch (Exception e) {
            log.error("HotIndex 5분 집계 중 오류 발생", e);
        }
    }

    /**
     * 데이터베이스별 5분 집계 처리
     */
    private void processDatabase5m(Database database, OffsetDateTime collectedAt, OffsetDateTime startTime) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // hot_index_agg 테이블에서 5분간 데이터를 인덱스별로 집계
        String sql = """
            SELECT
                ? as collected_at,
                ? as database_id,
                schema_name,
                table_name,
                index_name,
                ROUND(AVG(avg_index_size)::numeric, 0) as avg_index_size,
                SUM(total_idx_scan) as total_idx_scan,
                SUM(total_idx_tup_read) as total_idx_tup_read,
                SUM(total_idx_tup_fetch) as total_idx_tup_fetch,
                SUM(total_idx_blks_read) as total_idx_blks_read,
                SUM(total_idx_blks_hit) as total_idx_blks_hit,
                ROUND(AVG(avg_idx_efficiency)::numeric, 2) as avg_idx_efficiency,
                ROUND(AVG(avg_idx_hit_ratio)::numeric, 2) as avg_idx_hit_ratio,
                ROUND(AVG(avg_bloat_percent)::numeric, 2) as avg_bloat_percent,
                ROUND(AVG(avg_scan_time_ms)::numeric, 2) as avg_scan_time_ms,
                MAX(index_type) as index_type
            FROM hot_index_agg_1m
            WHERE database_id = ?
              AND collected_at >= ?
              AND collected_at < ?
            GROUP BY schema_name, table_name, index_name
            """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                sql, collectedAt, database.getDatabaseId(), database.getDatabaseId(), startTime, collectedAt);

        if (results.isEmpty()) {
            log.debug("집계할 데이터 없음: databaseId={}, timeBucket={}", database.getDatabaseId(), collectedAt);
            return;
        }

        // 인덱스별로 집계 데이터 저장
        for (Map<String, Object> aggData : results) {
            long totalIdxScan = getLong(aggData, "total_idx_scan");
            double avgIdxHitRatio = getDouble(aggData, "avg_idx_hit_ratio");
            double avgBloatPercent = getDouble(aggData, "avg_bloat_percent");
            
            // 일일 스캔 횟수 추정 (5분간 스캔 횟수 * 288 = 하루)
            long idxScanPerDay = totalIdxScan * 288;
            
            // 상태 판단
            String status = "정상";
            if (avgBloatPercent > 30 || avgIdxHitRatio < 80) {
                status = "위험";
            } else if (avgBloatPercent > 15 || avgIdxHitRatio < 90) {
                status = "주의";
            } else if (totalIdxScan == 0) {
                status = "미사용";
            }

            HotIndexAgg5m agg5m = HotIndexAgg5m.builder()
                    .databaseId(database.getDatabaseId())
                    .collectedAt(collectedAt)
                    .schemaName(getString(aggData, "schema_name"))
                    .tableName(getString(aggData, "table_name"))
                    .indexName(getString(aggData, "index_name"))
                    .avgIndexSize(getLong(aggData, "avg_index_size"))
                    .totalIdxScan(totalIdxScan)
                    .totalIdxTupRead(getLong(aggData, "total_idx_tup_read"))
                    .totalIdxTupFetch(getLong(aggData, "total_idx_tup_fetch"))
                    .totalIdxBlksRead(getLong(aggData, "total_idx_blks_read"))
                    .totalIdxBlksHit(getLong(aggData, "total_idx_blks_hit"))
                    .avgIdxEfficiency(getDouble(aggData, "avg_idx_efficiency"))
                    .avgIdxHitRatio(avgIdxHitRatio)
                    .idxScanPerDay(idxScanPerDay)
                    .status(status)
                    .indexType(getString(aggData, "index_type"))
                    .avgBloatPercent(avgBloatPercent)
                    .avgScanTimeMs(getDouble(aggData, "avg_scan_time_ms"))
                    .build();

            hotIndexMapper.insertAgg5m(agg5m);
            
            log.debug("HotIndex 5분 집계 완료: databaseId={}, schema={}, table={}, index={}", 
                    database.getDatabaseId(), agg5m.getSchemaName(), agg5m.getTableName(), 
                    agg5m.getIndexName());
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0.0;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0L;
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }
}

