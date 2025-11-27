package com.dajanggan.domain.metric.batch.aggregator;

import com.dajanggan.domain.system.disk.domain.DiskIoAgg5m;
import com.dajanggan.domain.system.disk.dto.agg5m.DiskIoAgg5mDto;
import com.dajanggan.domain.system.disk.repository.DiskIoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Disk I/O 5분 집계 Batch Job
 * disk_io_agg_1m 테이블에서 5분간 데이터를 읽어서
 * disk_io_agg_5m에 저장
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DiskIoAgg5mAggregator {

    private final JobRepository jobRepository;
    private final DataSource dataSource;
    private final PlatformTransactionManager batchTransactionManager;
    private final DiskIoMapper diskIoMapper;

    /**
     * Job 정의
     */
    @Bean
    public Job diskIoAgg5mJob() {
        return new JobBuilder("diskIoAgg5mJob", jobRepository)
                .start(diskIoAgg5mStep())
                .build();
    }

    /**
     * Step 정의
     */
    @Bean
    public Step diskIoAgg5mStep() {
        return new StepBuilder("diskIoAgg5mStep", jobRepository)
                .<DiskIoAgg5mDto, DiskIoAgg5m>chunk(100, batchTransactionManager)
                .reader(diskIoAgg5mReader())
                .processor(diskIoAgg5mProcessor())
                .writer(diskIoAgg5mWriter())
                .build();
    }

    /**
     * Reader: disk_io_agg_1m에서 5분간 데이터 집계 (backend_type별)
     */
    @Bean
    public JdbcCursorItemReader<DiskIoAgg5mDto> diskIoAgg5mReader() {
        String sql = """
            SELECT
                DATE_TRUNC('hour', collected_at) + 
                    INTERVAL '5 minutes' * FLOOR(EXTRACT(MINUTE FROM collected_at) / 5) 
                    as time_bucket,
                instance_id,
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
            WHERE collected_at >= DATE_TRUNC('minute', CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - INTERVAL '6 minutes'
              AND collected_at < DATE_TRUNC('minute', CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - INTERVAL '1 minute'
            GROUP BY time_bucket, instance_id, backend_type
            ORDER BY instance_id, backend_type, time_bucket
            """;

        return new JdbcCursorItemReaderBuilder<DiskIoAgg5mDto>()
                .name("diskIoAgg5mReader")
                .dataSource(dataSource)
                .sql(sql)
                .rowMapper(new DiskIoAgg5mDtoRowMapper())
                .build();
    }

    /**
     * Processor: 간단한 후처리
     */
    @Bean
    public ItemProcessor<DiskIoAgg5mDto, DiskIoAgg5m> diskIoAgg5mProcessor() {
        return dto -> {
            // DiskIoAgg5m 엔티티 생성
            return DiskIoAgg5m.builder()
                    .instanceId(dto.getInstanceId())
                    .collectedAt(dto.getTimeBucket())
                    .backendType(dto.getBackendType())
                    .avgReadLatencyMs(dto.getAvgReadLatencyMs() != null ? dto.getAvgReadLatencyMs() : 0.0)
                    .avgWriteLatencyMs(dto.getAvgWriteLatencyMs() != null ? dto.getAvgWriteLatencyMs() : 0.0)
                    .avgCacheHitRatio(dto.getAvgCacheHitRatio() != null ? dto.getAvgCacheHitRatio() : 0.0)
                    .avgBufferHitRatio(dto.getAvgBufferHitRatio() != null ? dto.getAvgBufferHitRatio() : 0.0)
                    .avgBackendFsyncRate(dto.getAvgBackendFsyncRate() != null ? dto.getAvgBackendFsyncRate() : 0.0)
                    .avgReadWriteRatio(dto.getAvgReadWriteRatio() != null ? dto.getAvgReadWriteRatio() : 0.0)
                    .sumDeltaReads(dto.getSumDeltaReads() != null ? dto.getSumDeltaReads() : 0L)
                    .sumDeltaWrites(dto.getSumDeltaWrites() != null ? dto.getSumDeltaWrites() : 0L)
                    .sumDeltaFsyncs(dto.getSumDeltaFsyncs() != null ? dto.getSumDeltaFsyncs() : 0L)
                    .sumDeltaEvictions(dto.getSumDeltaEvictions() != null ? dto.getSumDeltaEvictions() : 0L)
                    .sumDeltaBlksRead(dto.getSumDeltaBlksRead() != null ? dto.getSumDeltaBlksRead() : 0L)
                    .sumDeltaBlksHit(dto.getSumDeltaBlksHit() != null ? dto.getSumDeltaBlksHit() : 0L)
                    .sumDeltaBuffersCheckpoint(dto.getSumDeltaBuffersCheckpoint() != null ? dto.getSumDeltaBuffersCheckpoint() : 0L)
                    .sumDeltaBuffersClean(dto.getSumDeltaBuffersClean() != null ? dto.getSumDeltaBuffersClean() : 0L)
                    .sumDeltaBuffersBackend(dto.getSumDeltaBuffersBackend() != null ? dto.getSumDeltaBuffersBackend() : 0L)
                    .sumDeltaBuffersBackendFsync(dto.getSumDeltaBuffersBackendFsync() != null ? dto.getSumDeltaBuffersBackendFsync() : 0L)
                    .maxReadLatencyMs(dto.getMaxReadLatencyMs() != null ? dto.getMaxReadLatencyMs() : 0.0)
                    .maxWriteLatencyMs(dto.getMaxWriteLatencyMs() != null ? dto.getMaxWriteLatencyMs() : 0.0)
                    .minBufferHitRatio(dto.getMinBufferHitRatio() != null ? dto.getMinBufferHitRatio() : 0.0)
                    .status(dto.getStatus() != null ? dto.getStatus() : "정상")
                    .build();
        };
    }

    /**
     * Writer: Agg 5분 데이터 저장
     */
    @Bean
    public ItemWriter<DiskIoAgg5m> diskIoAgg5mWriter() {
        return (Chunk<? extends DiskIoAgg5m> chunk) -> {
            List<DiskIoAgg5m> items = new ArrayList<>();
            for (DiskIoAgg5m item : chunk.getItems()) {
                if (item != null) {
                    items.add(item);
                }
            }

            if (!items.isEmpty()) {
                for (DiskIoAgg5m agg5m : items) {
                    diskIoMapper.insertAgg5m(agg5m);
                }
                log.info("Disk I/O 5분 집계 완료: {} 건 저장", items.size());
            }
        };
    }

    /**
     * RowMapper: DB 결과를 DTO로 매핑
     */
    private static class DiskIoAgg5mDtoRowMapper implements RowMapper<DiskIoAgg5mDto> {
        @Override
        public DiskIoAgg5mDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            DiskIoAgg5mDto dto = new DiskIoAgg5mDto();
            dto.setInstanceId(rs.getLong("instance_id"));

            java.sql.Timestamp timeBucketTs = rs.getTimestamp("time_bucket");
            if (timeBucketTs != null) {
                dto.setTimeBucket(OffsetDateTime.ofInstant(
                        timeBucketTs.toInstant(),
                        ZoneOffset.UTC
                ));
            }

            dto.setBackendType(rs.getString("backend_type"));
            dto.setAvgReadLatencyMs(getDouble(rs, "avg_read_latency_ms"));
            dto.setAvgWriteLatencyMs(getDouble(rs, "avg_write_latency_ms"));
            dto.setAvgCacheHitRatio(getDouble(rs, "avg_cache_hit_ratio"));
            dto.setAvgBufferHitRatio(getDouble(rs, "avg_buffer_hit_ratio"));
            dto.setAvgBackendFsyncRate(getDouble(rs, "avg_backend_fsync_rate"));
            dto.setAvgReadWriteRatio(getDouble(rs, "avg_read_write_ratio"));
            dto.setSumDeltaReads(getLong(rs, "sum_delta_reads"));
            dto.setSumDeltaWrites(getLong(rs, "sum_delta_writes"));
            dto.setSumDeltaFsyncs(getLong(rs, "sum_delta_fsyncs"));
            dto.setSumDeltaEvictions(getLong(rs, "sum_delta_evictions"));
            dto.setSumDeltaBlksRead(getLong(rs, "sum_delta_blks_read"));
            dto.setSumDeltaBlksHit(getLong(rs, "sum_delta_blks_hit"));
            dto.setSumDeltaBuffersCheckpoint(getLong(rs, "sum_delta_buffers_checkpoint"));
            dto.setSumDeltaBuffersClean(getLong(rs, "sum_delta_buffers_clean"));
            dto.setSumDeltaBuffersBackend(getLong(rs, "sum_delta_buffers_backend"));
            dto.setSumDeltaBuffersBackendFsync(getLong(rs, "sum_delta_buffers_backend_fsync"));
            dto.setMaxReadLatencyMs(getDouble(rs, "max_read_latency_ms"));
            dto.setMaxWriteLatencyMs(getDouble(rs, "max_write_latency_ms"));
            dto.setMinBufferHitRatio(getDouble(rs, "min_buffer_hit_ratio"));
            dto.setStatus(rs.getString("status"));

            return dto;
        }

        private Double getDouble(ResultSet rs, String columnName) throws SQLException {
            double value = rs.getDouble(columnName);
            return rs.wasNull() ? null : value;
        }

        private Long getLong(ResultSet rs, String columnName) throws SQLException {
            long value = rs.getLong(columnName);
            return rs.wasNull() ? null : value;
        }
    }
}



