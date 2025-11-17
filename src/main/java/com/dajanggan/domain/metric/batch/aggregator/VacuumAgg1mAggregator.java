package com.dajanggan.domain.metric.batch.aggregator;

import com.dajanggan.domain.session.repository.SessionAgg5mRepository;
import com.dajanggan.domain.vacuum.dto.agg.VacuumAgg1mDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Vacuum 1분 집계 Batch Aggregator
 * vacuum_raw_metrics → vacuum_metrics_agg_1m
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class VacuumAgg1mAggregator {

    private final DataSource dataSource;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager batchTransactionManager;

    /**
     * Job 정의
     */
    @Bean
    public Job vacuumAgg1mJob() {
        return new JobBuilder("vacuumAgg1mJob", jobRepository)
                .start(vacuumAgg1mStep())
                .build();
    }

    /**
     * Step 정의
     */
    @Bean
    public Step vacuumAgg1mStep() {
        return new StepBuilder("vacuumAgg1mStep", jobRepository)
                .<VacuumAgg1mDto, VacuumAgg1mDto>chunk(100, batchTransactionManager)
                .reader(vacuumAgg1mReader())
                .processor(vacuumAgg1mProcessor())
                .writer(vacuumAgg1mWriter())
                .build();
    }

    /**
     * Reader: DB에서 이미 집계된 데이터 읽기 (GROUP BY 사용)
     * 지난 1~2분 사이 데이터를 database_id, instance_id별로 집계
     */
    @Bean
    public JdbcCursorItemReader<VacuumAgg1mDto> vacuumAgg1mReader() {
        String sql = """
        SELECT 
            database_id,
            instance_id,
            DATE_TRUNC('minute', collected_at) as collected_at,
            COUNT(*) as total_vacuum_sessions,
            COUNT(*) FILTER (WHERE session_phase IS NOT NULL 
                AND session_phase != 'not_running') as active_vacuum_sessions,
            COUNT(*) FILTER (WHERE autovacuum = true) as autovacuum_sessions,
            COUNT(*) FILTER (WHERE autovacuum = false OR autovacuum IS NULL) as manual_vacuum_sessions,
            AVG(n_dead_tup) as avg_dead_tuples,
            MAX(n_dead_tup) as max_dead_tuples,
            SUM(n_dead_tup) as total_dead_tuples,
            AVG(session_progress) FILTER (WHERE session_progress > 0) as avg_progress,
            COUNT(DISTINCT table_name) FILTER (WHERE n_dead_tup > 0) as tables_with_dead_tuples,
            COUNT(DISTINCT table_name) FILTER (WHERE session_phase IS NOT NULL 
                AND session_phase != 'not_running') as tables_being_vacuumed,
            AVG(elapsed_seconds) FILTER (WHERE elapsed_seconds IS NOT NULL) as avg_elapsed_seconds,
            MAX(elapsed_seconds) as max_elapsed_seconds
        FROM vacuum_raw_metrics
        WHERE collected_at >= NOW() - INTERVAL '2 minutes'
          AND collected_at < NOW() - INTERVAL '1 minute'
        GROUP BY database_id, instance_id, DATE_TRUNC('minute', collected_at)
        ORDER BY database_id, instance_id, collected_at
        """;

        return new JdbcCursorItemReaderBuilder<VacuumAgg1mDto>()
                .name("vacuumAgg1mReader")
                .dataSource(dataSource)
                .sql(sql)
                .rowMapper(new VacuumAgg1mDtoRowMapper())
                .build();
    }

    /**
     * Processor: 간단한 후처리
     */
    @Bean
    public ItemProcessor<VacuumAgg1mDto, VacuumAgg1mDto> vacuumAgg1mProcessor() {
        return item -> {
            // 생성 시간 설정
            item.setCreatedAt(OffsetDateTime.now());

            // null 값 처리
            if (item.getAvgProgress() == null) {
                item.setAvgProgress(0.0);
            }
            if (item.getAvgElapsedSeconds() == null) {
                item.setAvgElapsedSeconds(0.0);
            }
            if (item.getMaxElapsedSeconds() == null) {
                item.setMaxElapsedSeconds(0.0);
            }

            return item;
        };
    }

    /**
     * Writer: 집계 데이터 저장
     */
    @Bean
    public ItemWriter<VacuumAgg1mDto> vacuumAgg1mWriter() {
        return chunk -> {
            List<? extends VacuumAgg1mDto> items = chunk.getItems();
            if (items.isEmpty()) {
                return;
            }

            StringBuilder sql = new StringBuilder("""
                INSERT INTO vacuum_metrics_agg_1m (
                    database_id, instance_id, collected_at,
                    total_vacuum_sessions, active_vacuum_sessions, 
                    autovacuum_sessions, manual_vacuum_sessions,
                    avg_dead_tuples, max_dead_tuples, total_dead_tuples,
                    avg_progress, tables_with_dead_tuples, tables_being_vacuumed,
                    avg_elapsed_seconds, max_elapsed_seconds, created_at
                ) VALUES 
                """);

            for (int i = 0; i < items.size(); i++) {
                VacuumAgg1mDto item = items.get(i);
                sql.append(String.format(
                        "(%d, %d, '%s', %d, %d, %d, %d, %d, %d, %d, %.2f, %d, %d, %.2f, %.2f, NOW())",
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
                        item.getMaxElapsedSeconds()
                ));

                if (i < items.size() - 1) {
                    sql.append(", ");
                }
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
                    created_at = NOW()
                """);

            try (var connection = dataSource.getConnection();
                 var statement = connection.createStatement()) {

                statement.execute(sql.toString());
                log.info("Vacuum 1분 집계 데이터 {} 건 저장 완료", items.size());

            } catch (Exception e) {
                log.error("Vacuum 1분 집계 저장 실패", e);
                throw new RuntimeException("Vacuum 1분 집계 저장 실패", e);
            }
        };
    }

    /**
     * RowMapper
     */
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
                    .build();
        }
    }
}