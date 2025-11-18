package com.dajanggan.domain.metric.batch.aggregator;

import com.dajanggan.domain.vacuum.dto.agg.VacuumAgg1mDto;
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
public class VacuumAgg1mAggregator {

    private final DataSource dataSource;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager batchTransactionManager;

    @Bean
    public Job vacuumAgg1mJob() {
        return new JobBuilder("vacuumAgg1mJob", jobRepository)
                .start(vacuumAgg1mStep())
                .build();
    }

    @Bean
    public Step vacuumAgg1mStep() {
        return new StepBuilder("vacuumAgg1mStep", jobRepository)
                .<VacuumAgg1mDto, VacuumAgg1mDto>chunk(100, batchTransactionManager)
                .reader(vacuumAgg1mReader())
                .processor(vacuumAgg1mProcessor())
                .writer(vacuumAgg1mWriter())
                .build();
    }

    // VacuumAgg1mAggregator.java - Reader SQL 수정

    @Bean
    public JdbcCursorItemReader<VacuumAgg1mDto> vacuumAgg1mReader() {
        String sql = """
    WITH current_stats AS (
        SELECT 
            database_id,
            instance_id,
            DATE_TRUNC('minute', collected_at) as collected_at,
            
            -- ✅ 전체 세션 (dead tuple이 있는 테이블)
            COUNT(*) as total_vacuum_sessions,
            
            -- ✅ 실행 중인 세션만 카운트 (핵심 수정!)
            COUNT(*) FILTER (
                WHERE session_phase IS NOT NULL 
                AND session_phase != 'not_running'
                AND elapsed_seconds IS NOT NULL
                AND elapsed_seconds > 0
            ) as active_vacuum_sessions,
            
            -- ✅ Autovacuum 세션 (실행 중인 것만)
            COUNT(*) FILTER (
                WHERE autovacuum = true
                AND session_phase IS NOT NULL 
                AND session_phase != 'not_running'
            ) as autovacuum_sessions,
            
            -- ✅ Manual vacuum 세션 (실행 중인 것만)
            COUNT(*) FILTER (
                WHERE (autovacuum = false OR autovacuum IS NULL)
                AND session_phase IS NOT NULL 
                AND session_phase != 'not_running'
            ) as manual_vacuum_sessions,
            
            -- Dead Tuple 정보
            AVG(n_dead_tup) as avg_dead_tuples,
            MAX(n_dead_tup) as max_dead_tuples,
            SUM(n_dead_tup) as total_dead_tuples,
            
            -- 진행률 (실행 중인 세션만)
            AVG(session_progress) FILTER (
                WHERE session_progress > 0 
                AND session_phase != 'not_running'
            ) as avg_progress,
            
            -- 테이블 수
            COUNT(DISTINCT table_name) FILTER (WHERE n_dead_tup > 0) as tables_with_dead_tuples,
            COUNT(DISTINCT table_name) FILTER (
                WHERE session_phase IS NOT NULL 
                AND session_phase != 'not_running'
            ) as tables_being_vacuumed,
            
            -- ✅ 경과 시간 - 실행 중인 세션만, 비정상 값 제외 (핵심 수정!)
            AVG(elapsed_seconds) FILTER (
                WHERE elapsed_seconds IS NOT NULL
                AND elapsed_seconds > 0
                AND elapsed_seconds < 86400  -- 24시간 이상은 비정상
                AND session_phase IS NOT NULL
                AND session_phase != 'not_running'
            ) as avg_elapsed_seconds,
            
            MAX(elapsed_seconds) FILTER (
                WHERE elapsed_seconds > 0 
                AND elapsed_seconds < 86400
                AND session_phase != 'not_running'
            ) as max_elapsed_seconds,
            
            -- Bloat 계산
           COALESCE(AVG(bloat_bytes) FILTER (WHERE bloat_bytes > 0), 0)::BIGINT as avg_bloat_bytes,
           COALESCE(MAX(bloat_bytes), 0) as max_bloat_bytes,
           COALESCE(SUM(bloat_bytes), 0) as total_bloat_bytes,
           COALESCE(AVG(bloat_ratio) FILTER (WHERE bloat_ratio > 0), 0) as avg_bloat_ratio,
           COALESCE(MAX(bloat_ratio), 0) as max_bloat_ratio,
           COUNT(*) FILTER (WHERE bloat_ratio > 0.2) as critical_bloat_tables,
           SUM(relsize_total_bytes) as total_table_size_bytes,
            
            -- Worker 설정
            AVG(autovacuum_cost_delay_ms) FILTER (
                WHERE autovacuum_cost_delay_ms > 0
            ) as avg_cost_delay_ms,
            MAX(max_workers) as max_workers_configured,
            
            -- ✅ Worker 활용률 = 실행 중인 세션 / max_workers (수정!)
            CASE 
                WHEN MAX(max_workers) > 0 
                THEN (COUNT(*) FILTER (
                    WHERE session_phase IS NOT NULL 
                    AND session_phase != 'not_running'
                )::NUMERIC / MAX(max_workers)) * 100
                ELSE 0 
            END as worker_utilization_pct,
            
            -- Blocked 관련
            COUNT(*) FILTER (WHERE blocker_pid IS NOT NULL) as blocked_vacuum_count,
            AVG(blocked_seconds) FILTER (
                WHERE blocked_seconds > 0
            ) as avg_blocked_seconds,
            MAX(blocked_seconds) as max_blocked_seconds
            
        FROM vacuum_raw_metrics
      WHERE collected_at >= DATE_TRUNC('minute', NOW()) - INTERVAL '1 minute'
        AND collected_at < DATE_TRUNC('minute', NOW())
        GROUP BY database_id, instance_id, DATE_TRUNC('minute', collected_at)
    ),
    previous_stats AS (
        SELECT 
            database_id,
            instance_id,
            total_dead_tuples as prev_total_dead_tuples
        FROM vacuum_metrics_agg_1m
        WHERE collected_at = DATE_TRUNC('minute', NOW() - INTERVAL '1 minutes')
    )
    SELECT 
        cs.*,
        CASE 
            WHEN ps.prev_total_dead_tuples IS NOT NULL 
                 AND cs.total_dead_tuples > ps.prev_total_dead_tuples
            THEN cs.total_dead_tuples - ps.prev_total_dead_tuples
            ELSE 0 
        END as dead_tuple_increase_rate,
        CASE 
            WHEN ps.prev_total_dead_tuples IS NOT NULL 
                 AND cs.total_dead_tuples < ps.prev_total_dead_tuples
            THEN ps.prev_total_dead_tuples - cs.total_dead_tuples
            ELSE 0 
        END as dead_tuple_decrease_rate,
        CASE 
            WHEN ps.prev_total_dead_tuples IS NOT NULL
            THEN cs.total_dead_tuples - ps.prev_total_dead_tuples
            ELSE 0 
        END as net_dead_tuple_change
    FROM current_stats cs
    LEFT JOIN previous_stats ps 
        ON cs.database_id = ps.database_id 
        AND cs.instance_id = ps.instance_id
    ORDER BY cs.database_id, cs.instance_id, cs.collected_at
    """;

        return new JdbcCursorItemReaderBuilder<VacuumAgg1mDto>()
                .name("vacuumAgg1mReader")
                .dataSource(dataSource)
                .sql(sql)
                .rowMapper(new VacuumAgg1mDtoRowMapper())
                .build();
    }

