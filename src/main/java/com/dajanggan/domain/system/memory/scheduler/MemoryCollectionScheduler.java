package com.dajanggan.domain.system.memory.scheduler;

import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.domain.system.memory.domain.MemoryAgg;
import com.dajanggan.domain.system.memory.domain.MemoryRaw;
import com.dajanggan.domain.system.memory.repository.MemoryMapper;
import com.dajanggan.infrastructure.datasource.DataSourceFactory;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Memory 메트릭 수집 스케줄러 (pg_buffercache + pg_statio 기반)
 * 1분마다 실행:
 * 1. pg_buffercache에서 버퍼 캐시 통계 수집
 * 2. pg_statio_user_tables/indexes에서 I/O 통계 수집
 * 3. Raw 데이터 저장
 * 4. 이전 Raw와 비교하여 증분 계산 후 Agg 저장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryCollectionScheduler {

    private final MemoryMapper memoryMapper;
    private final InstanceRepository instanceRepository;
    private final DataSourceFactory dataSourceFactory;

    @PostConstruct
    public void init() {
        log.info("========== MemoryCollectionScheduler 초기화 완료 ==========");
    }

    /**
     * 1분마다 실행 (매분 0초)
     */
    @Scheduled(cron = "0 * * * * *")
    public void collectMemoryMetrics() {
        log.info("========== Memory 메트릭 수집 시작 (pg_buffercache) ==========");

        try {
            OffsetDateTime collectedAt = OffsetDateTime.now(ZoneOffset.UTC)
                    .truncatedTo(ChronoUnit.MINUTES);
            
            List<Long> instanceIds = memoryMapper.selectActiveInstanceIds();
            log.info("처리 대상 인스턴스: {} 개", instanceIds.size());

            int successCount = 0;
            int failCount = 0;

            for (Long instanceId : instanceIds) {
                try {
                    processInstanceMetrics(instanceId, collectedAt);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("Memory 메트릭 처리 실패: instanceId={}", instanceId, e);
                }
            }

            log.info("========== Memory 메트릭 수집 완료: 성공={}, 실패={} ==========", 
                    successCount, failCount);

        } catch (Exception e) {
            log.error("Memory 메트릭 수집 중 오류 발생", e);
        }
    }

    /**
     * 특정 인스턴스의 메트릭 처리
     */
    private void processInstanceMetrics(Long instanceId, OffsetDateTime collectedAt) {
        // 1. Instance 정보 조회
        Instance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("인스턴스를 찾을 수 없습니다: " + instanceId));

        // 2. JdbcTemplate 생성
        JdbcTemplate jdbcTemplate = dataSourceFactory.createJdbcTemplate(instance, "postgres");

        // 3. 데이터 수집
        List<Map<String, Object>> bufferCacheData;
        try {
            bufferCacheData = collectFromPgBuffercache(jdbcTemplate);
        } catch (Exception e) {
            log.warn("pg_buffercache 데이터 수집 실패 (확장 미설치 가능성): instanceId={}, error={}", 
                    instanceId, e.getMessage());
            bufferCacheData = new ArrayList<>(); // 빈 리스트로 처리
        }
        
        List<Map<String, Object>> statioData;
        try {
            statioData = collectFromPgStatio(jdbcTemplate);
        } catch (Exception e) {
            log.warn("pg_statio 데이터 수집 실패: instanceId={}, error={}", instanceId, e.getMessage());
            statioData = new ArrayList<>();
        }
        
        Map<String, Object> databaseData;
        try {
            databaseData = collectFromPgStatDatabase(jdbcTemplate);
        } catch (Exception e) {
            log.warn("pg_stat_database 데이터 수집 실패: instanceId={}, error={}", instanceId, e.getMessage());
            databaseData = new HashMap<>();
        }

        // 4. 데이터 병합 (relname 기준)
        Map<String, Map<String, Object>> mergedData = mergeData(bufferCacheData, statioData);
        
        if (mergedData.isEmpty()) {
            log.warn("수집된 데이터가 없습니다: instanceId={}, bufferCache={}, statio={}", 
                    instanceId, bufferCacheData.size(), statioData.size());
            return; // 데이터가 없으면 처리 중단
        }

        // 5. 이전 Raw 데이터 조회
        List<MemoryRaw> previousRawList = memoryMapper.selectPreviousRawByRelname(instanceId);
        Map<String, MemoryRaw> previousRawMap = new HashMap<>();
        for (MemoryRaw prev : previousRawList) {
            String key = prev.getRelname() != null ? prev.getRelname() : "";
            previousRawMap.put(key, prev);
        }

        // 6. Raw 데이터 생성 및 저장
        List<MemoryRaw> rawList = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : mergedData.entrySet()) {
            MemoryRaw raw = buildMemoryRaw(instanceId, collectedAt, entry.getValue(), databaseData);
            rawList.add(raw);
        }

        if (!rawList.isEmpty()) {
            memoryMapper.insertRawBatch(rawList);
        }

        // 7. Agg 데이터 생성 및 저장 (증분 계산)
        List<MemoryAgg> aggList = new ArrayList<>();
        Long totalBuffers = calculateTotalBuffers(rawList);
        
        for (MemoryRaw currentRaw : rawList) {
            String key = currentRaw.getRelname() != null ? currentRaw.getRelname() : "";
            MemoryRaw previousRaw = previousRawMap.get(key);
            
            MemoryAgg agg = calculateAggregation(currentRaw, previousRaw, collectedAt, totalBuffers);
            aggList.add(agg);
        }

        if (!aggList.isEmpty()) {
            memoryMapper.insertAggBatch(aggList);
        }

        log.info("메트릭 처리 완료: instanceId={}, raw={}, agg={}, tempFiles={}", 
                instanceId, rawList.size(), aggList.size(), 
                aggList.stream().findFirst().map(MemoryAgg::getDeltaTempFiles).orElse(0L));
    }

    /**
     * pg_buffercache에서 버퍼 캐시 통계 수집
     * 테이블/인덱스별 버퍼 점유 현황 + usagecount
     * 
     * 주의: pg_buffercache 확장이 설치되어 있어야 함
     * 설치 방법: CREATE EXTENSION IF NOT EXISTS pg_buffercache;
     */
    private List<Map<String, Object>> collectFromPgBuffercache(JdbcTemplate jdbcTemplate) {
        // pg_buffercache 확장 설치 여부 확인
        try {
            String checkSql = "SELECT 1 FROM pg_extension WHERE extname = 'pg_buffercache'";
            List<Map<String, Object>> checkResult = jdbcTemplate.queryForList(checkSql);
            if (checkResult.isEmpty()) {
                log.warn("pg_buffercache 확장이 설치되어 있지 않습니다. 빈 데이터를 반환합니다.");
                return new ArrayList<>();
            }
        } catch (Exception e) {
            log.warn("pg_buffercache 확장 확인 실패: {}", e.getMessage());
            return new ArrayList<>();
        }
        
        String sql = """
            SELECT 
                c.relname,
                c.relkind,
                COUNT(*) as buffers,
                COUNT(*) FILTER (WHERE b.isdirty) as dirty_buffers,
                COUNT(*) FILTER (WHERE b.pinning_backends > 0) as pinned_buffers,
                AVG(b.usagecount) as avg_usagecount,
                MAX(b.usagecount) as max_usagecount,
                MIN(b.usagecount) as min_usagecount
            FROM pg_buffercache b
            LEFT JOIN pg_class c ON b.relfilenode = pg_relation_filenode(c.oid)
            WHERE c.relname IS NOT NULL
            GROUP BY c.relname, c.relkind
            UNION ALL
            -- 전체 통계 (relname = NULL)
            SELECT 
                NULL as relname,
                NULL as relkind,
                COUNT(*) as buffers,
                COUNT(*) FILTER (WHERE isdirty) as dirty_buffers,
                COUNT(*) FILTER (WHERE pinning_backends > 0) as pinned_buffers,
                AVG(usagecount) as avg_usagecount,
                MAX(usagecount) as max_usagecount,
                MIN(usagecount) as min_usagecount
            FROM pg_buffercache
            """;

        try {
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            log.error("pg_buffercache 쿼리 실행 실패: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * pg_statio에서 I/O 통계 수집
     */
    private List<Map<String, Object>> collectFromPgStatio(JdbcTemplate jdbcTemplate) {
        String sql = """
            SELECT 
                relname,
                'r' as relkind,
                heap_blks_read,
                heap_blks_hit,
                0 as idx_blks_read,
                0 as idx_blks_hit
            FROM pg_statio_user_tables
            UNION ALL
            SELECT 
                indexrelname as relname,
                'i' as relkind,
                0 as heap_blks_read,
                0 as heap_blks_hit,
                idx_blks_read,
                idx_blks_hit
            FROM pg_statio_user_indexes
            """;

        return jdbcTemplate.queryForList(sql);
    }

    /**
     * pg_stat_database에서 메모리/I/O 관련 통계 수집
     */
    private Map<String, Object> collectFromPgStatDatabase(JdbcTemplate jdbcTemplate) {
        String sql = """
            SELECT 
                datname as database_name,
                COALESCE(temp_files, 0) as temp_files,
                COALESCE(temp_bytes, 0) as temp_bytes,
                COALESCE(blk_read_time, 0) as blk_read_time,
                COALESCE(blk_write_time, 0) as blk_write_time
            FROM pg_stat_database
            WHERE datname = current_database()
            """;

        return jdbcTemplate.queryForMap(sql);
    }

    /**
     * BufferCache 데이터와 Statio 데이터 병합
     */
    private Map<String, Map<String, Object>> mergeData(
            List<Map<String, Object>> bufferData,
            List<Map<String, Object>> statioData) {
        
        Map<String, Map<String, Object>> merged = new HashMap<>();

        // BufferCache 데이터 추가
        for (Map<String, Object> data : bufferData) {
            String relname = (String) data.get("relname");
            String key = relname != null ? relname : "";
            merged.put(key, new HashMap<>(data));
        }

        // Statio 데이터 병합
        for (Map<String, Object> data : statioData) {
            String relname = (String) data.get("relname");
            if (relname != null) {
                Map<String, Object> existing = merged.get(relname);
                if (existing != null) {
                    existing.putAll(data);
                } else {
                    // BufferCache에 없는 경우 기본값 추가
                    data.put("buffers", 0L);
                    data.put("dirty_buffers", 0L);
                    data.put("pinned_buffers", 0L);
                    merged.put(relname, data);
                }
            }
        }

        return merged;
    }

    /**
     * MemoryRaw 객체 생성
     */
    private MemoryRaw buildMemoryRaw(Long instanceId, OffsetDateTime collectedAt, 
                                      Map<String, Object> data, Map<String, Object> databaseData) {
        return MemoryRaw.builder()
                .instanceId(instanceId)
                .collectedAt(collectedAt)
                .relname(getString(data, "relname"))
                .relkind(getString(data, "relkind"))
                .buffers(getLong(data, "buffers"))
                .dirtyBuffers(getLong(data, "dirty_buffers"))
                .pinnedBuffers(getLong(data, "pinned_buffers"))
                .heapBlksRead(getLong(data, "heap_blks_read"))
                .heapBlksHit(getLong(data, "heap_blks_hit"))
                .idxBlksRead(getLong(data, "idx_blks_read"))
                .idxBlksHit(getLong(data, "idx_blks_hit"))
                // pg_buffercache usagecount
                .avgUsagecount(getDouble(data, "avg_usagecount"))
                .maxUsagecount(getLong(data, "max_usagecount"))
                .minUsagecount(getLong(data, "min_usagecount"))
                // pg_stat_database 메모리/I/O 통계
                .databaseName(getString(databaseData, "database_name"))
                .tempFiles(getLong(databaseData, "temp_files"))
                .tempBytes(getLong(databaseData, "temp_bytes"))
                .blkReadTime(getDouble(databaseData, "blk_read_time"))
                .blkWriteTime(getDouble(databaseData, "blk_write_time"))
                .build();
    }

    /**
     * 전체 버퍼 수 계산
     */
    private Long calculateTotalBuffers(List<MemoryRaw> rawList) {
        return rawList.stream()
                .filter(r -> r.getRelname() == null) // 전체 통계 row
                .findFirst()
                .map(MemoryRaw::getBuffers)
                .orElse(0L);
    }

    /**
     * MemoryAgg 객체 생성 (증분 계산)
     */
    private MemoryAgg calculateAggregation(MemoryRaw current, MemoryRaw previous, 
                                            OffsetDateTime collectedAt, Long totalBuffers) {
        // 버퍼 사용률
        double bufferUsagePct = totalBuffers > 0 ? 
                (double) current.getBuffers() / totalBuffers * 100 : 0.0;

        // Dirty 비율
        double dirtyRatio = current.getBuffers() > 0 ? 
                (double) current.getDirtyBuffers() / current.getBuffers() * 100 : 0.0;

        // I/O 증분 계산
        long deltaHeapBlksRead = 0;
        long deltaHeapBlksHit = 0;
        long deltaIdxBlksRead = 0;
        long deltaIdxBlksHit = 0;
        double cacheHitRatio = 0.0;
        
        // 임시 파일 및 I/O 시간 증분
        long deltaTempFiles = 0;
        long deltaTempBytes = 0;
        double deltaBlkReadTime = 0.0;
        double deltaBlkWriteTime = 0.0;
        double tempFileRate = 0.0;
        double tempBytesPerSec = 0.0;
        double avgIoWaitTimeMs = 0.0;

        if (previous != null) {
            deltaHeapBlksRead = safeMinus(current.getHeapBlksRead(), previous.getHeapBlksRead());
            deltaHeapBlksHit = safeMinus(current.getHeapBlksHit(), previous.getHeapBlksHit());
            deltaIdxBlksRead = safeMinus(current.getIdxBlksRead(), previous.getIdxBlksRead());
            deltaIdxBlksHit = safeMinus(current.getIdxBlksHit(), previous.getIdxBlksHit());

            // 캐시 히트율 = hit / (hit + read) * 100
            long totalHit = deltaHeapBlksHit + deltaIdxBlksHit;
            long totalRead = deltaHeapBlksRead + deltaIdxBlksRead;
            long totalAccess = totalHit + totalRead;
            cacheHitRatio = totalAccess > 0 ? (double) totalHit / totalAccess * 100 : 0.0;
            
            // 임시 파일 증분
            deltaTempFiles = safeMinus(current.getTempFiles(), previous.getTempFiles());
            deltaTempBytes = safeMinus(current.getTempBytes(), previous.getTempBytes());
            
            // I/O 대기 시간 증분
            deltaBlkReadTime = safeMinusDouble(current.getBlkReadTime(), previous.getBlkReadTime());
            deltaBlkWriteTime = safeMinusDouble(current.getBlkWriteTime(), previous.getBlkWriteTime());
            
            // 수집 간격(초) 계산
            long intervalSeconds = ChronoUnit.SECONDS.between(
                previous.getCollectedAt(), current.getCollectedAt()
            );
            if (intervalSeconds <= 0) {
                intervalSeconds = 60;
            }
            
            // 초당 비율 계산
            tempFileRate = (double) deltaTempFiles / intervalSeconds;
            tempBytesPerSec = (double) deltaTempBytes / intervalSeconds;
            
            // 평균 I/O 대기 시간 (ms)
            avgIoWaitTimeMs = (deltaBlkReadTime + deltaBlkWriteTime) / 2.0;
        }
        
        // 버퍼 재사용 점수 계산 (0~100)
        // usagecount가 높을수록 재사용이 많이 됨 (최대 5)
        double bufferReuseScore = current.getAvgUsagecount() != null ? 
                Math.min(current.getAvgUsagecount() * 20, 100.0) : 0.0;

        // 상태 판정
        String status = determineStatus(bufferUsagePct, dirtyRatio, cacheHitRatio);

        return MemoryAgg.builder()
                .instanceId(current.getInstanceId())
                .collectedAt(collectedAt)
                .relname(current.getRelname())
                .relkind(current.getRelkind())
                .avgBuffers(current.getBuffers())
                .avgBufferUsagePct(bufferUsagePct)
                .avgDirtyRatio(dirtyRatio)
                .avgPinnedBuffers((double) current.getPinnedBuffers())
                .deltaHeapBlksRead(deltaHeapBlksRead)
                .deltaHeapBlksHit(deltaHeapBlksHit)
                .deltaIdxBlksRead(deltaIdxBlksRead)
                .deltaIdxBlksHit(deltaIdxBlksHit)
                .cacheHitRatio(cacheHitRatio)
                // usagecount 통계
                .avgUsagecount(current.getAvgUsagecount())
                .bufferReuseScore(bufferReuseScore)
                // 임시 파일 통계
                .databaseName(current.getDatabaseName())
                .deltaTempFiles(deltaTempFiles)
                .deltaTempBytes(deltaTempBytes)
                .tempFileRate(tempFileRate)
                .tempBytesPerSec(tempBytesPerSec)
                // I/O 대기 시간
                .deltaBlkReadTime(deltaBlkReadTime)
                .deltaBlkWriteTime(deltaBlkWriteTime)
                .avgIoWaitTimeMs(avgIoWaitTimeMs)
                .status(status)
                .build();
    }

    /**
     * 상태 판정 로직
     * - 정상: 캐시 히트율 > 95% AND Dirty 비율 < 20%
     * - 주의: 캐시 히트율 > 90% AND Dirty 비율 < 30%
     * - 위험: 그 외
     */
    private String determineStatus(double bufferUsagePct, double dirtyRatio, double cacheHitRatio) {
        if (cacheHitRatio > 95 && dirtyRatio < 20) {
            return "정상";
        } else if (cacheHitRatio > 90 && dirtyRatio < 30) {
            return "주의";
        } else {
            return "위험";
        }
    }

    // ========== Helper Methods ==========

    private long safeMinus(Long a, Long b) {
        if (a == null || b == null) return 0L;
        long result = a - b;
        return result < 0 ? 0 : result;
    }

    private double safeMinusDouble(Double a, Double b) {
        if (a == null || b == null) return 0.0;
        double result = a - b;
        return result < 0 ? 0.0 : result;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0L;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        return Long.parseLong(value.toString());
    }

    private Double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0.0;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Float) return ((Float) value).doubleValue();
        if (value instanceof Number) return ((Number) value).doubleValue();
        return Double.parseDouble(value.toString());
    }
}
