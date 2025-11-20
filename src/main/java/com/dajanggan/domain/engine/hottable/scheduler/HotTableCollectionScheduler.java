package com.dajanggan.domain.engine.hottable.scheduler;

import com.dajanggan.domain.common.util.MetricCollectionUtils;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
            OffsetDateTime collectedAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
            List<Database> databases = hotTableMapper.selectActiveDatabases();
            log.info("처리 대상 데이터베이스: {} 개", databases.size());
            
            if (databases.isEmpty()) {
                log.warn("활성 데이터베이스가 없습니다. DB의 database 테이블에 is_active=true인 데이터베이스가 있는지 확인하세요.");
                return;
            }

            int successCount = 0;
            int failCount = 0;

            for (Database database : databases) {
                try {
                    // 인스턴스가 없으면 건너뛰기 (예외를 던지지 않음)
                    Long instanceId = database.getInstanceId();
                    Instance instance = instanceRepository.findById(instanceId).orElse(null);
                    if (instance == null) {
                        log.warn("인스턴스를 찾을 수 없어 건너뜁니다: databaseId={}, databaseName={}, instanceId={}", 
                                database.getDatabaseId(), database.getDatabaseName(), instanceId);
                        continue; // 실패 카운트에 포함하지 않고 건너뛰기
                    }
                    
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
    private void processDatabaseMetrics(Database database, OffsetDateTime collectedAt) {
        Long instanceId = database.getInstanceId();
        Instance instance = instanceRepository.findById(instanceId)
                .orElse(null);
        
        if (instance == null) {
            log.warn("인스턴스를 찾을 수 없어 건너뜁니다: databaseId={}, databaseName={}, instanceId={}", 
                    database.getDatabaseId(), database.getDatabaseName(), instanceId);
            return; // 예외를 던지지 않고 건너뛰기
        }

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
                SELECT 1 FROM pg_class c2
                JOIN pg_namespace n2 ON c2.relnamespace = n2.oid
                WHERE n2.nspname = ps.schemaname
                  AND c2.relname = ps.relname
            )
            ORDER BY (ps.seq_scan + COALESCE(ps.idx_scan, 0)) DESC
            LIMIT 20
            """;

        return jdbcTemplate.queryForList(query);
    }

    /**
     * HotTableRaw 객체 생성
     */
    private HotTableRaw buildHotTableRaw(Long databaseId, OffsetDateTime collectedAt,
                                         Map<String, Object> data) {
        // Bloat 비율 계산 (n_dead_tup 기반 추정)
        // 정확한 계산은 pgstattuple 확장이 필요하지만, 여기서는 n_dead_tup을 기반으로 추정
        Double bloatPercent = calculateBloatPercent(
                MetricCollectionUtils.getLongValue(data, "n_live_tup"),
                MetricCollectionUtils.getLongValue(data, "n_dead_tup")
        );

        return HotTableRaw.builder()
                .databaseId(databaseId)
                .collectedAt(collectedAt)
                .schemaName((String) data.get("schemaname"))
                .tableName((String) data.get("tablename"))
                .tableSize(MetricCollectionUtils.getLongValue(data, "table_size"))
                .seqScan(MetricCollectionUtils.getLongValue(data, "seq_scan"))
                .seqTupRead(MetricCollectionUtils.getLongValue(data, "seq_tup_read"))
                .idxScan(MetricCollectionUtils.getLongValue(data, "idx_scan"))
                .idxTupFetch(MetricCollectionUtils.getLongValue(data, "idx_tup_fetch"))
                .nTupIns(MetricCollectionUtils.getLongValue(data, "n_tup_ins"))
                .nTupUpd(MetricCollectionUtils.getLongValue(data, "n_tup_upd"))
                .nTupDel(MetricCollectionUtils.getLongValue(data, "n_tup_del"))
                .nTupHotUpd(MetricCollectionUtils.getLongValue(data, "n_tup_hot_upd"))
                .nLiveTup(MetricCollectionUtils.getLongValue(data, "n_live_tup"))
                .nDeadTup(MetricCollectionUtils.getLongValue(data, "n_dead_tup"))
                .heapBlksRead(MetricCollectionUtils.getLongValue(data, "heap_blks_read"))
                .heapBlksHit(MetricCollectionUtils.getLongValue(data, "heap_blks_hit"))
                .lastVacuum(getOffsetDateTimeValue(data, "last_vacuum"))
                .lastAutovacuum(getOffsetDateTimeValue(data, "last_autovacuum"))
                .bloatPercent(bloatPercent)
                .build();
    }

    /**
     * Bloat 비율 계산 (n_dead_tup 기반 추정)
     * 정확한 계산은 pgstattuple 확장이 필요하지만, 여기서는 간단한 추정 사용
     */
    private Double calculateBloatPercent(Long nLiveTup, Long nDeadTup) {
        if (nLiveTup == null || nDeadTup == null) {
            return null;
        }
        
        long totalTup = nLiveTup + nDeadTup;
        if (totalTup == 0) {
            return 0.0;
        }
        
        // Dead 튜플 비율을 Bloat 비율로 사용 (간단한 추정)
        // 실제 Bloat는 더 복잡하지만, dead 튜플이 많을수록 bloat가 많다고 가정
        return (100.0 * nDeadTup) / totalTup;
    }

    /**
     * 증분 계산하여 Agg 데이터 생성
     */
    private HotTableAgg calculateAggregation(Long databaseId, OffsetDateTime collectedAt,
                                             HotTableRaw current, HotTableRaw previous,
                                             String schemaName, String tableName) {
        // 증분 계산 (stats_reset 대응을 위한 안전한 계산)
        long deltaSeqScan = MetricCollectionUtils.calculateSafeDelta(
                current.getSeqScan(), previous.getSeqScan());
        long deltaSeqTupRead = MetricCollectionUtils.calculateSafeDelta(
                current.getSeqTupRead(), previous.getSeqTupRead());
        long deltaIdxScan = MetricCollectionUtils.calculateSafeDelta(
                current.getIdxScan(), previous.getIdxScan());
        long deltaIdxTupFetch = MetricCollectionUtils.calculateSafeDelta(
                current.getIdxTupFetch(), previous.getIdxTupFetch());
        long deltaTupIns = MetricCollectionUtils.calculateSafeDelta(
                current.getNTupIns(), previous.getNTupIns());
        long deltaTupUpd = MetricCollectionUtils.calculateSafeDelta(
                current.getNTupUpd(), previous.getNTupUpd());
        long deltaTupDel = MetricCollectionUtils.calculateSafeDelta(
                current.getNTupDel(), previous.getNTupDel());
        long deltaTupHotUpd = MetricCollectionUtils.calculateSafeDelta(
                current.getNTupHotUpd(), previous.getNTupHotUpd());

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
            OffsetDateTime lastVacuumTime = current.getLastVacuum();
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

        // Bloat 비율은 Raw 데이터에서 가져옴
        Double avgBloatPercent = current.getBloatPercent();

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
                .avgBloatPercent(avgBloatPercent)
                .build();
    }

    /**
     * Map에서 OffsetDateTime 값 추출 헬퍼
     */
    private OffsetDateTime getOffsetDateTimeValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toInstant().atOffset(ZoneOffset.UTC);
        }
        return null;
    }
}
