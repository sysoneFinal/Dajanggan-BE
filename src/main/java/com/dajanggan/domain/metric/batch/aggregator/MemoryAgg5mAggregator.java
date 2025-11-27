package com.dajanggan.domain.metric.batch.aggregator;

import com.dajanggan.domain.system.memory.domain.MemoryAgg5m;
import com.dajanggan.domain.system.memory.dto.agg5m.MemoryAgg5mDto;
import com.dajanggan.domain.system.memory.repository.MemoryMapper;
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
 * Memory 5분 집계 Batch Job
 * memory_agg_1m 테이블에서 5분간 데이터를 읽어서
 * memory_agg_5m에 저장
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MemoryAgg5mAggregator {

    private final JobRepository jobRepository;
    private final DataSource dataSource;
    private final PlatformTransactionManager batchTransactionManager;
    private final MemoryMapper memoryMapper;

    /**
     * Job 정의
     */
    @Bean
    public Job memoryAgg5mJob() {
        return new JobBuilder("memoryAgg5mJob", jobRepository)
                .start(memoryAgg5mStep())
                .build();
    }

    /**
     * Step 정의
     */
    @Bean
    public Step memoryAgg5mStep() {
        return new StepBuilder("memoryAgg5mStep", jobRepository)
                .<MemoryAgg5mDto, MemoryAgg5m>chunk(100, batchTransactionManager)
                .reader(memoryAgg5mReader())
                .processor(memoryAgg5mProcessor())
                .writer(memoryAgg5mWriter())
                .build();
    }

    /**
     * Reader: memory_agg_1m에서 5분간 데이터 집계 (relname별)
     */
    @Bean
    public JdbcCursorItemReader<MemoryAgg5mDto> memoryAgg5mReader() {
        String sql = """
            SELECT
                DATE_TRUNC('hour', collected_at) + 
                    INTERVAL '5 minutes' * FLOOR(EXTRACT(MINUTE FROM collected_at) / 5) 
                    as time_bucket,
                instance_id,
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
            FROM memory_agg_1m
            WHERE collected_at >= DATE_TRUNC('minute', CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - INTERVAL '6 minutes'
              AND collected_at < DATE_TRUNC('minute', CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - INTERVAL '1 minute'
            GROUP BY time_bucket, instance_id, COALESCE(relname, ''), relkind, database_name
            ORDER BY instance_id, COALESCE(relname, ''), database_name, time_bucket
            """;

        return new JdbcCursorItemReaderBuilder<MemoryAgg5mDto>()
                .name("memoryAgg5mReader")
                .dataSource(dataSource)
                .sql(sql)
                .rowMapper(new MemoryAgg5mDtoRowMapper())
                .build();
    }

    /**
     * Processor: 간단한 후처리
     */
    @Bean
    public ItemProcessor<MemoryAgg5mDto, MemoryAgg5m> memoryAgg5mProcessor() {
        return dto -> {
            // null 값 처리
            if (dto.getRecordCount() == null || dto.getRecordCount() == 0) {
                return null; // Skip 처리
            }

            String relname = dto.getRelname();
            if (relname != null && relname.isEmpty()) {
                relname = null;  // 빈 문자열을 NULL로 변환
            }

            // MemoryAgg5m 엔티티 생성
            return MemoryAgg5m.builder()
                    .instanceId(dto.getInstanceId())
                    .timeBucket(dto.getTimeBucket())
                    .relname(relname)
                    .relkind(dto.getRelkind())
                    .avgBuffers(dto.getAvgBuffers() != null ? dto.getAvgBuffers() : 0L)
                    .avgBufferUsagePct(dto.getAvgBufferUsagePct() != null ? dto.getAvgBufferUsagePct() : 0.0)
                    .avgDirtyRatio(dto.getAvgDirtyRatio() != null ? dto.getAvgDirtyRatio() : 0.0)
                    .avgPinnedBuffers(dto.getAvgPinnedBuffers() != null ? dto.getAvgPinnedBuffers() : 0.0)
                    .totalHeapBlksRead(dto.getTotalHeapBlksRead() != null ? dto.getTotalHeapBlksRead() : 0L)
                    .totalHeapBlksHit(dto.getTotalHeapBlksHit() != null ? dto.getTotalHeapBlksHit() : 0L)
                    .totalIdxBlksRead(dto.getTotalIdxBlksRead() != null ? dto.getTotalIdxBlksRead() : 0L)
                    .totalIdxBlksHit(dto.getTotalIdxBlksHit() != null ? dto.getTotalIdxBlksHit() : 0L)
                    .avgCacheHitRatio(dto.getAvgCacheHitRatio() != null ? dto.getAvgCacheHitRatio() : 0.0)
                    .avgUsagecount(dto.getAvgUsagecount() != null ? dto.getAvgUsagecount() : 0.0)
                    .avgBufferReuseScore(dto.getAvgBufferReuseScore() != null ? dto.getAvgBufferReuseScore() : 0.0)
                    .databaseName(dto.getDatabaseName())
                    .totalTempFiles(dto.getTotalTempFiles() != null ? dto.getTotalTempFiles() : 0L)
                    .totalTempBytes(dto.getTotalTempBytes() != null ? dto.getTotalTempBytes() : 0L)
                    .avgTempFileRate(dto.getAvgTempFileRate() != null ? dto.getAvgTempFileRate() : 0.0)
                    .avgTempBytesPerSec(dto.getAvgTempBytesPerSec() != null ? dto.getAvgTempBytesPerSec() : 0.0)
                    .totalBlkReadTime(dto.getTotalBlkReadTime() != null ? dto.getTotalBlkReadTime() : 0.0)
                    .totalBlkWriteTime(dto.getTotalBlkWriteTime() != null ? dto.getTotalBlkWriteTime() : 0.0)
                    .avgIoWaitTimeMs(dto.getAvgIoWaitTimeMs() != null ? dto.getAvgIoWaitTimeMs() : 0.0)
                    .recordCount(dto.getRecordCount() != null ? dto.getRecordCount() : 0L)
                    .build();
        };
    }

    /**
     * Writer: Agg 5분 데이터 저장
     */
    @Bean
    public ItemWriter<MemoryAgg5m> memoryAgg5mWriter() {
        return (Chunk<? extends MemoryAgg5m> chunk) -> {
            List<MemoryAgg5m> items = new ArrayList<>();
            for (MemoryAgg5m item : chunk.getItems()) {
                if (item != null) {
                    items.add(item);
                }
            }

            if (!items.isEmpty()) {
                for (MemoryAgg5m agg5m : items) {
                    memoryMapper.insertAgg5m(agg5m);
                }
                log.info("Memory 5분 집계 완료: {} 건 저장", items.size());
            }
        };
    }

    /**
     * RowMapper: DB 결과를 DTO로 매핑
     */
    private static class MemoryAgg5mDtoRowMapper implements RowMapper<MemoryAgg5mDto> {
        @Override
        public MemoryAgg5mDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            MemoryAgg5mDto dto = new MemoryAgg5mDto();
            dto.setInstanceId(rs.getLong("instance_id"));

            java.sql.Timestamp timeBucketTs = rs.getTimestamp("time_bucket");
            if (timeBucketTs != null) {
                dto.setTimeBucket(OffsetDateTime.ofInstant(
                        timeBucketTs.toInstant(),
                        ZoneOffset.UTC
                ));
            }

            String relname = rs.getString("relname");
            dto.setRelname(relname != null && relname.isEmpty() ? null : relname);
            dto.setRelkind(rs.getString("relkind"));
            dto.setAvgBuffers(getLong(rs, "avg_buffers"));
            dto.setAvgBufferUsagePct(getDouble(rs, "avg_buffer_usage_pct"));
            dto.setAvgDirtyRatio(getDouble(rs, "avg_dirty_ratio"));
            dto.setAvgPinnedBuffers(getDouble(rs, "avg_pinned_buffers"));
            dto.setTotalHeapBlksRead(getLong(rs, "total_heap_blks_read"));
            dto.setTotalHeapBlksHit(getLong(rs, "total_heap_blks_hit"));
            dto.setTotalIdxBlksRead(getLong(rs, "total_idx_blks_read"));
            dto.setTotalIdxBlksHit(getLong(rs, "total_idx_blks_hit"));
            dto.setAvgCacheHitRatio(getDouble(rs, "avg_cache_hit_ratio"));
            dto.setAvgUsagecount(getDouble(rs, "avg_usagecount"));
            dto.setAvgBufferReuseScore(getDouble(rs, "avg_buffer_reuse_score"));
            dto.setDatabaseName(rs.getString("database_name"));
            dto.setTotalTempFiles(getLong(rs, "total_temp_files"));
            dto.setTotalTempBytes(getLong(rs, "total_temp_bytes"));
            dto.setAvgTempFileRate(getDouble(rs, "avg_temp_file_rate"));
            dto.setAvgTempBytesPerSec(getDouble(rs, "avg_temp_bytes_per_sec"));
            dto.setTotalBlkReadTime(getDouble(rs, "total_blk_read_time"));
            dto.setTotalBlkWriteTime(getDouble(rs, "total_blk_write_time"));
            dto.setAvgIoWaitTimeMs(getDouble(rs, "avg_io_wait_time_ms"));
            dto.setRecordCount(getLong(rs, "record_count"));

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



