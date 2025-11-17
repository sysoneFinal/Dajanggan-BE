package com.dajanggan.domain.engine.hotindex.scheduler;

import com.dajanggan.domain.engine.hotindex.domain.HotIndexAgg;
import com.dajanggan.domain.engine.hotindex.domain.HotIndexRaw;
import com.dajanggan.domain.engine.hotindex.repository.HotIndexMapper;
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
 * HotIndex 메트릭 수집 스케줄러
 * 1분마다 실행:
 * 1. pg_stat_user_indexes에서 상위 20개 인덱스 수집
 * 2. Raw 데이터 20개 저장
 * 3. 이전 데이터와 비교하여 증분 계산 후 Agg 20개 저장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotIndexCollectionScheduler {

    private final HotIndexMapper hotIndexMapper;
    private final InstanceRepository instanceRepository;
    private final DataSourceFactory dataSourceFactory;

    @PostConstruct
    public void init() {
        log.info("========== HotIndexCollectionScheduler 초기화 완료 ==========");
    }

    /**
     * 1분마다 실행 (매분 0초)
     */
    @Scheduled(cron = "0 * * * * *")
    public void collectHotIndexMetrics() {
        log.info("========== HotIndex 메트릭 수집 시작 ==========");

        try {
            LocalDateTime collectedAt = LocalDateTime.now();
            List<Database> databases = hotIndexMapper.selectActiveDatabases();
            log.info("처리 대상 데이터베이스: {} 개", databases.size());

            int successCount = 0;
            int failCount = 0;

            for (Database database : databases) {
                try {
                    processDatabaseMetrics(database, collectedAt);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("HotIndex 메트릭 처리 실패: databaseId={}", database.getDatabaseId(), e);
                }
            }

            log.info("========== HotIndex 메트릭 수집 완료: 성공={}, 실패={} ==========", successCount, failCount);

        } catch (Exception e) {
            log.error("HotIndex 메트릭 수집 중 오류 발생", e);
        }
    }

    /**
     * 특정 데이터베이스의 메트릭 처리
     */
    private void processDatabaseMetrics(Database database, LocalDateTime collectedAt) {
        Instance instance = instanceRepository.findById(database.getInstanceId())
                .orElseThrow(() -> new RuntimeException("인스턴스를 찾을 수 없습니다: " + database.getInstanceId()));

        JdbcTemplate jdbcTemplate = dataSourceFactory.createJdbcTemplate(instance, database.getDatabaseName());
        List<Map<String, Object>> topIndexes = collectTopIndexesFromPgStat(jdbcTemplate);

        if (topIndexes.isEmpty()) {
            return;
        }

        List<HotIndexRaw> rawList = new ArrayList<>();
        List<HotIndexAgg> aggList = new ArrayList<>();

        for (Map<String, Object> indexData : topIndexes) {
            String schemaName = (String) indexData.get("schemaname");
            String tableName = (String) indexData.get("tablename");
            String indexName = (String) indexData.get("indexname");

            HotIndexRaw previousRaw = hotIndexMapper.selectPreviousRawByIndex(
                    database.getDatabaseId(), schemaName, tableName, indexName);

            HotIndexRaw raw = buildHotIndexRaw(database.getDatabaseId(), collectedAt, indexData);
            rawList.add(raw);

            if (previousRaw != null) {
                HotIndexAgg agg = calculateAggregation(database.getDatabaseId(), collectedAt, 
                        raw, previousRaw, schemaName, tableName, indexName);
                aggList.add(agg);
            }
        }

        if (!rawList.isEmpty()) {
            hotIndexMapper.insertRawBatch(rawList);
            log.debug("Raw 데이터 일괄 저장 완료: databaseId={}, count={}", database.getDatabaseId(), rawList.size());
        }

        if (!aggList.isEmpty()) {
            hotIndexMapper.insertAggBatch(aggList);
            log.debug("Agg 데이터 일괄 저장 완료: databaseId={}, count={}", database.getDatabaseId(), aggList.size());
        }

        log.info("메트릭 처리 완료: databaseId={}, databaseName={}, indexes={}", 
                database.getDatabaseId(), database.getDatabaseName(), topIndexes.size());
    }

    /**
     * pg_stat_user_indexes에서 상위 20개 인덱스 수집
     */
    private List<Map<String, Object>> collectTopIndexesFromPgStat(JdbcTemplate jdbcTemplate) {
        String query = """
        SELECT 
            s.schemaname,
            s.relname as tablename,
            s.indexrelname as indexname,
            pg_relation_size(s.indexrelid) as index_size,
            s.idx_scan,
            s.idx_tup_read,
            s.idx_tup_fetch,
            t.idx_blks_read,
            t.idx_blks_hit,
            am.amname as index_type
        FROM pg_stat_user_indexes s
        LEFT JOIN pg_statio_user_indexes t 
            ON s.indexrelid = t.indexrelid
        LEFT JOIN pg_index i 
            ON s.indexrelid = i.indexrelid
        LEFT JOIN pg_class c 
            ON i.indexrelid = c.oid
        LEFT JOIN pg_am am 
            ON c.relam = am.oid
        ORDER BY s.idx_scan DESC
        LIMIT 20
        """;

        return jdbcTemplate.queryForList(query);
    }


    /**
     * HotIndexRaw 객체 생성
     */
    private HotIndexRaw buildHotIndexRaw(Long databaseId, LocalDateTime collectedAt,
                                         Map<String, Object> data) {
        return HotIndexRaw.builder()
                .databaseId(databaseId)
                .collectedAt(collectedAt)
                .schemaName((String) data.get("schemaname"))
                .tableName((String) data.get("tablename"))
                .indexName((String) data.get("indexname"))
                .indexSize(getLongValue(data, "index_size"))
                .idxScan(getLongValue(data, "idx_scan"))
                .idxTupRead(getLongValue(data, "idx_tup_read"))
                .idxTupFetch(getLongValue(data, "idx_tup_fetch"))
                .idxBlksRead(getLongValue(data, "idx_blks_read"))
                .idxBlksHit(getLongValue(data, "idx_blks_hit"))
//                .lastIdxScan(getLocalDateTimeValue(data, "last_idx_scan"))
                .indexType((String) data.get("index_type"))
                .bloatPercent(0.0)  // Bloat 계산은 별도 쿼리 필요
                .avgScanTimeMs(0.0)  // 스캔 시간 계산은 별도 로직 필요
                .build();
    }

    /**
     * 증분 계산하여 Agg 데이터 생성
     */
    private HotIndexAgg calculateAggregation(Long databaseId, LocalDateTime collectedAt,
                                             HotIndexRaw current, HotIndexRaw previous,
                                             String schemaName, String tableName, String indexName) {
        // 증분 계산
        long deltaIdxScan = current.getIdxScan() - previous.getIdxScan();
        long deltaIdxTupRead = current.getIdxTupRead() - previous.getIdxTupRead();
        long deltaIdxTupFetch = current.getIdxTupFetch() - previous.getIdxTupFetch();
        long deltaIdxBlksRead = current.getIdxBlksRead() - previous.getIdxBlksRead();
        long deltaIdxBlksHit = current.getIdxBlksHit() - previous.getIdxBlksHit();

        // 인덱스 캐시 히트율 계산
        long totalBlks = deltaIdxBlksRead + deltaIdxBlksHit;
        double hitRatio = 0.0;
        if (totalBlks > 0) {
            hitRatio = (100.0 * deltaIdxBlksHit) / totalBlks;
        }

        // 인덱스 효율성 계산 (Fetch/Read 비율)
        double efficiency = 0.0;
        if (deltaIdxTupRead > 0) {
            efficiency = (100.0 * deltaIdxTupFetch) / deltaIdxTupRead;
        }

        // 일일 스캔 횟수 계산 (1분 데이터를 1440분으로 환산)
        long scanPerDay = deltaIdxScan * 1440;

        // 상태 판단
        String status = "정상";
        if (deltaIdxScan == 0) {
            status = "미사용";
        } else if (current.getBloatPercent() != null && current.getBloatPercent() >= 30) {
            status = "bloat";
        } else if (hitRatio < 85 || efficiency < 50) {
            status = "비효율";
        }

        return HotIndexAgg.builder()
                .databaseId(databaseId)
                .collectedAt(collectedAt)
                .schemaName(schemaName)
                .tableName(tableName)
                .indexName(indexName)
                .avgIndexSize(current.getIndexSize())
                .totalIdxScan(deltaIdxScan)
                .totalIdxTupRead(deltaIdxTupRead)
                .totalIdxTupFetch(deltaIdxTupFetch)
                .totalIdxBlksRead(deltaIdxBlksRead)
                .totalIdxBlksHit(deltaIdxBlksHit)
                .avgIdxEfficiency(efficiency)
                .avgIdxHitRatio(hitRatio)
                .idxScanPerDay(scanPerDay)
                .status(status)
                .indexType(current.getIndexType())
                .avgBloatPercent(current.getBloatPercent() != null ? current.getBloatPercent() : 0.0)
                .avgScanTimeMs(current.getAvgScanTimeMs() != null ? current.getAvgScanTimeMs() : 0.0)
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
