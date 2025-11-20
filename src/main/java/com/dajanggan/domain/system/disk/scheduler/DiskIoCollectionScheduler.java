package com.dajanggan.domain.system.disk.scheduler;

import com.dajanggan.domain.common.util.MetricCollectionUtils;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.domain.system.disk.domain.DiskIoAgg;
import com.dajanggan.domain.system.disk.domain.DiskIoRaw;
import com.dajanggan.domain.system.disk.repository.DiskIoMapper;
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
 * Disk I/O 메트릭 수집 스케줄러 (pg_stat_io 기반)
 * 1분마다 실행:
 * 1. pg_stat_io에서 I/O 통계 수집
 * 2. Raw 데이터 저장 (backend_type별로 여러 row)
 * 3. 이전 Raw 데이터와 비교하여 증분(delta) 계산
 * 4. Agg 데이터 저장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiskIoCollectionScheduler {

    private final DiskIoMapper diskIoMapper;
    private final InstanceRepository instanceRepository;
    private final DataSourceFactory dataSourceFactory;

    @PostConstruct
    public void init() {
        log.info("========== DiskIoCollectionScheduler 초기화 완료 ==========");
    }

    /**
     * 1분마다 실행 (매분 0초)
     */
    @Scheduled(cron = "0 * * * * *")
    public void collectDiskIoMetrics() {
        log.info("========== Disk I/O 메트릭 수집 시작 (pg_stat_io) ==========");

        try {
            OffsetDateTime collectedAt = OffsetDateTime.now(ZoneOffset.UTC)
                    .truncatedTo(ChronoUnit.MINUTES);
            
            List<Long> instanceIds = diskIoMapper.selectActiveInstanceIds();
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
                    log.error("Disk I/O 메트릭 처리 실패: instanceId={}", instanceId, e);
                }
            }

            log.info("========== Disk I/O 메트릭 수집 완료: 성공={}, 실패={} ==========", 
                    successCount, failCount);

        } catch (Exception e) {
            log.error("Disk I/O 메트릭 수집 중 오류 발생", e);
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

        // 3. pg_stat_io에서 데이터 수집 (여러 row)
        List<Map<String, Object>> currentDataList = collectFromPgStatIo(jdbcTemplate);
        
        // 4. pg_stat_database에서 데이터 수집 (blks_read, blks_hit)
        List<Map<String, Object>> databaseDataList = collectFromPgStatDatabase(jdbcTemplate);
        
        // 5. pg_stat_bgwriter에서 fsync 관련 데이터 수집
        Map<String, Object> bgwriterData = collectFromPgStatBgwriter(jdbcTemplate);

        // 6. 이전 Raw 데이터 조회 (backend_type별)
        List<DiskIoRaw> previousRawList = diskIoMapper.selectPreviousRawByBackendType(instanceId);
        Map<String, DiskIoRaw> previousRawMap = new HashMap<>();
        for (DiskIoRaw prev : previousRawList) {
            String key = prev.getBackendType() + "_" + (prev.getDatabaseName() != null ? prev.getDatabaseName() : "");
            previousRawMap.put(key, prev);
        }

        // 7. Raw 데이터 생성 및 저장
        List<DiskIoRaw> rawList = new ArrayList<>();
        
        // 7-1. pg_stat_io 데이터 추가
        for (Map<String, Object> data : currentDataList) {
            DiskIoRaw raw = buildDiskIoRaw(instanceId, collectedAt, data);
            rawList.add(raw);
        }
        
        // 7-2. pg_stat_database 데이터 추가 (별도 row로 저장)
        for (Map<String, Object> dbData : databaseDataList) {
            DiskIoRaw raw = buildDiskIoRawFromDatabase(instanceId, collectedAt, dbData);
            rawList.add(raw);
        }
        
        // 7-3. pg_stat_bgwriter 데이터 추가 (전역 통계)
        DiskIoRaw bgwriterRaw = buildDiskIoRawFromBgwriter(instanceId, collectedAt, bgwriterData);
        rawList.add(bgwriterRaw);
        
        if (!rawList.isEmpty()) {
            diskIoMapper.insertRawBatch(rawList);
            log.debug("Raw 데이터 일괄 저장 완료: instanceId={}, count={}", instanceId, rawList.size());
        }

        // 8. Agg 데이터 생성 및 저장 (증분 계산)
        List<DiskIoAgg> aggList = new ArrayList<>();
        for (DiskIoRaw currentRaw : rawList) {
            String key = currentRaw.getBackendType() + "_" + (currentRaw.getDatabaseName() != null ? currentRaw.getDatabaseName() : "");
            DiskIoRaw previousRaw = previousRawMap.get(key);
            if (previousRaw != null) {
                DiskIoAgg agg = calculateAggregation(currentRaw, previousRaw, collectedAt);
                aggList.add(agg);
            }
        }

        if (!aggList.isEmpty()) {
            diskIoMapper.insertAggBatch(aggList);
            log.debug("Agg 데이터 일괄 저장 완료: instanceId={}, count={}", instanceId, aggList.size());
        }

        log.info("메트릭 처리 완료: instanceId={}, raw={}, agg={}, fsync={}", 
                instanceId, rawList.size(), aggList.size(),
                aggList.stream().filter(a -> a.getBackendFsyncRate() != null && a.getBackendFsyncRate() > 0)
                        .count());
    }

    /**
     * pg_stat_io에서 데이터 수집
     * PostgreSQL 16 이상에서만 사용 가능
     */
    private List<Map<String, Object>> collectFromPgStatIo(JdbcTemplate jdbcTemplate) {
        String sql = """
            SELECT 
                backend_type,
                object,
                context,
                reads,
                read_time,
                writes,
                write_time,
                writebacks,
                writeback_time,
                extends,
                extend_time,
                op_bytes,
                hits,
                evictions,
                reuses,
                fsyncs,
                fsync_time,
                stats_reset
            FROM pg_stat_io
            WHERE backend_type IS NOT NULL
            """;

        return jdbcTemplate.queryForList(sql);
    }
    
    /**
     * pg_stat_database에서 blks_read, blks_hit 데이터 수집
     */
    private List<Map<String, Object>> collectFromPgStatDatabase(JdbcTemplate jdbcTemplate) {
        String sql = """
            SELECT 
                datname as database_name,
                blks_read,
                blks_hit,
                blk_read_time,
                blk_write_time
            FROM pg_stat_database
            WHERE datname IS NOT NULL
              AND datname NOT IN ('template0', 'template1')
            """;

        return jdbcTemplate.queryForList(sql);
    }

    /**
     * pg_stat_bgwriter에서 fsync 관련 데이터 수집
     */
    private Map<String, Object> collectFromPgStatBgwriter(JdbcTemplate jdbcTemplate) {
        String sql = """
            SELECT 
                COALESCE(buffers_checkpoint, 0) as buffers_checkpoint,
                COALESCE(buffers_clean, 0) as buffers_clean,
                COALESCE(buffers_backend, 0) as buffers_backend,
                COALESCE(buffers_backend_fsync, 0) as buffers_backend_fsync,
                stats_reset
            FROM pg_stat_bgwriter
            """;

        return jdbcTemplate.queryForMap(sql);
    }

    /**
     * DiskIoRaw 객체 생성 (pg_stat_io 기반)
     */
    private DiskIoRaw buildDiskIoRaw(Long instanceId, OffsetDateTime collectedAt, 
                                      Map<String, Object> data) {
        return DiskIoRaw.builder()
                .instanceId(instanceId)
                .collectedAt(collectedAt)
                .backendType(MetricCollectionUtils.getStringValue(data, "backend_type"))
                .object(MetricCollectionUtils.getStringValue(data, "object"))
                .context(MetricCollectionUtils.getStringValue(data, "context"))
                .reads(MetricCollectionUtils.getLongValue(data, "reads"))
                .readTime(MetricCollectionUtils.getDoubleValue(data, "read_time"))
                .writes(MetricCollectionUtils.getLongValue(data, "writes"))
                .writeTime(MetricCollectionUtils.getDoubleValue(data, "write_time"))
                .writebacks(MetricCollectionUtils.getLongValue(data, "writebacks"))
                .writebackTime(MetricCollectionUtils.getDoubleValue(data, "writeback_time"))
                // PostgreSQL pg_stat_io에서는 'extends' 컬럼을 사용하지만, Domain에서는 'extendCount' 필드 사용
                .extendCount(MetricCollectionUtils.getLongValue(data, "extends"))
                .extendTime(MetricCollectionUtils.getDoubleValue(data, "extend_time"))
                .opBytes(MetricCollectionUtils.getLongValue(data, "op_bytes"))
                .hits(MetricCollectionUtils.getLongValue(data, "hits"))
                .evictions(MetricCollectionUtils.getLongValue(data, "evictions"))
                .reuses(MetricCollectionUtils.getLongValue(data, "reuses"))
                .fsyncs(MetricCollectionUtils.getLongValue(data, "fsyncs"))
                .fsyncTime(MetricCollectionUtils.getDoubleValue(data, "fsync_time"))
                .statsReset(getOffsetDateTime(data, "stats_reset"))
                .build();
    }
    
    /**
     * DiskIoRaw 객체 생성 (pg_stat_database 기반)
     */
    private DiskIoRaw buildDiskIoRawFromDatabase(Long instanceId, OffsetDateTime collectedAt, 
                                                   Map<String, Object> data) {
        return DiskIoRaw.builder()
                .instanceId(instanceId)
                .collectedAt(collectedAt)
                .backendType("database")  // pg_stat_database는 backend_type을 'database'로 설정
                .databaseName(MetricCollectionUtils.getStringValue(data, "database_name"))
                .blksRead(MetricCollectionUtils.getLongValue(data, "blks_read"))
                .blksHit(MetricCollectionUtils.getLongValue(data, "blks_hit"))
                .build();
    }

    /**
     * DiskIoRaw 객체 생성 (pg_stat_bgwriter 기반)
     */
    private DiskIoRaw buildDiskIoRawFromBgwriter(Long instanceId, OffsetDateTime collectedAt, 
                                                   Map<String, Object> data) {
        return DiskIoRaw.builder()
                .instanceId(instanceId)
                .collectedAt(collectedAt)
                .backendType("bgwriter")  // pg_stat_bgwriter는 backend_type을 'bgwriter'로 설정
                .buffersCheckpoint(MetricCollectionUtils.getLongValue(data, "buffers_checkpoint"))
                .buffersClean(MetricCollectionUtils.getLongValue(data, "buffers_clean"))
                .buffersBackend(MetricCollectionUtils.getLongValue(data, "buffers_backend"))
                .buffersBackendFsync(MetricCollectionUtils.getLongValue(data, "buffers_backend_fsync"))
                .statsReset(getOffsetDateTime(data, "stats_reset"))
                .build();
    }

    /**
     * DiskIoAgg 객체 생성 (증분 계산)
     */
    private DiskIoAgg calculateAggregation(DiskIoRaw current, DiskIoRaw previous, 
                                            OffsetDateTime collectedAt) {
        // 증분 계산 (stats_reset 대응을 위한 안전한 계산)
        long deltaReads = MetricCollectionUtils.calculateSafeDelta(
                current.getReads(), previous.getReads());
        double deltaReadTime = MetricCollectionUtils.calculateSafeDelta(
                current.getReadTime(), previous.getReadTime());
        long deltaWrites = MetricCollectionUtils.calculateSafeDelta(
                current.getWrites(), previous.getWrites());
        double deltaWriteTime = MetricCollectionUtils.calculateSafeDelta(
                current.getWriteTime(), previous.getWriteTime());
        long deltaWritebacks = MetricCollectionUtils.calculateSafeDelta(
                current.getWritebacks(), previous.getWritebacks());
        long deltaExtendCount = MetricCollectionUtils.calculateSafeDelta(
                current.getExtendCount(), previous.getExtendCount());
        long deltaHits = MetricCollectionUtils.calculateSafeDelta(
                current.getHits(), previous.getHits());
        long deltaEvictions = MetricCollectionUtils.calculateSafeDelta(
                current.getEvictions(), previous.getEvictions());
        long deltaFsyncs = MetricCollectionUtils.calculateSafeDelta(
                current.getFsyncs(), previous.getFsyncs());
        double deltaFsyncTime = MetricCollectionUtils.calculateSafeDelta(
                current.getFsyncTime(), previous.getFsyncTime());
        
        // pg_stat_database 메트릭 증분
        long deltaBlksRead = MetricCollectionUtils.calculateSafeDelta(
                current.getBlksRead(), previous.getBlksRead());
        long deltaBlksHit = MetricCollectionUtils.calculateSafeDelta(
                current.getBlksHit(), previous.getBlksHit());
        
        // 디버깅: 물리 읽기와 캐시 히트 값 확인 (전체 통계용)
        if ("database".equals(current.getBackendType()) && current.getDatabaseName() != null) {
            log.debug("Physical Read 계산: instanceId={}, database={}, currentBlksRead={}, previousBlksRead={}, deltaBlksRead={}, currentBlksHit={}, previousBlksHit={}, deltaBlksHit={}",
                    current.getInstanceId(), current.getDatabaseName(),
                    current.getBlksRead(), previous.getBlksRead(), deltaBlksRead,
                    current.getBlksHit(), previous.getBlksHit(), deltaBlksHit);
        }
        
        // pg_stat_bgwriter 메트릭 증분
        long deltaBuffersBackendFsync = MetricCollectionUtils.calculateSafeDelta(
                current.getBuffersBackendFsync(), previous.getBuffersBackendFsync());
        long deltaBuffersCheckpoint = MetricCollectionUtils.calculateSafeDelta(
                current.getBuffersCheckpoint(), previous.getBuffersCheckpoint());
        long deltaBuffersClean = MetricCollectionUtils.calculateSafeDelta(
                current.getBuffersClean(), previous.getBuffersClean());
        long deltaBuffersBackend = MetricCollectionUtils.calculateSafeDelta(
                current.getBuffersBackend(), previous.getBuffersBackend());

        // 평균 레이턴시 계산
        double avgReadLatency = deltaReads > 0 ? deltaReadTime / deltaReads : 0.0;
        double avgWriteLatency = deltaWrites > 0 ? deltaWriteTime / deltaWrites : 0.0;

        // 읽기/쓰기 비율
        double readWriteRatio = deltaWrites > 0 ? 
                (double) deltaReads / deltaWrites : 0.0;

        // 캐시 히트율 (pg_stat_io) = hits / (hits + reads) * 100
        long totalAccess = deltaHits + deltaReads;
        double cacheHitRatio = totalAccess > 0 ? 
                (double) deltaHits / totalAccess * 100 : 0.0;
        
        // Buffer Hit Ratio (pg_stat_database) = blks_hit / (blks_hit + blks_read) * 100
        long totalBlocks = deltaBlksHit + deltaBlksRead;
        double bufferHitRatio = totalBlocks > 0 ? 
                (double) deltaBlksHit / totalBlocks * 100 : 0.0;
        
        // Backend Fsync Rate 계산 (초당 fsync 수)
        long intervalSeconds = ChronoUnit.SECONDS.between(
            previous.getCollectedAt(), current.getCollectedAt()
        );
        if (intervalSeconds <= 0) {
            intervalSeconds = 60;
        }
        double backendFsyncRate = (double) deltaBuffersBackendFsync / intervalSeconds;

        // 상태 판정 (Buffer Hit Ratio 우선 사용)
        String status = determineStatus(avgReadLatency, avgWriteLatency, 
                bufferHitRatio > 0 ? bufferHitRatio : cacheHitRatio);

        return DiskIoAgg.builder()
                .instanceId(current.getInstanceId())
                .collectedAt(collectedAt)
                .backendType(current.getBackendType())
                .databaseName(current.getDatabaseName())
                .deltaReads(deltaReads)
                .deltaReadTime(deltaReadTime)
                .deltaWrites(deltaWrites)
                .deltaWriteTime(deltaWriteTime)
                .deltaWritebacks(deltaWritebacks)
                .deltaExtendCount(deltaExtendCount)
                .deltaHits(deltaHits)
                .deltaEvictions(deltaEvictions)
                .deltaFsyncs(deltaFsyncs)
                .deltaFsyncTime(deltaFsyncTime)
                .deltaBlksRead(deltaBlksRead)
                .deltaBlksHit(deltaBlksHit)
                .avgReadLatencyMs(avgReadLatency)
                .avgWriteLatencyMs(avgWriteLatency)
                .readWriteRatio(readWriteRatio)
                .cacheHitRatio(cacheHitRatio)
                .bufferHitRatio(bufferHitRatio)
                // pg_stat_bgwriter 증분
                .deltaBuffersBackendFsync(deltaBuffersBackendFsync)
                .deltaBuffersCheckpoint(deltaBuffersCheckpoint)
                .deltaBuffersClean(deltaBuffersClean)
                .deltaBuffersBackend(deltaBuffersBackend)
                .backendFsyncRate(backendFsyncRate)
                .status(status)
                .build();
    }

    /**
     * 상태 판정 로직
     * - 정상: 레이턴시 < 10ms AND 캐시 히트율 > 90%
     * - 주의: 레이턴시 < 50ms AND 캐시 히트율 > 80%
     * - 위험: 그 외
     */
    private String determineStatus(double readLatency, double writeLatency, double cacheHitRatio) {
        double maxLatency = Math.max(readLatency, writeLatency);
        
        if (maxLatency < 10 && cacheHitRatio > 90) {
            return "정상";
        } else if (maxLatency < 50 && cacheHitRatio > 80) {
            return "주의";
        } else {
            return "위험";
        }
    }

    // ========== Helper Methods ==========

    private OffsetDateTime getOffsetDateTime(Map<String, Object> map, String key) {
        return MetricCollectionUtils.getOffsetDateTime(map, key);
    }
}