    @Bean
    public ItemProcessor<VacuumAgg1mDto, VacuumAgg1mDto> vacuumAgg1mProcessor() {
        return item -> {
            item.setCreatedAt(OffsetDateTime.now());

            if (item.getAvgProgress() == null) item.setAvgProgress(0.0);
            if (item.getAvgElapsedSeconds() == null) item.setAvgElapsedSeconds(0.0);
            if (item.getMaxElapsedSeconds() == null) item.setMaxElapsedSeconds(0.0);
            if (item.getAvgBloatBytes() == null) item.setAvgBloatBytes(0L);
            if (item.getMaxBloatBytes() == null) item.setMaxBloatBytes(0L);
            if (item.getTotalBloatBytes() == null) item.setTotalBloatBytes(0L);
            if (item.getAvgBloatRatio() == null) item.setAvgBloatRatio(0.0);
            if (item.getMaxBloatRatio() == null) item.setMaxBloatRatio(0.0);
            if (item.getCriticalBloatTables() == null) item.setCriticalBloatTables(0);
            if (item.getTotalTableSizeBytes() == null) item.setTotalTableSizeBytes(0L);
            if (item.getAvgCostDelayMs() == null) item.setAvgCostDelayMs(0.0);
            if (item.getMaxWorkersConfigured() == null) item.setMaxWorkersConfigured(0);
            if (item.getWorkerUtilizationPct() == null) item.setWorkerUtilizationPct(0.0);
            if (item.getBlockedVacuumCount() == null) item.setBlockedVacuumCount(0);
            if (item.getAvgBlockedSeconds() == null) item.setAvgBlockedSeconds(0.0);
            if (item.getMaxBlockedSeconds() == null) item.setMaxBlockedSeconds(0);

            return item;
        };
    }

