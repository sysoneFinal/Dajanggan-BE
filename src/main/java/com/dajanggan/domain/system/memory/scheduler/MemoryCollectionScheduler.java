package com.dajanggan.domain.system.memory.scheduler;

import com.dajanggan.domain.common.util.MetricCollectionUtils;
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
 * Memory 메트릭 수집 스케줄러 (pg_statio 기반)
 * 1분마다 실행:
 * 1. pg_statio_user_tables/indexes에서 I/O 통계 수집
 * 2. pg_stat_database에서 메모리/I/O 통계 수집
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
     * 1분마다 실행 (매분 5초) - metric/batch 스타일에 맞춤
     * 수집이 완료된 후 집계되도록 시간 조정
     */
    @Scheduled(cron = "5 * * * * *")
    public void collectMemoryMetrics() {
        log.info("========== Memory 메트릭 수집 시작 ==========");

        try {
            OffsetDateTime collectedAt = OffsetDateTime.now(ZoneOffset.UTC)
                    .truncatedTo(ChronoUnit.MINUTES);
            
            List<Long> instanceIds = memoryMapper.selectActiveInstanceIds();
            log.info("처리 대상 인스턴스: {} 개", instanceIds.size());
            
            if (instanceIds.isEmpty()) {
                log.warn("활성 인스턴스가 없습니다. DB의 instance 테이블에 is_active=true인 인스턴스가 있는지 확인하세요.");
                return;
            }

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
        List<Map<String, Object>> statioData = collectFromPgStatio(jdbcTemplate);
        Map<String, Object> databaseData = collectFromPgStatDatabase(jdbcTemplate);

        log.info("데이터 수집 완료: instanceId={}, statio={}, database={}", 
                instanceId, statioData.size(), databaseData != null ? "OK" : "NULL");
        
        if (statioData.isEmpty()) {
            log.warn("pg_statio 데이터가 비어있습니다. instanceId={}. pg_statio_user_tables와 pg_statio_user_indexes에 데이터가 있는지 확인하세요.", instanceId);
        }

        // 4. 데이터 병합 (relname 기준)
        Map<String, Map<String, Object>> mergedData = mergeData(statioData);
        
        log.info("데이터 병합 완료: instanceId={}, merged={}", instanceId, mergedData.size());

        // 5. Raw 데이터 생성 및 저장
        List<MemoryRaw> rawList = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : mergedData.entrySet()) {
            MemoryRaw raw = buildMemoryRaw(instanceId, collectedAt, entry.getValue(), databaseData);
            rawList.add(raw);
            
            // 디버깅: Raw 데이터 상세 로깅 (relname이 NULL인 전체 통계만)
            if (raw.getRelname() == null || raw.getRelname().isEmpty()) {
                log.info("Raw 데이터 생성 (전체 통계): instanceId={}, relname={}, database={}, heapBlksRead={}, heapBlksHit={}, idxBlksRead={}, idxBlksHit={}, tempFiles={}, tempBytes={}, blkReadTime={}, blkWriteTime={}",
                        instanceId, raw.getRelname(), raw.getDatabaseName(), 
                        raw.getHeapBlksRead(), raw.getHeapBlksHit(),
                        raw.getIdxBlksRead(), raw.getIdxBlksHit(),
                        raw.getTempFiles(), raw.getTempBytes(),
                        raw.getBlkReadTime(), raw.getBlkWriteTime());
            } else {
                log.debug("Raw 데이터 생성: instanceId={}, relname={}, database={}, heapBlksRead={}, heapBlksHit={}", 
                        instanceId, raw.getRelname(), raw.getDatabaseName(), 
                        raw.getHeapBlksRead(), raw.getHeapBlksHit());
            }
        }
        
        // 6-2. pg_stat_database 데이터를 별도 Raw 행으로 추가 (relname=NULL, database_name=current_database())
        // 버퍼 캐시 히트율 계산을 위해 pg_stat_database의 blks_hit, blks_read를 별도로 저장
        if (databaseData != null && databaseData.get("database_name") != null) {
            Map<String, Object> databaseStats = new HashMap<>();
            databaseStats.put("relname", null);  // NULL로 설정
            databaseStats.put("relkind", null);
            // pg_stat_database 행은 버퍼 통계 없음 (0으로 설정)
            databaseStats.put("buffers", 0L);
            databaseStats.put("dirty_buffers", 0L);
            databaseStats.put("pinned_buffers", 0L);
            // pg_stat_database의 blks_hit, blks_read를 heap_blks_hit, heap_blks_read로 매핑
            // (인덱스는 0으로 설정하여 데이터베이스 레벨 통계임을 표시)
            Long blksHit = MetricCollectionUtils.getLongValue(databaseData, "blks_hit");
            Long blksRead = MetricCollectionUtils.getLongValue(databaseData, "blks_read");
            databaseStats.put("heap_blks_read", blksRead);
            databaseStats.put("heap_blks_hit", blksHit);
            databaseStats.put("idx_blks_read", 0L);
            databaseStats.put("idx_blks_hit", 0L);
            // usagecount 통계는 pg_buffercache 미사용으로 0으로 설정
            databaseStats.put("avg_usagecount", 0.0);
            databaseStats.put("max_usagecount", 0L);
            databaseStats.put("min_usagecount", 0L);
            // database_name은 databaseData에서 가져오므로 명시적으로 설정하지 않음
            // (buildMemoryRaw에서 databaseData를 사용하여 설정됨)
            
            MemoryRaw databaseRaw = buildMemoryRaw(instanceId, collectedAt, databaseStats, databaseData);
            rawList.add(databaseRaw);
            
            log.info("Raw 데이터 생성 (pg_stat_database): instanceId={}, relname={}, database={}, heapBlksRead={}, heapBlksHit={}, idxBlksRead={}, idxBlksHit={}",
                    instanceId, databaseRaw.getRelname(), databaseRaw.getDatabaseName(),
                    databaseRaw.getHeapBlksRead(), databaseRaw.getHeapBlksHit(),
                    databaseRaw.getIdxBlksRead(), databaseRaw.getIdxBlksHit());
        }

        if (!rawList.isEmpty()) {
            try {
                memoryMapper.insertRawBatch(rawList);
                log.info("Raw 데이터 일괄 저장 완료: instanceId={}, count={}", instanceId, rawList.size());
            } catch (Exception e) {
                log.error("Raw 데이터 저장 실패: instanceId={}, count={}", instanceId, rawList.size(), e);
                throw e;
            }
        } else {
            log.warn("Raw 데이터 없음: instanceId={}, mergedData={}", instanceId, mergedData.size());
        }

        // Agg 데이터 생성은 배치로 처리
        log.info("메트릭 처리 완료: instanceId={}, raw={}", instanceId, rawList.size());
    }

    /**
     * pg_statio에서 I/O 통계 수집
     */
    private List<Map<String, Object>> collectFromPgStatio(JdbcTemplate jdbcTemplate) {
        // 먼저 실제 테이블/인덱스 존재 여부 확인
        try {
            String checkTablesSql = "SELECT COUNT(*) as count FROM pg_statio_user_tables";
            String checkIndexesSql = "SELECT COUNT(*) as count FROM pg_statio_user_indexes";
            
            Long tableViewCount = jdbcTemplate.queryForObject(checkTablesSql, Long.class);
            Long indexViewCount = jdbcTemplate.queryForObject(checkIndexesSql, Long.class);
            
            log.info("pg_statio 뷰 확인: pg_statio_user_tables 레코드 수={}, pg_statio_user_indexes 레코드 수={}", 
                    tableViewCount, indexViewCount);
            
            // 실제 사용자 테이블 존재 여부 확인
            String checkUserTablesSql = """
                SELECT COUNT(*) as count 
                FROM pg_tables 
                WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
                """;
            Long userTableCount = jdbcTemplate.queryForObject(checkUserTablesSql, Long.class);
            log.info("사용자 테이블 개수 (pg_tables): {}", userTableCount);
            
            // 실제 사용자 인덱스 존재 여부 확인
            String checkUserIndexesSql = """
                SELECT COUNT(*) as count 
                FROM pg_indexes 
                WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
                """;
            Long userIndexCount = jdbcTemplate.queryForObject(checkUserIndexesSql, Long.class);
            log.info("사용자 인덱스 개수 (pg_indexes): {}", userIndexCount);
            
        } catch (Exception e) {
            log.warn("pg_statio 뷰 확인 중 오류 발생", e);
        }
        
        String sql = """
            SELECT 
                relname,
                'r' as relkind,
                COALESCE(heap_blks_read, 0) as heap_blks_read,
                COALESCE(heap_blks_hit, 0) as heap_blks_hit,
                0 as idx_blks_read,
                0 as idx_blks_hit
            FROM pg_statio_user_tables
            WHERE relname IS NOT NULL
            UNION ALL
            SELECT 
                indexrelname as relname,
                'i' as relkind,
                0 as heap_blks_read,
                0 as heap_blks_hit,
                COALESCE(idx_blks_read, 0) as idx_blks_read,
                COALESCE(idx_blks_hit, 0) as idx_blks_hit
            FROM pg_statio_user_indexes
            WHERE indexrelname IS NOT NULL
            """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
        long tableCount = results.stream().filter(r -> "r".equals(r.get("relkind"))).count();
        long indexCount = results.stream().filter(r -> "i".equals(r.get("relkind"))).count();
        log.info("pg_statio 데이터 수집: tables={}, indexes={}, total={}", tableCount, indexCount, results.size());
        
        // 샘플 데이터 로깅 (최대 3개)
        if (!results.isEmpty()) {
            int sampleCount = Math.min(3, results.size());
            for (int i = 0; i < sampleCount; i++) {
                Map<String, Object> sample = results.get(i);
                log.info("pg_statio 샘플 데이터[{}]: relname={}, relkind={}, heap_blks_read={}, heap_blks_hit={}, idx_blks_read={}, idx_blks_hit={}",
                        i,
                        sample.get("relname"),
                        sample.get("relkind"),
                        sample.get("heap_blks_read"),
                        sample.get("heap_blks_hit"),
                        sample.get("idx_blks_read"),
                        sample.get("idx_blks_hit"));
            }
        } else {
            log.warn("pg_statio 데이터가 비어있습니다! pg_statio_user_tables와 pg_statio_user_indexes에 데이터가 있는지 확인하세요.");
            log.warn("참고: pg_statio_user_tables는 현재 사용자가 소유한 테이블만 표시합니다. 테이블이 다른 사용자나 스키마에 있으면 표시되지 않을 수 있습니다.");
        }
        
        return results;
    }

    /**
     * pg_stat_database에서 메모리/I/O 관련 통계 수집
     * blks_hit, blks_read도 수집하여 버퍼 캐시 히트율 계산에 사용
     */
    private Map<String, Object> collectFromPgStatDatabase(JdbcTemplate jdbcTemplate) {
        String sql = """
            SELECT 
                datname as database_name,
                COALESCE(blks_hit, 0) as blks_hit,
                COALESCE(blks_read, 0) as blks_read,
                COALESCE(temp_files, 0) as temp_files,
                COALESCE(temp_bytes, 0) as temp_bytes,
                COALESCE(blk_read_time, 0) as blk_read_time,
                COALESCE(blk_write_time, 0) as blk_write_time
            FROM pg_stat_database
            WHERE datname = current_database()
            """;

        Map<String, Object> result = jdbcTemplate.queryForMap(sql);
        
        // 디버깅: pg_stat_database 데이터 로깅
        log.info("pg_stat_database 데이터 수집: database_name={}, blks_hit={}, blks_read={}, temp_files={}, temp_bytes={}, blk_read_time={}, blk_write_time={}",
                result.get("database_name"),
                result.get("blks_hit"),
                result.get("blks_read"),
                result.get("temp_files"),
                result.get("temp_bytes"),
                result.get("blk_read_time"),
                result.get("blk_write_time"));
        
        // I/O 대기 시간 확인 (track_io_timing 설정 확인)
        Double blkReadTime = (Double) result.get("blk_read_time");
        Double blkWriteTime = (Double) result.get("blk_write_time");
        
        if (blkReadTime == null || blkWriteTime == null) {
            log.warn("I/O 대기 시간 데이터가 NULL입니다. PostgreSQL의 track_io_timing 설정을 확인하세요. (SHOW track_io_timing;)");
        } else if (blkReadTime == 0.0 && blkWriteTime == 0.0) {
            log.debug("I/O 대기 시간이 0입니다. track_io_timing이 활성화되어 있지 않거나 실제 I/O 대기가 없을 수 있습니다.");
        } else {
            log.debug("I/O 대기 시간 수집: blk_read_time={}, blk_write_time={}", blkReadTime, blkWriteTime);
        }
        
        return result;
    }


    /**
     * Statio 데이터 병합
     * pg_buffercache 미사용으로 버퍼 통계는 모두 0으로 설정
     */
    private Map<String, Map<String, Object>> mergeData(
            List<Map<String, Object>> statioData) {
        
        Map<String, Map<String, Object>> merged = new HashMap<>();

        // 전체 통계를 위한 합계 변수
        long totalHeapBlksRead = 0L;
        long totalHeapBlksHit = 0L;
        long totalIdxBlksRead = 0L;
        long totalIdxBlksHit = 0L;

        // Statio 데이터 추가 및 전체 통계 합산
        for (Map<String, Object> data : statioData) {
            String relname = (String) data.get("relname");
            if (relname != null) {
                // pg_buffercache 미사용으로 버퍼 통계는 모두 0으로 설정
                data.put("buffers", 0L);
                data.put("dirty_buffers", 0L);
                data.put("pinned_buffers", 0L);
                data.put("avg_usagecount", 0.0);
                data.put("max_usagecount", 0L);
                data.put("min_usagecount", 0L);
                merged.put(relname, data);
                
                // 전체 통계를 위한 합산
                Object heapBlksRead = data.get("heap_blks_read");
                Object heapBlksHit = data.get("heap_blks_hit");
                Object idxBlksRead = data.get("idx_blks_read");
                Object idxBlksHit = data.get("idx_blks_hit");
                
                if (heapBlksRead instanceof Number) {
                    totalHeapBlksRead += ((Number) heapBlksRead).longValue();
                }
                if (heapBlksHit instanceof Number) {
                    totalHeapBlksHit += ((Number) heapBlksHit).longValue();
                }
                if (idxBlksRead instanceof Number) {
                    totalIdxBlksRead += ((Number) idxBlksRead).longValue();
                }
                if (idxBlksHit instanceof Number) {
                    totalIdxBlksHit += ((Number) idxBlksHit).longValue();
                }
            }
        }

        // 전체 통계(relname = NULL) 생성
        // pg_statio_user_tables와 pg_statio_user_indexes의 합계
        // database_name은 NULL로 설정하여 pg_stat_database 행과 구분
        Map<String, Object> totalStats = new HashMap<>();
        totalStats.put("relname", null);  // NULL로 설정 (빈 문자열 아님)
        totalStats.put("relkind", null);
        
        // pg_buffercache 미사용으로 버퍼 통계는 모두 0으로 설정
        totalStats.put("buffers", 0L);
        totalStats.put("dirty_buffers", 0L);
        totalStats.put("pinned_buffers", 0L);
        totalStats.put("avg_usagecount", 0.0);
        totalStats.put("max_usagecount", 0L);
        totalStats.put("min_usagecount", 0L);
        
        totalStats.put("heap_blks_read", totalHeapBlksRead);  // 합산된 값 사용
        totalStats.put("heap_blks_hit", totalHeapBlksHit);  // 합산된 값 사용
        totalStats.put("idx_blks_read", totalIdxBlksRead);  // 합산된 값 사용
        totalStats.put("idx_blks_hit", totalIdxBlksHit);    // 합산된 값 사용
        // database_name을 NULL로 설정하여 pg_stat_database 행과 구분
        // (buildMemoryRaw에서 databaseData를 사용하므로 명시적으로 NULL 설정 필요)
        totalStats.put("database_name", null);
        // 키는 빈 문자열이지만, relname은 NULL로 저장됨
        merged.put("", totalStats);
        
        log.info("전체 통계 생성: heap_blks_read={}, heap_blks_hit={}, idx_blks_read={}, idx_blks_hit={}", 
                totalHeapBlksRead, totalHeapBlksHit, totalIdxBlksRead, totalIdxBlksHit);

        return merged;
    }

    /**
     * MemoryRaw 객체 생성
     */
    private MemoryRaw buildMemoryRaw(Long instanceId, OffsetDateTime collectedAt, 
                                      Map<String, Object> data, Map<String, Object> databaseData) {
        // database_name은 data Map에 명시적으로 설정되어 있으면 그것을 사용, 없으면 databaseData에서 가져옴
        String databaseName = MetricCollectionUtils.getStringValue(data, "database_name");
        if (databaseName == null && databaseData != null) {
            databaseName = MetricCollectionUtils.getStringValue(databaseData, "database_name");
        }
        
        return MemoryRaw.builder()
                .instanceId(instanceId)
                .collectedAt(collectedAt)
                .relname(MetricCollectionUtils.getStringValue(data, "relname"))
                .relkind(MetricCollectionUtils.getStringValue(data, "relkind"))
                .buffers(MetricCollectionUtils.getLongValue(data, "buffers"))
                .dirtyBuffers(MetricCollectionUtils.getLongValue(data, "dirty_buffers"))
                .pinnedBuffers(MetricCollectionUtils.getLongValue(data, "pinned_buffers"))
                .heapBlksRead(MetricCollectionUtils.getLongValue(data, "heap_blks_read"))
                .heapBlksHit(MetricCollectionUtils.getLongValue(data, "heap_blks_hit"))
                .idxBlksRead(MetricCollectionUtils.getLongValue(data, "idx_blks_read"))
                .idxBlksHit(MetricCollectionUtils.getLongValue(data, "idx_blks_hit"))
                // usagecount (기본값 0)
                .avgUsagecount(MetricCollectionUtils.getDoubleValue(data, "avg_usagecount"))
                .maxUsagecount(MetricCollectionUtils.getLongValue(data, "max_usagecount"))
                .minUsagecount(MetricCollectionUtils.getLongValue(data, "min_usagecount"))
                // pg_stat_database 메모리/I/O 통계
                // database_name은 data Map에 명시적으로 설정되어 있으면 그것을 사용
                .databaseName(databaseName)
                .tempFiles(MetricCollectionUtils.getLongValue(databaseData, "temp_files"))
                .tempBytes(MetricCollectionUtils.getLongValue(databaseData, "temp_bytes"))
                .blkReadTime(MetricCollectionUtils.getDoubleValue(databaseData, "blk_read_time"))
                .blkWriteTime(MetricCollectionUtils.getDoubleValue(databaseData, "blk_write_time"))
                .build();
    }


    /**
     * MemoryAgg 객체 생성 (증분 계산)
     * pg_buffercache 미사용으로 버퍼 관련 메트릭은 0으로 설정
     */
    private MemoryAgg calculateAggregation(MemoryRaw current, MemoryRaw previous, 
                                            OffsetDateTime collectedAt, Long totalBuffers) {
        // pg_buffercache 미사용으로 버퍼 사용률은 0
        double bufferUsagePct = 0.0;

        // pg_buffercache 미사용으로 Dirty 비율은 0
        double dirtyRatio = 0.0;

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
            // 공통 유틸리티 사용 (stats_reset 대응)
            deltaHeapBlksRead = MetricCollectionUtils.calculateSafeDelta(
                    current.getHeapBlksRead(), previous.getHeapBlksRead());
            deltaHeapBlksHit = MetricCollectionUtils.calculateSafeDelta(
                    current.getHeapBlksHit(), previous.getHeapBlksHit());
            deltaIdxBlksRead = MetricCollectionUtils.calculateSafeDelta(
                    current.getIdxBlksRead(), previous.getIdxBlksRead());
            deltaIdxBlksHit = MetricCollectionUtils.calculateSafeDelta(
                    current.getIdxBlksHit(), previous.getIdxBlksHit());

            // 캐시 히트율 = hit / (hit + read) * 100
            long totalHit = deltaHeapBlksHit + deltaIdxBlksHit;
            long totalRead = deltaHeapBlksRead + deltaIdxBlksRead;
            long totalAccess = totalHit + totalRead;
            cacheHitRatio = totalAccess > 0 ? (double) totalHit / totalAccess * 100 : 0.0;
            
            // 전체 통계(relname=NULL)의 경우 상세 로깅
            if (current.getRelname() == null) {
                log.info("전체 통계 캐시 히트율 계산: instanceId={}, deltaHeapBlksRead={}, deltaHeapBlksHit={}, deltaIdxBlksRead={}, deltaIdxBlksHit={}, totalHit={}, totalRead={}, totalAccess={}, cacheHitRatio={}%",
                        current.getInstanceId(), deltaHeapBlksRead, deltaHeapBlksHit, deltaIdxBlksRead, deltaIdxBlksHit,
                        totalHit, totalRead, totalAccess, cacheHitRatio);
            }
            
            // 임시 파일 증분
            deltaTempFiles = MetricCollectionUtils.calculateSafeDelta(
                    current.getTempFiles(), previous.getTempFiles());
            deltaTempBytes = MetricCollectionUtils.calculateSafeDelta(
                    current.getTempBytes(), previous.getTempBytes());
            
            // I/O 대기 시간 증분
            deltaBlkReadTime = MetricCollectionUtils.calculateSafeDelta(
                    current.getBlkReadTime(), previous.getBlkReadTime());
            deltaBlkWriteTime = MetricCollectionUtils.calculateSafeDelta(
                    current.getBlkWriteTime(), previous.getBlkWriteTime());
            
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
            
            // I/O 대기 시간 로깅 (디버깅용)
            if (deltaBlkReadTime > 0 || deltaBlkWriteTime > 0) {
                log.debug("I/O 대기 시간 증분: deltaBlkReadTime={}, deltaBlkWriteTime={}, avgIoWaitTimeMs={}", 
                        deltaBlkReadTime, deltaBlkWriteTime, avgIoWaitTimeMs);
            }
        }
        
        // pg_buffercache 미사용으로 버퍼 재사용 점수는 0
        double bufferReuseScore = 0.0;

        // 상태 판정 (버퍼 관련 메트릭 제외)
        String status = determineStatus(cacheHitRatio);

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
                // usagecount 통계 (기본값 0)
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
     * pg_buffercache 미사용으로 캐시 히트율만으로 판정
     * - 정상: 캐시 히트율 > 95%
     * - 주의: 캐시 히트율 > 90%
     * - 위험: 그 외
     */
    private String determineStatus(double cacheHitRatio) {
        if (cacheHitRatio > 95) {
            return "정상";
        } else if (cacheHitRatio > 90) {
            return "주의";
        } else {
            return "위험";
        }
    }

}
