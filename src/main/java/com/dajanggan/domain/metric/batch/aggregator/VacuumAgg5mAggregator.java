package com.dajanggan.domain.metric.batch.aggregator;

import com.dajanggan.domain.vacuum.dto.agg.VacuumAgg5mDto;
import com.dajanggan.domain.vacuum.repository.VacuumAgg5mMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
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
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class VacuumAgg5mAggregator {

    private final DataSource dataSource;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager batchTransactionManager;
    private final VacuumAgg5mMapper agg5mMapper;

    @Bean
    public Job vacuumAgg5mJob() {
        return new JobBuilder("vacuumAgg5mJob", jobRepository)
                .start(vacuumAgg5mStep())
                .build();
    }

    @Bean
    public Step vacuumAgg5mStep() {
        return new StepBuilder("vacuumAgg5mStep", jobRepository)
                .<VacuumAgg5mDto, VacuumAgg5mDto>chunk(100, batchTransactionManager)
                .reader(vacuumAgg5mReader())
                .processor(vacuumAgg5mProcessor())
                .writer(vacuumAgg5mWriter())
                .build();
    }

    @Bean
    public JdbcCursorItemReader<VacuumAgg5mDto> vacuumAgg5mReader() {
        String sql = """
    WITH agg_5m AS (
        SELECT 
            database_id,
            instance_id,
            DATE_TRUNC('hour', collected_at) + 
                INTERVAL '5 minutes' * FLOOR(EXTRACT(MINUTE FROM collected_at) / 5) as collected_at,
            AVG(total_vacuum_sessions) as total_vacuum_sessions,
            AVG(active_vacuum_sessions) as active_vacuum_sessions,
            AVG(autovacuum_sessions) as autovacuum_sessions,
            AVG(manual_vacuum_sessions) as manual_vacuum_sessions,
            AVG(avg_dead_tuples) as avg_dead_tuples,
            MAX(max_dead_tuples) as max_dead_tuples,
            SUM(total_dead_tuples) as total_dead_tuples,
            AVG(avg_progress) as avg_progress,
            AVG(tables_with_dead_tuples) as tables_with_dead_tuples,
            AVG(tables_being_vacuumed) as tables_being_vacuumed,
            AVG(avg_elapsed_seconds) as avg_elapsed_seconds,
            MAX(max_elapsed_seconds) as max_elapsed_seconds,
            AVG(dead_tuple_increase_rate) as dead_tuple_increase_rate,
            AVG(dead_tuple_decrease_rate) as dead_tuple_decrease_rate,
            AVG(net_dead_tuple_change) as net_dead_tuple_change,
            AVG(avg_cost_delay_ms) as avg_cost_delay_ms,
            AVG(worker_utilization_pct) as worker_utilization_pct,
            MAX(max_workers_configured) as max_workers_configured,
            COALESCE(AVG(avg_bloat_bytes), 0)::BIGINT as avg_bloat_bytes,
            COALESCE(MAX(max_bloat_bytes), 0) as max_bloat_bytes,
            COALESCE(SUM(total_bloat_bytes), 0) as total_bloat_bytes,
            COALESCE(AVG(avg_bloat_ratio), 0) as avg_bloat_ratio,
            COALESCE(MAX(max_bloat_ratio), 0) as max_bloat_ratio,
            SUM(critical_bloat_tables) as critical_bloat_tables,
            SUM(total_table_size_bytes) as total_table_size_bytes,
            SUM(blocked_vacuum_count) as blocked_vacuum_count,
            AVG(avg_blocked_seconds) as avg_blocked_seconds,
            MAX(max_blocked_seconds) as max_blocked_seconds
        FROM vacuum_metrics_agg_1m
       WHERE collected_at >= DATE_TRUNC('minute', NOW()) - INTERVAL '5 minutes'
        AND collected_at < DATE_TRUNC('minute', NOW())
        GROUP BY database_id, instance_id, 
                 DATE_TRUNC('hour', collected_at) + 
                 INTERVAL '5 minutes' * FLOOR(EXTRACT(MINUTE FROM collected_at) / 5)
    ),
    top_tables AS (  
        SELECT 
            database_id,
            instance_id,
            DATE_TRUNC('hour', collected_at) + 
                INTERVAL '5 minutes' * FLOOR(EXTRACT(MINUTE FROM collected_at) / 5) as collected_at,
            table_name,
            n_dead_tup,
            ROW_NUMBER() OVER (
                PARTITION BY database_id, instance_id,
                             DATE_TRUNC('hour', collected_at) + 
                             INTERVAL '5 minutes' * FLOOR(EXTRACT(MINUTE FROM collected_at) / 5)
                ORDER BY n_dead_tup DESC
            ) as rank
        FROM vacuum_raw_metrics
       WHERE collected_at >= DATE_TRUNC('minute', NOW()) - INTERVAL '5 minutes'
            AND collected_at < DATE_TRUNC('minute', NOW())
          AND n_dead_tup > 0
    ),
    top_bloat_tables AS (
        SELECT 
            database_id,
            instance_id,
            DATE_TRUNC('hour', collected_at) + 
                INTERVAL '5 minutes' * FLOOR(EXTRACT(MINUTE FROM collected_at) / 5) as collected_at,
            table_name,
            relsize_total_bytes,
            ROW_NUMBER() OVER (
                PARTITION BY database_id, instance_id,
                             DATE_TRUNC('hour', collected_at) + 
                             INTERVAL '5 minutes' * FLOOR(EXTRACT(MINUTE FROM collected_at) / 5)
                ORDER BY relsize_total_bytes DESC
            ) as rank
        FROM vacuum_raw_metrics
       WHERE collected_at >= DATE_TRUNC('minute', NOW()) - INTERVAL '5 minutes'
          AND collected_at < DATE_TRUNC('minute', NOW())
          AND relsize_total_bytes > 0
    ),
    top_blocker_tables AS (
        SELECT 
            database_id,
            instance_id,
            DATE_TRUNC('hour', collected_at) + 
                INTERVAL '5 minutes' * FLOOR(EXTRACT(MINUTE FROM collected_at) / 5) as collected_at,
            table_name,
            blocked_seconds,
            ROW_NUMBER() OVER (
                PARTITION BY database_id, instance_id,
                             DATE_TRUNC('hour', collected_at) + 
                             INTERVAL '5 minutes' * FLOOR(EXTRACT(MINUTE FROM collected_at) / 5)
                ORDER BY blocked_seconds DESC
            ) as rank
        FROM vacuum_raw_metrics
       WHERE collected_at >= DATE_TRUNC('minute', NOW()) - INTERVAL '5 minutes'
          AND collected_at < DATE_TRUNC('minute', NOW())
          AND blocker_pid IS NOT NULL
          AND blocked_seconds > 0
    )
    SELECT 
        a.database_id,
        a.instance_id,
        a.collected_at,
        a.total_vacuum_sessions,
        a.active_vacuum_sessions,
        a.autovacuum_sessions,
        a.manual_vacuum_sessions,
        a.avg_dead_tuples,
        a.max_dead_tuples,
        a.total_dead_tuples,
        a.avg_progress,
        a.tables_with_dead_tuples,
        a.tables_being_vacuumed,
        a.avg_elapsed_seconds,
        a.max_elapsed_seconds,
        a.dead_tuple_increase_rate,
        a.dead_tuple_decrease_rate,
        a.net_dead_tuple_change,
        a.avg_cost_delay_ms,
        a.worker_utilization_pct,
        a.max_workers_configured,
        a.avg_bloat_bytes,
        a.max_bloat_bytes,
        a.total_bloat_bytes,
        a.avg_bloat_ratio,
        a.max_bloat_ratio,
        a.critical_bloat_tables,
        a.total_table_size_bytes,
        a.blocked_vacuum_count,
        a.avg_blocked_seconds,
        a.max_blocked_seconds,
        MAX(CASE WHEN t.rank = 1 THEN t.table_name END) as top_table_1,
        MAX(CASE WHEN t.rank = 1 THEN t.n_dead_tup END) as top_table_1_dead_tuples,
        MAX(CASE WHEN t.rank = 2 THEN t.table_name END) as top_table_2,
        MAX(CASE WHEN t.rank = 2 THEN t.n_dead_tup END) as top_table_2_dead_tuples,
        MAX(CASE WHEN t.rank = 3 THEN t.table_name END) as top_table_3,
        MAX(CASE WHEN t.rank = 3 THEN t.n_dead_tup END) as top_table_3_dead_tuples,
        MAX(CASE WHEN t.rank = 4 THEN t.table_name END) as top_table_4,
        MAX(CASE WHEN t.rank = 4 THEN t.n_dead_tup END) as top_table_4_dead_tuples,
        MAX(CASE WHEN t.rank = 5 THEN t.table_name END) as top_table_5,
        MAX(CASE WHEN t.rank = 5 THEN t.n_dead_tup END) as top_table_5_dead_tuples,
        MAX(CASE WHEN b.rank = 1 THEN b.table_name END) as top_bloat_table_1,
        MAX(CASE WHEN b.rank = 1 THEN b.relsize_total_bytes END) as top_bloat_table_1_bytes,
        MAX(CASE WHEN b.rank = 2 THEN b.table_name END) as top_bloat_table_2,
        MAX(CASE WHEN b.rank = 2 THEN b.relsize_total_bytes END) as top_bloat_table_2_bytes,
        MAX(CASE WHEN b.rank = 3 THEN b.table_name END) as top_bloat_table_3,
        MAX(CASE WHEN b.rank = 3 THEN b.relsize_total_bytes END) as top_bloat_table_3_bytes,
        MAX(CASE WHEN b.rank = 4 THEN b.table_name END) as top_bloat_table_4,
        MAX(CASE WHEN b.rank = 4 THEN b.relsize_total_bytes END) as top_bloat_table_4_bytes,
        MAX(CASE WHEN b.rank = 5 THEN b.table_name END) as top_bloat_table_5,
        MAX(CASE WHEN b.rank = 5 THEN b.relsize_total_bytes END) as top_bloat_table_5_bytes,
        MAX(CASE WHEN bl.rank = 1 THEN bl.table_name END) as top_blocker_table_1,
        MAX(CASE WHEN bl.rank = 1 THEN bl.blocked_seconds END) as top_blocker_table_1_seconds,
        MAX(CASE WHEN bl.rank = 2 THEN bl.table_name END) as top_blocker_table_2,
        MAX(CASE WHEN bl.rank = 2 THEN bl.blocked_seconds END) as top_blocker_table_2_seconds,
        MAX(CASE WHEN bl.rank = 3 THEN bl.table_name END) as top_blocker_table_3,
        MAX(CASE WHEN bl.rank = 3 THEN bl.blocked_seconds END) as top_blocker_table_3_seconds
    FROM agg_5m a
    LEFT JOIN top_tables t ON a.database_id = t.database_id 
        AND a.instance_id = t.instance_id
        AND a.collected_at = t.collected_at
    LEFT JOIN top_bloat_tables b ON a.database_id = b.database_id 
        AND a.instance_id = b.instance_id
        AND a.collected_at = b.collected_at
    LEFT JOIN top_blocker_tables bl ON a.database_id = bl.database_id 
        AND a.instance_id = bl.instance_id
        AND a.collected_at = bl.collected_at
    GROUP BY 
        a.database_id, a.instance_id, a.collected_at,
        a.total_vacuum_sessions, a.active_vacuum_sessions,
        a.autovacuum_sessions, a.manual_vacuum_sessions,
        a.avg_dead_tuples, a.max_dead_tuples, a.total_dead_tuples,
        a.avg_progress, a.tables_with_dead_tuples, a.tables_being_vacuumed,
        a.avg_elapsed_seconds, a.max_elapsed_seconds,
        a.dead_tuple_increase_rate, a.dead_tuple_decrease_rate, a.net_dead_tuple_change,
        a.avg_cost_delay_ms, a.worker_utilization_pct, a.max_workers_configured,
        a.avg_bloat_bytes, a.max_bloat_bytes, a.total_bloat_bytes,
        a.avg_bloat_ratio, a.max_bloat_ratio, a.critical_bloat_tables, a.total_table_size_bytes,
        a.blocked_vacuum_count, a.avg_blocked_seconds, a.max_blocked_seconds
    ORDER BY a.database_id, a.instance_id, a.collected_at
    """;

        return new JdbcCursorItemReaderBuilder<VacuumAgg5mDto>()
                .name("vacuumAgg5mReader")
                .dataSource(dataSource)
                .sql(sql)
                .rowMapper(new VacuumAgg5mDtoRowMapper())
                .build();
    }

    @Bean
    public ItemProcessor<VacuumAgg5mDto, VacuumAgg5mDto> vacuumAgg5mProcessor() {
        return item -> {
            item.setCreatedAt(OffsetDateTime.now());

            if (item.getAvgProgress() == null) item.setAvgProgress(0.0);
            if (item.getAvgElapsedSeconds() == null) item.setAvgElapsedSeconds(0.0);
            if (item.getMaxElapsedSeconds() == null) item.setMaxElapsedSeconds(0.0);
            if (item.getDeadTupleIncreaseRate() == null) item.setDeadTupleIncreaseRate(0L);
            if (item.getDeadTupleDecreaseRate() == null) item.setDeadTupleDecreaseRate(0L);
            if (item.getNetDeadTupleChange() == null) item.setNetDeadTupleChange(0L);
            if (item.getAvgCostDelayMs() == null) item.setAvgCostDelayMs(0.0);
            if (item.getWorkerUtilizationPct() == null) item.setWorkerUtilizationPct(0.0);
            if (item.getMaxWorkersConfigured() == null) item.setMaxWorkersConfigured(0);
            if (item.getAvgBloatBytes() == null) item.setAvgBloatBytes(0L);
            if (item.getMaxBloatBytes() == null) item.setMaxBloatBytes(0L);
            if (item.getTotalBloatBytes() == null) item.setTotalBloatBytes(0L);
            if (item.getAvgBloatRatio() == null) item.setAvgBloatRatio(0.0);
            if (item.getMaxBloatRatio() == null) item.setMaxBloatRatio(0.0);
            if (item.getCriticalBloatTables() == null) item.setCriticalBloatTables(0);
            if (item.getTotalTableSizeBytes() == null) item.setTotalTableSizeBytes(0L);
            if (item.getBlockedVacuumCount() == null) item.setBlockedVacuumCount(0);
            if (item.getAvgBlockedSeconds() == null) item.setAvgBlockedSeconds(0.0);
            if (item.getMaxBlockedSeconds() == null) item.setMaxBlockedSeconds(0);

            return item;
        };
    }


    @Bean
    public ItemWriter<VacuumAgg5mDto> vacuumAgg5mWriter() {
        return chunk -> {
            List<? extends VacuumAgg5mDto> items = chunk.getItems();
            if (items.isEmpty()) return;

            try {
                // MyBatis Mapper 사용
                agg5mMapper.insertAgg5mBatch(items);
                log.info("📊 [VACUUM AGG 5M] ✅ {} 건 저장 완료", items.size());
            } catch (Exception e) {
                log.error("📊 [VACUUM AGG 5M] ❌ 저장 실패", e);
                throw new RuntimeException("Vacuum 5분 집계 저장 실패", e);
            }
        };
    }

    private String sqlString(String value) {
        return value == null ? "NULL" : "'" + value.replace("'", "''") + "'";
    }

    private String sqlLong(Long value) {
        return value == null ? "NULL" : value.toString();
    }

    private String sqlInt(Integer value) {
        return value == null ? "NULL" : value.toString();
    }

    private static class VacuumAgg5mDtoRowMapper implements RowMapper<VacuumAgg5mDto> {
        @Override
        public VacuumAgg5mDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            return VacuumAgg5mDto.builder()
                    .databaseId(rs.getLong("database_id"))
                    .instanceId(rs.getLong("instance_id"))
                    .collectedAt(rs.getObject("collected_at", OffsetDateTime.class))
                    .totalVacuumSessions(rs.getInt("total_vacuum_sessions"))
                    .activeVacuumSessions(rs.getInt("active_vacuum_sessions"))
                    .autovacuumSessions(rs.getInt("autovacuum_sessions"))
                    .manualVacuumSessions(rs.getInt("manual_vacuum_sessions"))
                    .avgDeadTuples(rs.getLong("avg_dead_tuples"))
                    .maxDeadTuples(rs.getLong("max_dead_tuples"))
                    .totalDeadTuples(rs.getLong("total_dead_tuples"))
                    .avgProgress(rs.getDouble("avg_progress"))
                    .tablesWithDeadTuples(rs.getInt("tables_with_dead_tuples"))
                    .tablesBeingVacuumed(rs.getInt("tables_being_vacuumed"))
                    .avgElapsedSeconds(rs.getDouble("avg_elapsed_seconds"))
                    .maxElapsedSeconds(rs.getDouble("max_elapsed_seconds"))
                    .deadTupleIncreaseRate(rs.getLong("dead_tuple_increase_rate"))
                    .deadTupleDecreaseRate(rs.getLong("dead_tuple_decrease_rate"))
                    .netDeadTupleChange(rs.getLong("net_dead_tuple_change"))
                    .avgCostDelayMs(rs.getDouble("avg_cost_delay_ms"))
                    .workerUtilizationPct(rs.getDouble("worker_utilization_pct"))
                    .maxWorkersConfigured(rs.getInt("max_workers_configured"))
                    .avgBloatBytes(rs.getLong("avg_bloat_bytes"))
                    .maxBloatBytes(rs.getLong("max_bloat_bytes"))
                    .totalBloatBytes(rs.getLong("total_bloat_bytes"))
                    .avgBloatRatio(rs.getDouble("avg_bloat_ratio"))
                    .maxBloatRatio(rs.getDouble("max_bloat_ratio"))
                    .criticalBloatTables(rs.getInt("critical_bloat_tables"))
                    .totalTableSizeBytes(rs.getLong("total_table_size_bytes"))
                    .blockedVacuumCount(rs.getInt("blocked_vacuum_count"))
                    .avgBlockedSeconds(rs.getDouble("avg_blocked_seconds"))
                    .maxBlockedSeconds(rs.getInt("max_blocked_seconds"))
                    .topTable1(rs.getString("top_table_1"))
                    .topTable1DeadTuples((Long) rs.getObject("top_table_1_dead_tuples"))
                    .topTable2(rs.getString("top_table_2"))
                    .topTable2DeadTuples((Long) rs.getObject("top_table_2_dead_tuples"))
                    .topTable3(rs.getString("top_table_3"))
                    .topTable3DeadTuples((Long) rs.getObject("top_table_3_dead_tuples"))
                    .topTable4(rs.getString("top_table_4"))
                    .topTable4DeadTuples((Long) rs.getObject("top_table_4_dead_tuples"))
                    .topTable5(rs.getString("top_table_5"))
                    .topTable5DeadTuples((Long) rs.getObject("top_table_5_dead_tuples"))
                    .topBloatTable1(rs.getString("top_bloat_table_1"))
                    .topBloatTable1Bytes((Long) rs.getObject("top_bloat_table_1_bytes"))
                    .topBloatTable2(rs.getString("top_bloat_table_2"))
                    .topBloatTable2Bytes((Long) rs.getObject("top_bloat_table_2_bytes"))
                    .topBloatTable3(rs.getString("top_bloat_table_3"))
                    .topBloatTable3Bytes((Long) rs.getObject("top_bloat_table_3_bytes"))
                    .topBloatTable4(rs.getString("top_bloat_table_4"))
                    .topBloatTable4Bytes((Long) rs.getObject("top_bloat_table_4_bytes"))
                    .topBloatTable5(rs.getString("top_bloat_table_5"))
                    .topBloatTable5Bytes((Long) rs.getObject("top_bloat_table_5_bytes"))
                    .topBlockerTable1(rs.getString("top_blocker_table_1"))
                    .topBlockerTable1Seconds((Integer) rs.getObject("top_blocker_table_1_seconds"))
                    .topBlockerTable2(rs.getString("top_blocker_table_2"))
                    .topBlockerTable2Seconds((Integer) rs.getObject("top_blocker_table_2_seconds"))
                    .topBlockerTable3(rs.getString("top_blocker_table_3"))
                    .topBlockerTable3Seconds((Integer) rs.getObject("top_blocker_table_3_seconds"))
                    .build();
        }
    }
}