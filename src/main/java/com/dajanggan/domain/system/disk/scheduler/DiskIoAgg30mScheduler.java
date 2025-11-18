package com.dajanggan.domain.system.disk.scheduler;

import com.dajanggan.domain.system.disk.domain.DiskIoAgg30m;
import com.dajanggan.domain.system.disk.repository.DiskIoMapper;
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
 * Disk I/O 30분 집계 스케줄러
 * 30분마다 실행하여 disk_io_agg 데이터를 30분 단위로 재집계
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiskIoAgg30mScheduler {

    private final DataSource dataSource;
    private final DiskIoMapper diskIoMapper;

    @PostConstruct
    public void init() {
        log.info("========== DiskIoAgg30mScheduler 초기화 완료 ==========");
    }

    /**
     * 30분마다 실행 (0, 30분)
     */
    @Scheduled(cron = "0 */30 * * * *")
    public void aggregate30m() {
        log.info("========== Disk I/O 30분 집계 시작 ==========");

        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime timeBucket = now.truncatedTo(ChronoUnit.HOURS)
                    .plusMinutes((now.getMinute() / 30) * 30);
            
            OffsetDateTime startTime = timeBucket.minusMinutes(30);
            OffsetDateTime endTime = timeBucket;

            log.info("집계 시간 범위: {} ~ {}", startTime, endTime);

            // 활성 인스턴스 조회
            List<Long> instanceIds = diskIoMapper.selectActiveInstanceIds();
            log.info("처리 대상 인스턴스: {} 개", instanceIds.size());

            int successCount = 0;
            int failCount = 0;

            for (Long instanceId : instanceIds) {
                try {
                    aggregateInstance30m(instanceId, timeBucket, startTime, endTime);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("Disk I/O 30분 집계 실패: instanceId={}", instanceId, e);
                }
            }

            log.info("========== Disk I/O 30분 집계 완료: 성공={}, 실패={} ==========", 
                    successCount, failCount);

        } catch (Exception e) {
            log.error("Disk I/O 30분 집계 중 오류 발생", e);
        }
    }

    /**
     * 인스턴스별 30분 집계 처리 (backend_type별로)
     */
    private void aggregateInstance30m(Long instanceId, OffsetDateTime timeBucket,
                                       OffsetDateTime startTime, OffsetDateTime endTime) {
        
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // disk_io_agg 테이블에서 30분간 데이터를 backend_type별로 집계
        String sql = """
            SELECT
                ? as time_bucket,
                ? as instance_id,
                backend_type,
                ROUND(AVG(avg_read_latency_ms)::numeric, 2) as avg_read_latency_ms,
                ROUND(AVG(avg_write_latency_ms)::numeric, 2) as avg_write_latency_ms,
                ROUND(AVG(cache_hit_ratio)::numeric, 2) as avg_cache_hit_ratio,
                ROUND(AVG(buffer_hit_ratio)::numeric, 2) as avg_buffer_hit_ratio,
                ROUND(AVG(backend_fsync_rate)::numeric, 2) as avg_backend_fsync_rate,
                ROUND(AVG(read_write_ratio)::numeric, 2) as avg_read_write_ratio,
                SUM(delta_reads) as sum_delta_reads,
                SUM(delta_writes) as sum_delta_writes,
                SUM(delta_fsyncs) as sum_delta_fsyncs,
                SUM(delta_evictions) as sum_delta_evictions,
                SUM(delta_blks_read) as sum_delta_blks_read,
                SUM(delta_blks_hit) as sum_delta_blks_hit,
                SUM(delta_buffers_checkpoint) as sum_delta_buffers_checkpoint,
                SUM(delta_buffers_clean) as sum_delta_buffers_clean,
                SUM(delta_buffers_backend) as sum_delta_buffers_backend,
                SUM(delta_buffers_backend_fsync) as sum_delta_buffers_backend_fsync,
                MAX(avg_read_latency_ms) as max_read_latency_ms,
                MAX(avg_write_latency_ms) as max_write_latency_ms,
                MIN(buffer_hit_ratio) as min_buffer_hit_ratio,
                CASE
                    WHEN AVG(avg_read_latency_ms) > 50 OR AVG(avg_write_latency_ms) > 50 THEN '위험'
                    WHEN AVG(avg_read_latency_ms) > 20 OR AVG(avg_write_latency_ms) > 20 THEN '주의'
                    ELSE '정상'
                END as status
            FROM disk_io_agg_1m
            WHERE instance_id = ?
              AND collected_at >= ?
              AND collected_at < ?
            GROUP BY backend_type
            """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                sql, timeBucket, instanceId, instanceId, startTime, endTime);

        if (results.isEmpty()) {
            log.debug("집계할 데이터 없음: instanceId={}, timeBucket={}", instanceId, timeBucket);
            return;
        }

        // backend_type별로 집계 데이터 저장
        for (Map<String, Object> aggData : results) {
            DiskIoAgg30m agg30m = DiskIoAgg30m.builder()
                    .instanceId(instanceId)
                    .collectedAt(timeBucket)
                    .backendType(getString(aggData, "backend_type"))
                    .avgReadLatencyMs(getDouble(aggData, "avg_read_latency_ms"))
                    .avgWriteLatencyMs(getDouble(aggData, "avg_write_latency_ms"))
                    .avgCacheHitRatio(getDouble(aggData, "avg_cache_hit_ratio"))
                    .avgBufferHitRatio(getDouble(aggData, "avg_buffer_hit_ratio"))
                    .avgBackendFsyncRate(getDouble(aggData, "avg_backend_fsync_rate"))
                    .avgReadWriteRatio(getDouble(aggData, "avg_read_write_ratio"))
                    .sumDeltaReads(getLong(aggData, "sum_delta_reads"))
                    .sumDeltaWrites(getLong(aggData, "sum_delta_writes"))
                    .sumDeltaFsyncs(getLong(aggData, "sum_delta_fsyncs"))
                    .sumDeltaEvictions(getLong(aggData, "sum_delta_evictions"))
                    .sumDeltaBlksRead(getLong(aggData, "sum_delta_blks_read"))
                    .sumDeltaBlksHit(getLong(aggData, "sum_delta_blks_hit"))
                    .sumDeltaBuffersCheckpoint(getLong(aggData, "sum_delta_buffers_checkpoint"))
                    .sumDeltaBuffersClean(getLong(aggData, "sum_delta_buffers_clean"))
                    .sumDeltaBuffersBackend(getLong(aggData, "sum_delta_buffers_backend"))
                    .sumDeltaBuffersBackendFsync(getLong(aggData, "sum_delta_buffers_backend_fsync"))
                    .maxReadLatencyMs(getDouble(aggData, "max_read_latency_ms"))
                    .maxWriteLatencyMs(getDouble(aggData, "max_write_latency_ms"))
                    .minBufferHitRatio(getDouble(aggData, "min_buffer_hit_ratio"))
                    .status(getString(aggData, "status"))
                    .build();

            diskIoMapper.insertAgg30m(agg30m);
            
            log.debug("Disk I/O 30분 집계 완료: instanceId={}, timeBucket={}, backendType={}", 
                    instanceId, timeBucket, agg30m.getBackendType());
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
