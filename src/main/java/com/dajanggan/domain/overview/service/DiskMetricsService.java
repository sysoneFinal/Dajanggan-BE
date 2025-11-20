package com.dajanggan.domain.overview.service;

import com.dajanggan.domain.instance.domain.Instance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
public class DiskMetricsService {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public DiskMetricsService(NamedParameterJdbcTemplate namedJdbcTemplate) {
        this.namedJdbcTemplate = namedJdbcTemplate;
    }

    public record DiskStats(
            long lockWaitCount,
            long ioWaitCount,
            long totalBlksHit,
            long totalBlksRead,
            BigDecimal avgCacheHitRatio,
            BigDecimal avgReadLatency,
            BigDecimal avgWriteLatency
    ) {}

    public DiskStats aggregate(Instance instance, String databaseName,
                               Long instanceId, Long databaseId,
                               java.time.OffsetDateTime from, java.time.OffsetDateTime to) {

        String diskSql = """
            SELECT 
                COALESCE(SUM(delta_blks_hit), 0) as total_blks_hit,
                COALESCE(SUM(delta_blks_read), 0) as total_blks_read,
                COALESCE(ROUND(AVG(cache_hit_ratio)::numeric, 2), 0) as avg_cache_hit_ratio,
                COALESCE(ROUND(AVG(avg_read_latency_ms)::numeric, 2), 0) as avg_read_latency,
                COALESCE(ROUND(AVG(avg_write_latency_ms)::numeric, 2), 0) as avg_write_latency
            FROM disk_io_agg_1m
            WHERE instance_id = :instanceId
              AND collected_at BETWEEN :from AND :to
        """;

        MapSqlParameterSource diskParams = new MapSqlParameterSource()
                .addValue("instanceId", instanceId)
                .addValue("from", from)
                .addValue("to", to);

        var diskResult = namedJdbcTemplate.queryForMap(diskSql, diskParams);

        long blksHit = ((Number) diskResult.get("total_blks_hit")).longValue();
        long blksRead = ((Number) diskResult.get("total_blks_read")).longValue();
        BigDecimal cacheHitRatio = (BigDecimal) diskResult.get("avg_cache_hit_ratio");
        BigDecimal avgReadLatency = (BigDecimal) diskResult.get("avg_read_latency");
        BigDecimal avgWriteLatency = (BigDecimal) diskResult.get("avg_write_latency");

        // Wait events
        String waitSql = """
            SELECT 
                COALESCE(SUM(lock_wait_count), 0) as lock_wait_count,
                COALESCE(SUM(io_wait_count), 0) as io_wait_count
            FROM session_metrics_agg_1m
            WHERE instance_id = :instanceId
              AND database_id = :databaseId
              AND collected_at BETWEEN :from AND :to
        """;

        MapSqlParameterSource waitParams = new MapSqlParameterSource()
                .addValue("instanceId", instanceId)
                .addValue("databaseId", databaseId)
                .addValue("from", from)
                .addValue("to", to);

        var waitResult = namedJdbcTemplate.queryForMap(waitSql, waitParams);

        long lockWait = ((Number) waitResult.get("lock_wait_count")).longValue();
        long ioWait = ((Number) waitResult.get("io_wait_count")).longValue();

        return new DiskStats(
                lockWait, ioWait,
                blksHit, blksRead,
                cacheHitRatio,
                avgReadLatency,
                avgWriteLatency
        );
    }
}
