package com.dajanggan.domain.metric.batch.aggregator;

import com.dajanggan.domain.engine.checkpoint.domain.CheckpointAgg5m;
import com.dajanggan.domain.engine.checkpoint.dto.agg5m.CheckpointAgg5mDto;
import com.dajanggan.domain.engine.checkpoint.repository.CheckpointMapper;
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
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Checkpoint 5분 집계 Batch Job
 * checkpoint_agg_1m 테이블에서 5분간 데이터를 읽어서
 * checkpoint_agg_5m에 저장
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CheckpointAgg5mAggregator {

    private final JobRepository jobRepository;
    private final DataSource dataSource;
    private final PlatformTransactionManager batchTransactionManager;
    private final CheckpointMapper checkpointMapper;

    @Bean
    public Job checkpointAgg5mJob() {
        return new JobBuilder("checkpointAgg5mJob", jobRepository)
                .start(checkpointAgg5mStep())
                .build();
    }

    @Bean
    public Step checkpointAgg5mStep() {
        return new StepBuilder("checkpointAgg5mStep", jobRepository)
                .<CheckpointAgg5mDto, CheckpointAgg5m>chunk(100, batchTransactionManager)
                .reader(checkpointAgg5mReader())
                .processor(checkpointAgg5mProcessor())
                .writer(checkpointAgg5mWriter())
                .build();
    }

    @Bean
    public JdbcCursorItemReader<CheckpointAgg5mDto> checkpointAgg5mReader() {
        String sql = """
            SELECT
                DATE_TRUNC('hour', collected_at) + 
                    INTERVAL '5 minutes' * FLOOR(EXTRACT(MINUTE FROM collected_at) / 5) 
                    as time_bucket,
                instance_id,
                ROUND(AVG(avg_checkpoint_req_ratio)::numeric, 2) as avg_checkpoint_req_ratio,
                ROUND(AVG(avg_write_time) FILTER (WHERE avg_write_time > 0)::numeric, 2) as avg_write_time,
                ROUND(AVG(avg_sync_time) FILTER (WHERE avg_sync_time > 0)::numeric, 2) as avg_sync_time,
                ROUND(AVG(avg_total_time) FILTER (WHERE avg_total_time > 0)::numeric, 2) as avg_total_time,
                SUM(total_checkpoints_timed) as total_checkpoints_timed,
                SUM(total_checkpoints_req) as total_checkpoints_req,
                SUM(total_wal_bytes) as total_wal_bytes,
                SUM(total_buffers_checkpoint) as total_buffers_checkpoint,
                CASE
                    WHEN AVG(avg_total_time) FILTER (WHERE avg_total_time > 0) > 5.0 OR 
                         (SUM(total_checkpoints_req)::float / NULLIF(SUM(total_checkpoints_timed) + SUM(total_checkpoints_req), 0) * 100) > 30 THEN '위험'
                    WHEN AVG(avg_total_time) FILTER (WHERE avg_total_time > 0) > 1.0 OR 
                         (SUM(total_checkpoints_req)::float / NULLIF(SUM(total_checkpoints_timed) + SUM(total_checkpoints_req), 0) * 100) > 10 THEN '주의'
                    ELSE '정상'
                END as status,
                COUNT(*) as record_count
            FROM checkpoint_agg_1m
            WHERE collected_at >= DATE_TRUNC('minute', CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - INTERVAL '6 minutes'
              AND collected_at < DATE_TRUNC('minute', CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - INTERVAL '1 minute'
            GROUP BY time_bucket, instance_id
            ORDER BY instance_id, time_bucket
            """;

        return new JdbcCursorItemReaderBuilder<CheckpointAgg5mDto>()
                .name("checkpointAgg5mReader")
                .dataSource(dataSource)
                .sql(sql)
                .rowMapper(new CheckpointAgg5mDtoRowMapper())
                .build();
    }

    @Bean
    public ItemProcessor<CheckpointAgg5mDto, CheckpointAgg5m> checkpointAgg5mProcessor() {
        return dto -> {
            if (dto.getRecordCount() == null || dto.getRecordCount() == 0) {
                return null;
            }

            // 요청형 체크포인트 비율 재계산
            long totalCheckpoints = (dto.getTotalCheckpointsTimed() != null ? dto.getTotalCheckpointsTimed() : 0L) +
                    (dto.getTotalCheckpointsReq() != null ? dto.getTotalCheckpointsReq() : 0L);
            double reqRatio = 0.0;
            if (totalCheckpoints > 0) {
                reqRatio = (100.0 * (dto.getTotalCheckpointsReq() != null ? dto.getTotalCheckpointsReq() : 0L)) / totalCheckpoints;
            }

            return CheckpointAgg5m.builder()
                    .instanceId(dto.getInstanceId())
                    .collectedAt(dto.getTimeBucket())
                    .avgCheckpointReqRatio(reqRatio)
                    .avgWriteTime(dto.getAvgWriteTime() != null ? dto.getAvgWriteTime() : 0.0)
                    .avgSyncTime(dto.getAvgSyncTime() != null ? dto.getAvgSyncTime() : 0.0)
                    .avgTotalTime(dto.getAvgTotalTime() != null ? dto.getAvgTotalTime() : 0.0)
                    .totalCheckpointsTimed(dto.getTotalCheckpointsTimed() != null ? dto.getTotalCheckpointsTimed() : 0L)
                    .totalCheckpointsReq(dto.getTotalCheckpointsReq() != null ? dto.getTotalCheckpointsReq() : 0L)
                    .totalWalBytes(dto.getTotalWalBytes() != null ? dto.getTotalWalBytes() : BigDecimal.ZERO)
                    .totalBuffersCheckpoint(dto.getTotalBuffersCheckpoint() != null ? dto.getTotalBuffersCheckpoint() : 0L)
                    .status(dto.getStatus() != null ? dto.getStatus() : "정상")
                    .build();
        };
    }

    @Bean
    public ItemWriter<CheckpointAgg5m> checkpointAgg5mWriter() {
        return (Chunk<? extends CheckpointAgg5m> chunk) -> {
            List<CheckpointAgg5m> items = new ArrayList<>();
            for (CheckpointAgg5m item : chunk.getItems()) {
                if (item != null) {
                    items.add(item);
                }
            }

            if (!items.isEmpty()) {
                for (CheckpointAgg5m agg5m : items) {
                    checkpointMapper.insertAgg5m(agg5m);
                }
                log.info("Checkpoint 5분 집계 완료: {} 건 저장", items.size());
            }
        };
    }

    private static class CheckpointAgg5mDtoRowMapper implements RowMapper<CheckpointAgg5mDto> {
        @Override
        public CheckpointAgg5mDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            CheckpointAgg5mDto dto = new CheckpointAgg5mDto();
            dto.setInstanceId(rs.getLong("instance_id"));

            java.sql.Timestamp timeBucketTs = rs.getTimestamp("time_bucket");
            if (timeBucketTs != null) {
                dto.setTimeBucket(OffsetDateTime.ofInstant(
                        timeBucketTs.toInstant(),
                        ZoneOffset.UTC
                ));
            }

            dto.setAvgCheckpointReqRatio(getDouble(rs, "avg_checkpoint_req_ratio"));
            dto.setAvgWriteTime(getDouble(rs, "avg_write_time"));
            dto.setAvgSyncTime(getDouble(rs, "avg_sync_time"));
            dto.setAvgTotalTime(getDouble(rs, "avg_total_time"));
            dto.setTotalCheckpointsTimed(getLong(rs, "total_checkpoints_timed"));
            dto.setTotalCheckpointsReq(getLong(rs, "total_checkpoints_req"));

            java.math.BigDecimal totalWalBytes = rs.getBigDecimal("total_wal_bytes");
            dto.setTotalWalBytes(totalWalBytes != null ? totalWalBytes : BigDecimal.ZERO);

            dto.setTotalBuffersCheckpoint(getLong(rs, "total_buffers_checkpoint"));
            dto.setStatus(rs.getString("status"));
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


