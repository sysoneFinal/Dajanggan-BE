package com.dajanggan.domain.engine.hottable.scheduler;

import com.dajanggan.domain.engine.hottable.domain.HotTableAgg5m;
import com.dajanggan.domain.engine.hottable.repository.HotTableMapper;
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
 * HotTable 5분 집계 스케줄러
 * 5분마다 실행하여 hot_table_agg 데이터를 5분 단위로 재집계
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotTableAgg5mScheduler {

    private final DataSource dataSource;
    private final HotTableMapper hotTableMapper;

    @PostConstruct
    public void init() {
        log.info("========== HotTableAgg5mScheduler 초기화 완료 ==========");
    }

    /**
     * 5분마다 실행 (0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55분)
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void aggregate5m() {
        log.info("========== HotTable 5분 집계 시작 ==========");

        try {
            OffsetDateTime collectedAt = OffsetDateTime.now(ZoneOffset.UTC)
                    .truncatedTo(ChronoUnit.MINUTES);
            OffsetDateTime startTime = collectedAt.minusMinutes(5);

            // 활성 데이터베이스 조회
            List<Database> databases = hotTableMapper.selectActiveDatabases();
            log.info("처리 대상 데이터베이스: {} 개", databases.size());

            int successCount = 0;
            int failCount = 0;

            for (Database database : databases) {
                try {
                    processDatabase5m(database, collectedAt, startTime);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("HotTable 5분 집계 실패: databaseId={}", database.getDatabaseId(), e);
                }
            }

            log.info("========== HotTable 5분 집계 완료: 성공={}, 실패={} ==========", 
                    successCount, failCount);

        } catch (Exception e) {
            log.error("HotTable 5분 집계 중 오류 발생", e);
        }
    }

    /**
     * 데이터베이스별 5분 집계 처리
     */
    private void processDatabase5m(Database database, OffsetDateTime collectedAt, OffsetDateTime startTime) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // hot_table_agg 테이블에서 5분간 데이터를 테이블별로 집계
        String sql = """
            SELECT
                ? as collected_at,
                ? as database_id,
                schema_name,
                table_name,
                ROUND(AVG(avg_table_size)::numeric, 0) as avg_table_size,
                SUM(total_seq_scan) as total_seq_scan,
                SUM(total_seq_tup_read) as total_seq_tup_read,
                SUM(total_idx_scan) as total_idx_scan,
                SUM(total_idx_tup_fetch) as total_idx_tup_fetch,
                SUM(total_tup_ins) as total_tup_ins,
                SUM(total_tup_upd) as total_tup_upd,
                SUM(total_tup_del) as total_tup_del,
                SUM(total_tup_hot_upd) as total_tup_hot_upd,
                ROUND(AVG(avg_dead_ratio)::numeric, 2) as avg_dead_ratio,
                ROUND(AVG(avg_cache_hit_ratio)::numeric, 2) as avg_cache_hit_ratio,
                ROUND(AVG(avg_seq_scan_ratio)::numeric, 2) as avg_seq_scan_ratio,
                ROUND(AVG(vacuum_delay_seconds)::numeric, 0) as avg_vacuum_delay_seconds,
                ROUND(AVG(avg_bloat_percent)::numeric, 2) as avg_bloat_percent
            FROM hot_table_agg_1m
            WHERE database_id = ?
              AND collected_at >= ?
              AND collected_at < ?
            GROUP BY schema_name, table_name
            """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                sql, collectedAt, database.getDatabaseId(), database.getDatabaseId(), startTime, collectedAt);

        if (results.isEmpty()) {
            log.debug("집계할 데이터 없음: databaseId={}, timeBucket={}", database.getDatabaseId(), collectedAt);
            return;
        }

        // 테이블별로 집계 데이터 저장
        for (Map<String, Object> aggData : results) {
            HotTableAgg5m agg5m = HotTableAgg5m.builder()
                    .databaseId(database.getDatabaseId())
                    .collectedAt(collectedAt)
                    .schemaName(getString(aggData, "schema_name"))
                    .tableName(getString(aggData, "table_name"))
                    .avgTableSize(getLong(aggData, "avg_table_size"))
                    .totalSeqScan(getLong(aggData, "total_seq_scan"))
                    .totalSeqTupRead(getLong(aggData, "total_seq_tup_read"))
                    .totalIdxScan(getLong(aggData, "total_idx_scan"))
                    .totalIdxTupFetch(getLong(aggData, "total_idx_tup_fetch"))
                    .totalTupIns(getLong(aggData, "total_tup_ins"))
                    .totalTupUpd(getLong(aggData, "total_tup_upd"))
                    .totalTupDel(getLong(aggData, "total_tup_del"))
                    .totalTupHotUpd(getLong(aggData, "total_tup_hot_upd"))
                    .avgDeadRatio(getDouble(aggData, "avg_dead_ratio"))
                    .avgCacheHitRatio(getDouble(aggData, "avg_cache_hit_ratio"))
                    .avgSeqScanRatio(getDouble(aggData, "avg_seq_scan_ratio"))
                    .vacuumDelaySeconds(getLong(aggData, "avg_vacuum_delay_seconds"))
                    .avgBloatPercent(getDouble(aggData, "avg_bloat_percent"))
                    .status(determineStatus(getDouble(aggData, "avg_dead_ratio")))
                    .build();

            hotTableMapper.insertAgg5m(agg5m);
            
            log.debug("HotTable 5분 집계 완료: databaseId={}, schema={}, table={}", 
                    database.getDatabaseId(), agg5m.getSchemaName(), agg5m.getTableName());
        }
    }

    private String determineStatus(Double avgDeadRatio) {
        if (avgDeadRatio == null) {
            return "정상";
        }
        if (avgDeadRatio > 30) {
            return "위험";
        } else if (avgDeadRatio > 15) {
            return "주의";
        }
        return "정상";
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