    @Bean
    public ItemWriter<VacuumAgg1mDto> vacuumAgg1mWriter() {
        return chunk -> {
            List<? extends VacuumAgg1mDto> items = chunk.getItems();
            if (items.isEmpty()) return;

            StringBuilder sql = new StringBuilder("""
                INSERT INTO vacuum_metrics_agg_1m (
                    database_id, instance_id, collected_at,
                    total_vacuum_sessions, active_vacuum_sessions, 
                    autovacuum_sessions, manual_vacuum_sessions,
                    avg_dead_tuples, max_dead_tuples, total_dead_tuples,
                    avg_progress, tables_with_dead_tuples, tables_being_vacuumed,
                    avg_elapsed_seconds, max_elapsed_seconds,
                    dead_tuple_increase_rate, dead_tuple_decrease_rate, net_dead_tuple_change,
                    avg_cost_delay_ms, worker_utilization_pct, max_workers_configured,
                    avg_bloat_bytes, max_bloat_bytes, total_bloat_bytes,
                    avg_bloat_ratio, max_bloat_ratio, critical_bloat_tables, total_table_size_bytes,
                    blocked_vacuum_count, avg_blocked_seconds, max_blocked_seconds,
                    created_at
                ) VALUES 
                """);

            for (int i = 0; i < items.size(); i++) {
                VacuumAgg1mDto item = items.get(i);
                sql.append(String.format(
                        "(%d, %d, '%s', %d, %d, %d, %d, %d, %d, %d, %.2f, %d, %d, %.2f, %.2f, %d, %d, %d, %.2f, %.2f, %d, %d, %d, %d, %.4f, %.4f, %d, %d, %d, %.2f, %d, NOW())",
                        item.getDatabaseId(),
                        item.getInstanceId(),
                        item.getCollectedAt(),
                        item.getTotalVacuumSessions(),
                        item.getActiveVacuumSessions(),
                        item.getAutovacuumSessions(),
                        item.getManualVacuumSessions(),
                        item.getAvgDeadTuples(),
                        item.getMaxDeadTuples(),
                        item.getTotalDeadTuples(),
                        item.getAvgProgress(),
                        item.getTablesWithDeadTuples(),
                        item.getTablesBeingVacuumed(),
                        item.getAvgElapsedSeconds(),
                        item.getMaxElapsedSeconds(),
                        item.getDeadTupleIncreaseRate(),
                        item.getDeadTupleDecreaseRate(),
                        item.getNetDeadTupleChange(),
                        item.getAvgCostDelayMs(),
                        item.getWorkerUtilizationPct(),
                        item.getMaxWorkersConfigured(),
                        item.getAvgBloatBytes(),
                        item.getMaxBloatBytes(),
                        item.getTotalBloatBytes(),
                        item.getAvgBloatRatio(),
                        item.getMaxBloatRatio(),
                        item.getCriticalBloatTables(),
                        item.getTotalTableSizeBytes(),
                        item.getBlockedVacuumCount(),
                        item.getAvgBlockedSeconds(),
                        item.getMaxBlockedSeconds()
                ));

                if (i < items.size() - 1) sql.append(", ");
            }

            sql.append("""
                ON CONFLICT (database_id, instance_id, collected_at) 
                DO UPDATE SET
                    total_vacuum_sessions = EXCLUDED.total_vacuum_sessions,
                    active_vacuum_sessions = EXCLUDED.active_vacuum_sessions,
                    autovacuum_sessions = EXCLUDED.autovacuum_sessions,
                    manual_vacuum_sessions = EXCLUDED.manual_vacuum_sessions,
                    avg_dead_tuples = EXCLUDED.avg_dead_tuples,
                    max_dead_tuples = EXCLUDED.max_dead_tuples,
                    total_dead_tuples = EXCLUDED.total_dead_tuples,
                    avg_progress = EXCLUDED.avg_progress,
                    tables_with_dead_tuples = EXCLUDED.tables_with_dead_tuples,
                    tables_being_vacuumed = EXCLUDED.tables_being_vacuumed,
                    avg_elapsed_seconds = EXCLUDED.avg_elapsed_seconds,
                    max_elapsed_seconds = EXCLUDED.max_elapsed_seconds,
                    dead_tuple_increase_rate = EXCLUDED.dead_tuple_increase_rate,
                    dead_tuple_decrease_rate = EXCLUDED.dead_tuple_decrease_rate,
                    net_dead_tuple_change = EXCLUDED.net_dead_tuple_change,
                    avg_cost_delay_ms = EXCLUDED.avg_cost_delay_ms,
                    worker_utilization_pct = EXCLUDED.worker_utilization_pct,
                    max_workers_configured = EXCLUDED.max_workers_configured,
                    avg_bloat_bytes = EXCLUDED.avg_bloat_bytes,
                    max_bloat_bytes = EXCLUDED.max_bloat_bytes,
                    total_bloat_bytes = EXCLUDED.total_bloat_bytes,
                    avg_bloat_ratio = EXCLUDED.avg_bloat_ratio,
                    max_bloat_ratio = EXCLUDED.max_bloat_ratio,
                    critical_bloat_tables = EXCLUDED.critical_bloat_tables,
                    total_table_size_bytes = EXCLUDED.total_table_size_bytes,
                    blocked_vacuum_count = EXCLUDED.blocked_vacuum_count,
                    avg_blocked_seconds = EXCLUDED.avg_blocked_seconds,
                    max_blocked_seconds = EXCLUDED.max_blocked_seconds,
                    created_at = NOW()
                """);

            try (var connection = dataSource.getConnection();
                 var statement = connection.createStatement()) {

                statement.execute(sql.toString());
                log.info("📊 [VACUUM AGG 1M] ✅ Vacuum 1분 집계 데이터 {} 건 저장 완료", items.size());

            } catch (Exception e) {
                log.error("📊 [VACUUM AGG 1M] ❌ 저장 실패", e);
                throw new RuntimeException("Vacuum 1분 집계 저장 실패", e);
            }
        };
    }

    private static class VacuumAgg1mDtoRowMapper implements RowMapper<VacuumAgg1mDto> {
        @Override
        public VacuumAgg1mDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            return VacuumAgg1mDto.builder()
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
                    .build();
        }
    }
}