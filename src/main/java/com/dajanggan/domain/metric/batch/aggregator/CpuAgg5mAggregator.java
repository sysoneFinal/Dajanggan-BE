package com.dajanggan.domain.metric.batch.aggregator;

import com.dajanggan.domain.system.cpu.domain.CpuAgg5m;
import com.dajanggan.domain.system.cpu.dto.agg5m.CpuAgg5mDto;
import com.dajanggan.domain.system.cpu.repository.CpuMapper;
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
 * CPU 5분 집계 Batch Job
 * cpu_agg_1m 테이블에서 5분간 데이터를 읽어서
 * cpu_agg_5m에 저장
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CpuAgg5mAggregator {

    private final JobRepository jobRepository;
    private final DataSource dataSource;
    private final PlatformTransactionManager batchTransactionManager;
    private final CpuMapper cpuMapper;

    @Bean
    public Job cpuAgg5mJob() {
        return new JobBuilder("cpuAgg5mJob", jobRepository)
                .start(cpuAgg5mStep())
                .build();
    }

    @Bean
    public Step cpuAgg5mStep() {
        return new StepBuilder("cpuAgg5mStep", jobRepository)
                .<CpuAgg5mDto, CpuAgg5m>chunk(100, batchTransactionManager)
                .reader(cpuAgg5mReader())
                .processor(cpuAgg5mProcessor())
                .writer(cpuAgg5mWriter())
                .build();
    }

    @Bean
    public JdbcCursorItemReader<CpuAgg5mDto> cpuAgg5mReader() {
        String sql = """
            SELECT
                DATE_TRUNC('hour', collected_at) + 
                    INTERVAL '5 minutes' * FLOOR(EXTRACT(MINUTE FROM collected_at) / 5) 
                    as time_bucket,
                instance_id,
                ROUND(AVG(avg_total_connections)::numeric, 2) as avg_total_connections,
                ROUND(AVG(avg_active_connections)::numeric, 2) as avg_active_connections,
                ROUND(AVG(avg_idle_connections)::numeric, 2) as avg_idle_connections,
                ROUND(AVG(avg_idle_in_transaction)::numeric, 2) as avg_idle_in_transaction,
                ROUND(AVG(avg_waiting_sessions)::numeric, 2) as avg_waiting_sessions,
                ROUND(AVG(avg_waiting_for_lock)::numeric, 2) as avg_waiting_for_lock,
                ROUND(AVG(avg_waiting_for_io)::numeric, 2) as avg_waiting_for_io,
                ROUND(AVG(avg_wait_event_client)::numeric, 2) as avg_wait_event_client,
                ROUND(AVG(avg_wait_event_activity)::numeric, 2) as avg_wait_event_activity,
                ROUND(AVG(avg_wait_event_bufferpin)::numeric, 2) as avg_wait_event_bufferpin,
                ROUND(AVG(avg_wait_event_lwlock)::numeric, 2) as avg_wait_event_lwlock,
                ROUND(AVG(avg_wait_event_timeout)::numeric, 2) as avg_wait_event_timeout,
                ROUND(AVG(avg_wait_event_ipc)::numeric, 2) as avg_wait_event_ipc,
                ROUND(AVG(avg_client_backend)::numeric, 2) as avg_client_backend,
                ROUND(AVG(avg_autovacuum_worker)::numeric, 2) as avg_autovacuum_worker,
                ROUND(AVG(avg_parallel_worker)::numeric, 2) as avg_parallel_worker,
                ROUND(AVG(avg_background_worker)::numeric, 2) as avg_background_worker,
                ROUND(AVG(avg_long_running_queries)::numeric, 2) as avg_long_running_queries,
                SUM(delta_xact_commit) as total_xact_commit,
                SUM(delta_xact_rollback) as total_xact_rollback,
                COUNT(*) as record_count
            FROM cpu_agg_1m
            WHERE collected_at >= DATE_TRUNC('minute', CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - INTERVAL '10 minutes'
              AND collected_at <= DATE_TRUNC('minute', CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
            GROUP BY time_bucket, instance_id
            ORDER BY instance_id, time_bucket
            """;

        return new JdbcCursorItemReaderBuilder<CpuAgg5mDto>()
                .name("cpuAgg5mReader")
                .dataSource(dataSource)
                .sql(sql)
                .rowMapper(new CpuAgg5mDtoRowMapper())
                .build();
    }

    @Bean
    public ItemProcessor<CpuAgg5mDto, CpuAgg5m> cpuAgg5mProcessor() {
        return dto -> {
            if (dto.getRecordCount() == null || dto.getRecordCount() == 0) {
                return null;
            }

            return CpuAgg5m.builder()
                    .instanceId(dto.getInstanceId())
                    .timeBucket(dto.getTimeBucket())
                    .avgTotalConnections(dto.getAvgTotalConnections() != null ? dto.getAvgTotalConnections().longValue() : 0L)
                    .avgActiveConnections(dto.getAvgActiveConnections() != null ? dto.getAvgActiveConnections().longValue() : 0L)
                    .avgIdleConnections(dto.getAvgIdleConnections() != null ? dto.getAvgIdleConnections().longValue() : 0L)
                    .avgIdleInTransaction(dto.getAvgIdleInTransaction() != null ? dto.getAvgIdleInTransaction().longValue() : 0L)
                    .avgWaitingSessions(dto.getAvgWaitingSessions() != null ? dto.getAvgWaitingSessions() : 0.0)
                    .avgWaitingForLock(dto.getAvgWaitingForLock() != null ? dto.getAvgWaitingForLock() : 0.0)
                    .avgWaitingForIo(dto.getAvgWaitingForIo() != null ? dto.getAvgWaitingForIo() : 0.0)
                    .avgWaitEventClient(dto.getAvgWaitEventClient() != null ? dto.getAvgWaitEventClient() : 0.0)
                    .avgWaitEventActivity(dto.getAvgWaitEventActivity() != null ? dto.getAvgWaitEventActivity() : 0.0)
                    .avgWaitEventBufferpin(dto.getAvgWaitEventBufferpin() != null ? dto.getAvgWaitEventBufferpin() : 0.0)
                    .avgWaitEventLwlock(dto.getAvgWaitEventLwlock() != null ? dto.getAvgWaitEventLwlock() : 0.0)
                    .avgWaitEventTimeout(dto.getAvgWaitEventTimeout() != null ? dto.getAvgWaitEventTimeout() : 0.0)
                    .avgWaitEventIpc(dto.getAvgWaitEventIpc() != null ? dto.getAvgWaitEventIpc() : 0.0)
                    .avgClientBackend(dto.getAvgClientBackend() != null ? dto.getAvgClientBackend() : 0.0)
                    .avgAutovacuumWorker(dto.getAvgAutovacuumWorker() != null ? dto.getAvgAutovacuumWorker() : 0.0)
                    .avgParallelWorker(dto.getAvgParallelWorker() != null ? dto.getAvgParallelWorker() : 0.0)
                    .avgBackgroundWorker(dto.getAvgBackgroundWorker() != null ? dto.getAvgBackgroundWorker() : 0.0)
                    .avgLongRunningQueries(dto.getAvgLongRunningQueries() != null ? dto.getAvgLongRunningQueries() : 0.0)
                    .totalXactCommit(dto.getTotalXactCommit() != null ? dto.getTotalXactCommit() : 0L)
                    .totalXactRollback(dto.getTotalXactRollback() != null ? dto.getTotalXactRollback() : 0L)
                    .recordCount(dto.getRecordCount() != null ? dto.getRecordCount() : 0L)
                    .build();
        };
    }

    @Bean
    public ItemWriter<CpuAgg5m> cpuAgg5mWriter() {
        return (Chunk<? extends CpuAgg5m> chunk) -> {
            List<CpuAgg5m> items = new ArrayList<>();
            for (CpuAgg5m item : chunk.getItems()) {
                if (item != null) {
                    items.add(item);
                }
            }

            if (!items.isEmpty()) {
                for (CpuAgg5m agg5m : items) {
                    cpuMapper.insertAgg5m(agg5m);
                }
                log.info("CPU 5분 집계 완료: {} 건 저장", items.size());
            }
        };
    }

    private static class CpuAgg5mDtoRowMapper implements RowMapper<CpuAgg5mDto> {
        @Override
        public CpuAgg5mDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            CpuAgg5mDto dto = new CpuAgg5mDto();
            dto.setInstanceId(rs.getLong("instance_id"));

            java.sql.Timestamp timeBucketTs = rs.getTimestamp("time_bucket");
            if (timeBucketTs != null) {
                dto.setTimeBucket(OffsetDateTime.ofInstant(
                        timeBucketTs.toInstant(),
                        ZoneOffset.UTC
                ));
            }

            dto.setAvgTotalConnections(getDouble(rs, "avg_total_connections"));
            dto.setAvgActiveConnections(getDouble(rs, "avg_active_connections"));
            dto.setAvgIdleConnections(getDouble(rs, "avg_idle_connections"));
            dto.setAvgIdleInTransaction(getDouble(rs, "avg_idle_in_transaction"));
            dto.setAvgWaitingSessions(getDouble(rs, "avg_waiting_sessions"));
            dto.setAvgWaitingForLock(getDouble(rs, "avg_waiting_for_lock"));
            dto.setAvgWaitingForIo(getDouble(rs, "avg_waiting_for_io"));
            dto.setAvgWaitEventClient(getDouble(rs, "avg_wait_event_client"));
            dto.setAvgWaitEventActivity(getDouble(rs, "avg_wait_event_activity"));
            dto.setAvgWaitEventBufferpin(getDouble(rs, "avg_wait_event_bufferpin"));
            dto.setAvgWaitEventLwlock(getDouble(rs, "avg_wait_event_lwlock"));
            dto.setAvgWaitEventTimeout(getDouble(rs, "avg_wait_event_timeout"));
            dto.setAvgWaitEventIpc(getDouble(rs, "avg_wait_event_ipc"));
            dto.setAvgClientBackend(getDouble(rs, "avg_client_backend"));
            dto.setAvgAutovacuumWorker(getDouble(rs, "avg_autovacuum_worker"));
            dto.setAvgParallelWorker(getDouble(rs, "avg_parallel_worker"));
            dto.setAvgBackgroundWorker(getDouble(rs, "avg_background_worker"));
            dto.setAvgLongRunningQueries(getDouble(rs, "avg_long_running_queries"));
            dto.setTotalXactCommit(getLong(rs, "total_xact_commit"));
            dto.setTotalXactRollback(getLong(rs, "total_xact_rollback"));
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