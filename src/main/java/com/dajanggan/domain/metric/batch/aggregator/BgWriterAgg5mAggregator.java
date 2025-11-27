package com.dajanggan.domain.metric.batch.aggregator;

import com.dajanggan.domain.engine.bgwriter.domain.BgWriterAgg5m;
import com.dajanggan.domain.engine.bgwriter.dto.agg5m.BgWriterAgg5mDto;
import com.dajanggan.domain.engine.bgwriter.repository.BgWriterMapper;
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
 * BGWriter 5분 집계 Batch Job
 * bgwriter_agg_1m 테이블에서 5분간 데이터를 읽어서
 * bgwriter_agg_5m에 저장
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class BgWriterAgg5mAggregator {

    private final JobRepository jobRepository;
    private final DataSource dataSource;
    private final PlatformTransactionManager batchTransactionManager;
    private final BgWriterMapper bgWriterMapper;

    @Bean
    public Job bgWriterAgg5mJob() {
        return new JobBuilder("bgWriterAgg5mJob", jobRepository)
                .start(bgWriterAgg5mStep())
                .build();
    }

    @Bean
    public Step bgWriterAgg5mStep() {
        return new StepBuilder("bgWriterAgg5mStep", jobRepository)
                .<BgWriterAgg5mDto, BgWriterAgg5m>chunk(100, batchTransactionManager)
                .reader(bgWriterAgg5mReader())
                .processor(bgWriterAgg5mProcessor())
                .writer(bgWriterAgg5mWriter())
                .build();
    }

    @Bean
    public JdbcCursorItemReader<BgWriterAgg5mDto> bgWriterAgg5mReader() {
        String sql = """
            SELECT
                DATE_TRUNC('hour', collected_at) + 
                    INTERVAL '5 minutes' * FLOOR(EXTRACT(MINUTE FROM collected_at) / 5) 
                    as time_bucket,
                instance_id,
                ROUND(AVG(avg_backend_flush_ratio) FILTER (WHERE avg_backend_flush_ratio IS NOT NULL)::numeric, 2) as avg_backend_flush_ratio,
                ROUND(AVG(avg_clean_rate) FILTER (WHERE avg_clean_rate > 0)::numeric, 2) as avg_clean_rate,
                SUM(total_buffers_clean) as total_buffers_clean,
                SUM(total_buffers_backend) as total_buffers_backend,
                SUM(total_backend_fsync) as total_backend_fsync,
                SUM(total_maxwritten_clean) as total_maxwritten_clean,
                ROUND(AVG(avg_cycle_time_ms) FILTER (WHERE avg_cycle_time_ms IS NOT NULL)::numeric, 2) as avg_cycle_time_ms,
                CASE
                    WHEN AVG(avg_backend_flush_ratio) FILTER (WHERE avg_backend_flush_ratio IS NOT NULL) > 30 OR 
                         SUM(total_maxwritten_clean) > 5 THEN '위험'
                    WHEN AVG(avg_backend_flush_ratio) FILTER (WHERE avg_backend_flush_ratio IS NOT NULL) > 10 OR 
                         SUM(total_maxwritten_clean) > 0 THEN '주의'
                    ELSE '정상'
                END as status,
                COUNT(*) as record_count
            FROM bgwriter_agg_1m
            WHERE collected_at >= DATE_TRUNC('minute', CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - INTERVAL '6 minutes'
              AND collected_at < DATE_TRUNC('minute', CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - INTERVAL '1 minute'
            GROUP BY time_bucket, instance_id
            ORDER BY instance_id, time_bucket
            """;

        return new JdbcCursorItemReaderBuilder<BgWriterAgg5mDto>()
                .name("bgWriterAgg5mReader")
                .dataSource(dataSource)
                .sql(sql)
                .rowMapper(new BgWriterAgg5mDtoRowMapper())
                .build();
    }

    @Bean
    public ItemProcessor<BgWriterAgg5mDto, BgWriterAgg5m> bgWriterAgg5mProcessor() {
        return dto -> {
            if (dto.getRecordCount() == null || dto.getRecordCount() == 0) {
                return null;
            }

            // Backend Flush 비율 재계산
            long totalBuffers = (dto.getTotalBuffersClean() != null ? dto.getTotalBuffersClean() : 0L) +
                    (dto.getTotalBuffersBackend() != null ? dto.getTotalBuffersBackend() : 0L);
            double backendFlushRatio = 0.0;
            if (totalBuffers > 0) {
                backendFlushRatio = (100.0 * (dto.getTotalBuffersBackend() != null ? dto.getTotalBuffersBackend() : 0L)) / totalBuffers;
            }

            return BgWriterAgg5m.builder()
                    .instanceId(dto.getInstanceId())
                    .collectedAt(dto.getTimeBucket())
                    .avgBackendFlushRatio(backendFlushRatio)
                    .avgCleanRate(dto.getAvgCleanRate() != null ? dto.getAvgCleanRate() : 0.0)
                    .totalBuffersClean(dto.getTotalBuffersClean() != null ? dto.getTotalBuffersClean() : 0L)
                    .totalBuffersBackend(dto.getTotalBuffersBackend() != null ? dto.getTotalBuffersBackend() : 0L)
                    .totalBackendFsync(dto.getTotalBackendFsync() != null ? dto.getTotalBackendFsync() : 0L)
                    .totalMaxwrittenClean(dto.getTotalMaxwrittenClean() != null ? dto.getTotalMaxwrittenClean() : 0L)
                    .status(dto.getStatus() != null ? dto.getStatus() : "정상")
                    .avgCycleTimeMs(dto.getAvgCycleTimeMs())
                    .build();
        };
    }

    @Bean
    public ItemWriter<BgWriterAgg5m> bgWriterAgg5mWriter() {
        return (Chunk<? extends BgWriterAgg5m> chunk) -> {
            List<BgWriterAgg5m> items = new ArrayList<>();
            for (BgWriterAgg5m item : chunk.getItems()) {
                if (item != null) {
                    items.add(item);
                }
            }

            if (!items.isEmpty()) {
                for (BgWriterAgg5m agg5m : items) {
                    bgWriterMapper.insertAgg5m(agg5m);
                }
                log.info("BGWriter 5분 집계 완료: {} 건 저장", items.size());
            }
        };
    }

    private static class BgWriterAgg5mDtoRowMapper implements RowMapper<BgWriterAgg5mDto> {
        @Override
        public BgWriterAgg5mDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            BgWriterAgg5mDto dto = new BgWriterAgg5mDto();
            dto.setInstanceId(rs.getLong("instance_id"));

            java.sql.Timestamp timeBucketTs = rs.getTimestamp("time_bucket");
            if (timeBucketTs != null) {
                dto.setTimeBucket(OffsetDateTime.ofInstant(
                        timeBucketTs.toInstant(),
                        ZoneOffset.UTC
                ));
            }

            dto.setAvgBackendFlushRatio(getDouble(rs, "avg_backend_flush_ratio"));
            dto.setAvgCleanRate(getDouble(rs, "avg_clean_rate"));
            dto.setTotalBuffersClean(getLong(rs, "total_buffers_clean"));
            dto.setTotalBuffersBackend(getLong(rs, "total_buffers_backend"));
            dto.setTotalBackendFsync(getLong(rs, "total_backend_fsync"));
            dto.setTotalMaxwrittenClean(getLong(rs, "total_maxwritten_clean"));
            dto.setStatus(rs.getString("status"));
            dto.setAvgCycleTimeMs(getDouble(rs, "avg_cycle_time_ms"));
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



