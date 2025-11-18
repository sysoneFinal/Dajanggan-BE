package com.dajanggan.domain.engine.hottable.scheduler;

import com.dajanggan.domain.engine.hottable.domain.HotTableAgg;
import com.dajanggan.domain.engine.hottable.domain.HotTableRaw;
import com.dajanggan.domain.engine.hottable.repository.HotTableMapper;
import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.infrastructure.datasource.DataSourceFactory;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HotTable 메트릭 수집 스케줄러
 * 1분마다 실행:
 * 1. pg_stat_user_tables에서 상위 20개 테이블 수집
 * 2. Raw 데이터 20개 저장
 * 3. 이전 데이터와 비교하여 증분 계산 후 Agg 20개 저장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotTableCollectionScheduler {

    private final HotTableMapper hotTableMapper;
    private final InstanceRepository instanceRepository;
    private final DataSourceFactory dataSourceFactory;

    @PostConstruct
    public void init() {
        log.info("========== HotTableCollectionScheduler 초기화 완료 ==========");
    }

    /**
     * 1분마다 실행 (매분 0초)
     */
    @Scheduled(cron = "0 * * * * *")
    public void collectHotTableMetrics() {
        log.info("========== HotTable 메트릭 수집 시작 ==========");

        try {
            LocalDateTime collectedAt = LocalDateTime.now();
            List<Database> databases = hotTableMapper.selectActiveDatabases();
            log.info("처리 대상 데이터베이스: {} 개", databases.size());

            int successCount = 0;
            int failCount = 0;

            for (Database database : databases) {
                try {
                    processDatabaseMetrics(database, collectedAt);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("HotTable 메트릭 처리 실패: databaseId={}", database.getDatabaseId(), e);
                }
            }

            log.info("========== HotTable 메트릭 수집 완료: 성공={}, 실패={} ==========", successCount, failCount);

        } catch (Exception e) {
            log.error("HotTable 메트릭 수집 중 오류 발생", e);
        }
    }

    /**
     * 특정 데이터베이스의 메트릭 처리
     */
    private void processDatabaseMetrics(Database database, LocalDateTime collectedAt) {
        Instance instance = instanceRepository.findById(database.getInstanceId())
                .orElseThrow(() -> new RuntimeException("인스턴스를 찾을 수 없습니다: " + database.getInstanceId()));

        JdbcTemplate jdbcTemplate = dataSourceFactory.createJdbcTemplate(instance, database.getDatabaseName());
        List<Map<String, Object>> topTables = collectTopTablesFromPgStat(jdbcTemplate);

        if (topTables.isEmpty()) {
            return;
        }

        List<HotTableRaw> rawList = new ArrayList<>();
        List<HotTableAgg> aggList = new ArrayList<>();

        for (Map<String, Object> tableData : topTables) {
            String schemaName = (String) tableData.get("schemaname");
            String tableName = (String) tableData.get("tablename");

            HotTableRaw previousRaw = hotTableMapper.selectPreviousRawByTable(
                    database.getDatabaseId(), schemaName, tableName);

            HotTableRaw raw = buildHotTableRaw(database.getDatabaseId(), collectedAt, tableData);
            rawList.add(raw);

            if (previousRaw != null) {
                HotTableAgg agg = calculateAggregation(database.getDatabaseId(), collectedAt, 
                        raw, previousRaw, schemaName, tableName);
                aggList.add(agg);
            }
        }

        if (!rawList.isEmpty()) {
            hotTableMapper.insertRawBatch(rawList);
            log.debug("Raw 데이터 일괄 저장 완료");
        }

        if (!aggList.isEmpty()) {
            hotTableMapper.insertAggBatch(aggList);
            log.debug("Agg 데이터 일괄 저장 완료");
        }

        log.info("메트릭 처리 완료");
    }

    /**
     * pg_stat_user_tables에서 상위 20개 테이블 수집
     */
    private List<Map<String, Object>> collectTopTablesFromPgStat(JdbcTemplate jdbcTemplate) {
        String query = """
            SELECT 
                ps.schemaname,
                ps.relname as tablename,
                COALESCE(
                    (SELECT pg_total_relation_size(c.oid)
                     FROM pg_class c
                     JOIN pg_namespace n ON c.relnamespace = n.oid
                     WHERE n.nspname = ps.schemaname
                       AND c.relname = ps.relname
                     LIMIT 1),
                    0
                ) as table_size,
                ps.seq_scan,
                ps.seq_tup_read,
                ps.idx_scan,
                ps.idx_tup_fetch,
                ps.n_tup_ins,
                ps.n_tup_upd,
                ps.n_tup_del,
                ps.n_tup_hot_upd,
                ps.n_live_tup,
                ps.n_dead_tup,
                sio.heap_blks_read,
                sio.heap_blks_hit,
                ps.last_vacuum,
                ps.last_autovacuum
            FROM pg_stat_user_tables ps
            LEFT JOIN pg_statio_user_tables sio ON ps.relid = sio.relid
            WHERE EXISTS (
                SELECT 1 FROM pg_class c
                JOIN pg_namespace n ON c.relnamespace = n.oid
                WHERE n.nspname = ps.schemaname
                  AND c.relname = ps.relname
            )
            ORDER BY (ps.seq_scan + COALESCE(ps.idx_scan, 0)) DESC
            LIMIT 20
            """;

        return jdbcTemplate.queryForList(query);
    }

    /**
     * HotTableRaw 객체 생성
     */
    private HotTableRaw buildHotTableRaw(Long databaseId, LocalDateTime collectedAt,
                                         Map<String, Object> data) {
        return HotTableRaw.builder()
                .databaseId(databaseId)
                .collectedAt(collectedAt)
                .schemaName((String) data.get("schemaname"))
                .tableName((String) data.get("tablename"))
                .tableSize(getLongValue(data, "table_size"))
                .seqScan(getLongValue(data, "seq_scan"))
                .seqTupRead(getLongValue(data, "seq_tup_read"))
                .idxScan(getLongValue(data, "idx_scan"))
                .idxTupFetch(getLongValue(data, "idx_tup_fetch"))
                .nTupIns(getLongValue(data, "n_tup_ins"))
                .nTupUpd(getLongValue(data, "n_tup_upd"))
                .nTupDel(getLongValue(data, "n_tup_del"))
                .nTupHotUpd(getLongValue(data, "n_tup_hot_upd"))
                .nLiveTup(getLongValue(data, "n_live_tup"))
                .nDeadTup(getLongValue(data, "n_dead_tup"))
                .heapBlksRead(getLongValue(data, "heap_blks_read"))
                .heapBlksHit(getLongValue(data, "heap_blks_hit"))
                .lastVacuum(getLocalDateTimeValue(data, "last_vacuum"))
                .lastAutovacuum(getLocalDateTimeValue(data, "last_autovacuum"))
                .bloatPercent(0.0)  // Bloat 계산은 별도 쿼리 필요
                .build();
    }

    /**
     * 증분 계산하여 Agg 데이터 생성
     */
    private HotTableAgg calculateAggregation(Long databaseId, LocalDateTime collectedAt,
                                             HotTableRaw current, HotTableRaw previous,
                                             String schemaName, String tableName) {
        // 증분 계산
        long deltaSeqScan = current.getSeqScan() - previous.getSeqScan();
        long deltaSeqTupRead = current.getSeqTupRead() - previous.getSeqTupRead();
        long deltaIdxScan = current.getIdxScan() - previous.getIdxScan();
        long deltaIdxTupFetch = current.getIdxTupFetch() - previous.getIdxTupFetch();
        long deltaTupIns = current.getNTupIns() - previous.getNTupIns();
        long deltaTupUpd = current.getNTupUpd() - previous.getNTupUpd();
        long deltaTupDel = current.getNTupDel() - previous.getNTupDel();
        long deltaTupHotUpd = current.getNTupHotUpd() - previous.getNTupHotUpd();

        // Dead 튜플 비율 계산
        double deadRatio = 0.0;
        if (current.getNLiveTup() > 0) {
            deadRatio = (100.0 * current.getNDeadTup()) / (current.getNLiveTup() + current.getNDeadTup());
        }

        // 캐시 히트율 계산
        long totalBlks = current.getHeapBlksRead() + current.getHeapBlksHit();
        double cacheHitRatio = 0.0;
        if (totalBlks > 0) {
            cacheHitRatio = (100.0 * current.getHeapBlksHit()) / totalBlks;
        }

        // Sequential Scan 비율 계산
        long totalScans = deltaSeqScan + deltaIdxScan;
        double seqScanRatio = 0.0;
        if (totalScans > 0) {
            seqScanRatio = (100.0 * deltaSeqScan) / totalScans;
        }

        // VACUUM 지연 시간 계산 (초)
        long vacuumDelay = 0L;
        if (current.getLastVacuum() != null || current.getLastAutovacuum() != null) {
            LocalDateTime lastVacuumTime = current.getLastVacuum();
            if (lastVacuumTime == null || 
                (current.getLastAutovacuum() != null && current.getLastAutovacuum().isAfter(lastVacuumTime))) {
                lastVacuumTime = current.getLastAutovacuum();
            }
            if (lastVacuumTime != null) {
                vacuumDelay = java.time.Duration.between(lastVacuumTime, collectedAt).getSeconds();
            }
        }

        // 상태 판단 (Dead Ratio 기준)
        String status = "정상";
        if (deadRatio > 30) {
            status = "위험";
        } else if (deadRatio > 15) {
            status = "주의";
        }

        return HotTableAgg.builder()
                .databaseId(databaseId)
                .collectedAt(collectedAt)
                .schemaName(schemaName)
                .tableName(tableName)
                .avgTableSize(current.getTableSize())
                .totalSeqScan(deltaSeqScan)
                .totalSeqTupRead(deltaSeqTupRead)
                .totalIdxScan(deltaIdxScan)
                .totalIdxTupFetch(deltaIdxTupFetch)
                .totalTupIns(deltaTupIns)
                .totalTupUpd(deltaTupUpd)
                .totalTupDel(deltaTupDel)
                .totalTupHotUpd(deltaTupHotUpd)
                .avgDeadRatio(deadRatio)
                .avgCacheHitRatio(cacheHitRatio)
                .avgSeqScanRatio(seqScanRatio)
                .vacuumDelaySeconds(vacuumDelay)
                .status(status)
                .avgBloatPercent(0.0)  // Bloat 계산은 별도 쿼리 필요
                .build();
    }

    /**
     * Map에서 Long 값 추출 헬퍼
     */
    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    /**
     * Map에서 LocalDateTime 값 추출 헬퍼
     */
    private LocalDateTime getLocalDateTimeValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime();
        }
        return null;
    }
}
