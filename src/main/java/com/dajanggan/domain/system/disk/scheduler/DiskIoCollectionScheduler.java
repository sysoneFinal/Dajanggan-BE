package com.dajanggan.domain.system.disk.scheduler;

import com.dajanggan.domain.common.util.MetricCollectionUtils;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.repository.InstanceRepository;
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
     * 1분마다 실행 (매분 5초) - metric/batch 스타일에 맞춤
     * 수집이 완료된 후 집계되도록 시간 조정
     */
    @Scheduled(cron = "5 * * * * *")
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

        // 6. Raw 데이터 생성 및 저장
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

        // Agg 데이터 생성은 배치로 처리
        log.info("메트릭 처리 완료: instanceId={}, raw={}", instanceId, rawList.size());
    }

    /**
     * pg_stat_io에서 데이터 수집
     * PostgreSQL 16 이상에서만 사용 가능
     * context='normal'만 필터링 (latency 측정이 되는 context)
     */
    private List<Map<String, Object>> collectFromPgStatIo(JdbcTemplate jdbcTemplate) {
        String sql = """
            SELECT 
                backend_type,
                object,
                context,
                COALESCE(reads, 0) as reads,
                COALESCE(read_time, 0.0) as read_time,
                COALESCE(writes, 0) as writes,
                COALESCE(write_time, 0.0) as write_time,
                COALESCE(writebacks, 0) as writebacks,
                COALESCE(writeback_time, 0.0) as writeback_time,
                COALESCE(extends, 0) as extends,
                COALESCE(extend_time, 0.0) as extend_time,
                COALESCE(op_bytes, 0) as op_bytes,
                COALESCE(hits, 0) as hits,
                COALESCE(evictions, 0) as evictions,
                COALESCE(reuses, 0) as reuses,
                COALESCE(fsyncs, 0) as fsyncs,
                COALESCE(fsync_time, 0.0) as fsync_time,
                stats_reset
            FROM pg_stat_io
            WHERE backend_type IS NOT NULL
              AND context = 'normal'
            """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
        
        // 디버깅: SQL 쿼리 결과 확인
        if (!results.isEmpty()) {
            Map<String, Object> firstRow = results.get(0);
            log.debug("pg_stat_io SQL 쿼리 결과 첫 번째 행: backend_type={}, reads={} ({}), read_time={} ({}), writes={} ({}), write_time={} ({})",
                    firstRow.get("backend_type"),
                    firstRow.get("reads"), firstRow.get("reads") != null ? firstRow.get("reads").getClass().getName() : "null",
                    firstRow.get("read_time"), firstRow.get("read_time") != null ? firstRow.get("read_time").getClass().getName() : "null",
                    firstRow.get("writes"), firstRow.get("writes") != null ? firstRow.get("writes").getClass().getName() : "null",
                    firstRow.get("write_time"), firstRow.get("write_time") != null ? firstRow.get("write_time").getClass().getName() : "null"
            );
        }
        
        return results;
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
        // 디버깅: read_time, write_time 값 확인
        Object readTimeObj = data.get("read_time");
        Object writeTimeObj = data.get("write_time");
        log.debug("buildDiskIoRaw - backend_type: {}, reads: {}, read_time raw: {} (type: {}), writes: {}, write_time raw: {} (type: {})",
                data.get("backend_type"),
                data.get("reads"),
                readTimeObj,
                readTimeObj != null ? readTimeObj.getClass().getName() : "null",
                data.get("writes"),
                writeTimeObj,
                writeTimeObj != null ? writeTimeObj.getClass().getName() : "null"
        );
        
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


    // ========== Helper Methods ==========

    private OffsetDateTime getOffsetDateTime(Map<String, Object> map, String key) {
        return MetricCollectionUtils.getOffsetDateTime(map, key);
    }
}
