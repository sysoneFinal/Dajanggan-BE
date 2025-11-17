package com.dajanggan.domain.system.memory.scheduler;

import com.dajanggan.domain.system.memory.domain.MemoryAgg5m;
import com.dajanggan.domain.system.memory.repository.MemoryMapper;
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
 * Memory 5분 집계 스케줄러
 * 5분마다 실행하여 memory_agg 데이터를 5분 단위로 재집계
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryAgg5mScheduler {

    private final DataSource dataSource;
    private final MemoryMapper memoryMapper;

    @PostConstruct
    public void init() {
        log.info("========== MemoryAgg5mScheduler 초기화 완료 ==========");
    }

    /**
     * 5분마다 실행 (0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55분)
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void aggregate5m() {
        log.info("========== Memory 5분 집계 시작 ==========");

        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime timeBucket = now.truncatedTo(ChronoUnit.HOURS)
                    .plusMinutes((now.getMinute() / 5) * 5);
            
            OffsetDateTime startTime = timeBucket.minusMinutes(5);
            OffsetDateTime endTime = timeBucket;

            log.info("집계 시간 범위: {} ~ {}", startTime, endTime);

            // 활성 인스턴스 조회
            List<Long> instanceIds = memoryMapper.selectActiveInstanceIds();
            log.info("처리 대상 인스턴스: {} 개", instanceIds.size());

            int successCount = 0;
            int failCount = 0;

            for (Long instanceId : instanceIds) {
                try {
                    aggregateInstance5m(instanceId, timeBucket, startTime, endTime);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("Memory 5분 집계 실패: instanceId={}", instanceId, e);
                }
            }

            log.info("========== Memory 5분 집계 완료: 성공={}, 실패={} ==========", 
                    successCount, failCount);

        } catch (Exception e) {
            log.error("Memory 5분 집계 중 오류 발생", e);
        }
    }

    /**
     * 인스턴스별 5분 집계 처리 (relname별로)
     */
    private void aggregateInstance5m(Long instanceId, OffsetDateTime timeBucket,
                                       OffsetDateTime startTime, OffsetDateTime endTime) {
        
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // memory_agg 테이블에서 5분간 데이터를 relname별로 집계
        String sql = """
            SELECT
                ? as time_bucket,
                ? as instance_id,
                COALESCE(relname, '') as relname,
                relkind,
                ROUND(AVG(avg_buffers)::numeric, 0) as avg_buffers,
                ROUND(AVG(avg_buffer_usage_pct)::numeric, 2) as avg_buffer_usage_pct,
                ROUND(AVG(avg_dirty_ratio)::numeric, 2) as avg_dirty_ratio,
                ROUND(AVG(avg_pinned_buffers)::numeric, 2) as avg_pinned_buffers,
                SUM(delta_heap_blks_read) as total_heap_blks_read,
                SUM(delta_heap_blks_hit) as total_heap_blks_hit,
                SUM(delta_idx_blks_read) as total_idx_blks_read,
                SUM(delta_idx_blks_hit) as total_idx_blks_hit,
                ROUND(AVG(cache_hit_ratio)::numeric, 2) as avg_cache_hit_ratio,
                ROUND(AVG(avg_usagecount)::numeric, 2) as avg_usagecount,
                ROUND(AVG(buffer_reuse_score)::numeric, 2) as avg_buffer_reuse_score,
                database_name,
                SUM(delta_temp_files) as total_temp_files,
                SUM(delta_temp_bytes) as total_temp_bytes,
                ROUND(AVG(temp_file_rate)::numeric, 2) as avg_temp_file_rate,
                ROUND(AVG(temp_bytes_per_sec)::numeric, 2) as avg_temp_bytes_per_sec,
                SUM(delta_blk_read_time) as total_blk_read_time,
                SUM(delta_blk_write_time) as total_blk_write_time,
                ROUND(AVG(avg_io_wait_time_ms)::numeric, 2) as avg_io_wait_time_ms,
                COUNT(*) as record_count
            FROM memory_agg
            WHERE instance_id = ?
              AND collected_at >= ?
              AND collected_at < ?
            GROUP BY COALESCE(relname, ''), relkind, database_name
            """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                sql, timeBucket, instanceId, instanceId, startTime, endTime);

        if (results.isEmpty()) {
            log.debug("집계할 데이터 없음: instanceId={}, timeBucket={}", instanceId, timeBucket);
            return;
        }

        // relname별로 집계 데이터 저장
        for (Map<String, Object> aggData : results) {
            String relname = getString(aggData, "relname");
            if (relname != null && relname.isEmpty()) {
                relname = null;  // 빈 문자열을 NULL로 변환
            }
            
            MemoryAgg5m agg5m = MemoryAgg5m.builder()
                    .instanceId(instanceId)
                    .timeBucket(timeBucket)
                    .relname(relname)
                    .relkind(getString(aggData, "relkind"))
                    .avgBuffers(getLong(aggData, "avg_buffers"))
                    .avgBufferUsagePct(getDouble(aggData, "avg_buffer_usage_pct"))
                    .avgDirtyRatio(getDouble(aggData, "avg_dirty_ratio"))
                    .avgPinnedBuffers(getDouble(aggData, "avg_pinned_buffers"))
                    .totalHeapBlksRead(getLong(aggData, "total_heap_blks_read"))
                    .totalHeapBlksHit(getLong(aggData, "total_heap_blks_hit"))
                    .totalIdxBlksRead(getLong(aggData, "total_idx_blks_read"))
                    .totalIdxBlksHit(getLong(aggData, "total_idx_blks_hit"))
                    .avgCacheHitRatio(getDouble(aggData, "avg_cache_hit_ratio"))
                    .avgUsagecount(getDouble(aggData, "avg_usagecount"))
                    .avgBufferReuseScore(getDouble(aggData, "avg_buffer_reuse_score"))
                    .databaseName(getString(aggData, "database_name"))
                    .totalTempFiles(getLong(aggData, "total_temp_files"))
                    .totalTempBytes(getLong(aggData, "total_temp_bytes"))
                    .avgTempFileRate(getDouble(aggData, "avg_temp_file_rate"))
                    .avgTempBytesPerSec(getDouble(aggData, "avg_temp_bytes_per_sec"))
                    .totalBlkReadTime(getDouble(aggData, "total_blk_read_time"))
                    .totalBlkWriteTime(getDouble(aggData, "total_blk_write_time"))
                    .avgIoWaitTimeMs(getDouble(aggData, "avg_io_wait_time_ms"))
                    .recordCount(getLong(aggData, "record_count"))
                    .build();

            memoryMapper.insertAgg5m(agg5m);
            
            log.debug("Memory 5분 집계 완료: instanceId={}, timeBucket={}, relname={}, records={}", 
                    instanceId, timeBucket, relname, agg5m.getRecordCount());
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
