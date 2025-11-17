package com.dajanggan.domain.metric.batch.aggregator;

import com.dajanggan.domain.vacuum.dto.agg.VacuumAgg5mDto;
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
 * Vacuum 5분 집계 Batch Aggregator
 * vacuum_metrics_agg_1m → vacuum_metrics_agg_5m
 * + raw 데이터에서 Top 5 테이블 추출
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class VacuumAgg5mAggregator {

    private final DataSource dataSource;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager batchTransactionManager;

    /**
     * Job 정의
     */
    @Bean
    public Job vacuumAgg5mJob() {
        return new JobBuilder("vacuumAgg5mJob", jobRepository)
                .start(vacuumAgg5mStep())
                .build();
    }

    /**
     * Step 정의
     */
    @Bean
    public Step vacuumAgg5mStep() {
        return new StepBuilder("vacuumAgg5mStep", jobRepository)
                .<VacuumAgg5mDto, VacuumAgg5mDto>chunk(100, batchTransactionManager)
                .reader(vacuumAgg5mReader())
                .processor(vacuumAgg5mProcessor())
                .writer(vacuumAgg5mWriter())
                .build();
    }

    /**
     * Reader: 1분 집계 데이터를 5분 단위로 재집계
     * - 최근 5~10분 사이 데이터를 5분 단위로 묶음
     * - raw 데이터에서 Top 5 테이블 추출
     */
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
                    MAX(max_elapsed_seconds) as max_elapsed_seconds
                FROM vacuum_metrics_agg_1m
                WHERE collected_at >= NOW() - INTERVAL '10 minutes'
                  AND collected_at < NOW() - INTERVAL '5 minutes'
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
                WHERE collected_at >= NOW() - INTERVAL '10 minutes'
                  AND collected_at < NOW() - INTERVAL '5 minutes'
                  AND n_dead_tup > 0
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
                MAX(CASE WHEN t.rank = 1 THEN t.table_name END) as top_table_1,
                MAX(CASE WHEN t.rank = 1 THEN t.n_dead_tup END) as top_table_1_dead_tuples,
                MAX(CASE WHEN t.rank = 2 THEN t.table_name END) as top_table_2,
                MAX(CASE WHEN t.rank = 2 THEN t.n_dead_tup END) as top_table_2_dead_tuples,
                MAX(CASE WHEN t.rank = 3 THEN t.table_name END) as top_table_3,
                MAX(CASE WHEN t.rank = 3 THEN t.n_dead_tup END) as top_table_3_dead_tuples,
                MAX(CASE WHEN t.rank = 4 THEN t.table_name END) as top_table_4,
                MAX(CASE WHEN t.rank = 4 THEN t.n_dead_tup END) as top_table_4_dead_tuples,
                MAX(CASE WHEN t.rank = 5 THEN t.table_name END) as top_table_5,
                MAX(CASE WHEN t.rank = 5 THEN t.n_dead_tup END) as top_table_5_dead_tuples
            FROM agg_5m a
            LEFT JOIN top_tables t ON a.database_id = t.database_id 
                AND a.instance_id = t.instance_id
                AND a.collected_at = t.collected_at
            GROUP BY 
                a.database_id, a.instance_id, a.collected_at,
                a.total_vacuum_sessions, a.active_vacuum_sessions,
                a.autovacuum_sessions, a.manual_vacuum_sessions,
                a.avg_dead_tuples, a.max_dead_tuples, a.total_dead_tuples,
                a.avg_progress, a.tables_with_dead_tuples, a.tables_being_vacuumed,
                a.avg_elapsed_seconds, a.max_elapsed_seconds
            ORDER BY a.database_id, a.instance_id, a.collected_at
            """;

        return new JdbcCursorItemReaderBuilder<VacuumAgg5mDto>()
                .name("vacuumAgg5mReader")
                .dataSource(dataSource)
                .sql(sql)
                .rowMapper(new VacuumAgg5mDtoRowMapper())
                .build();
    }

    /**
     * Processor: 간단한 후처리
     */
    @Bean
    public ItemProcessor<VacuumAgg5mDto, VacuumAgg5mDto> vacuumAgg5mProcessor() {
        return item -> {
            item.setCreatedAt(OffsetDateTime.now());

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
    public ItemWriter<VacuumAgg5mDto> vacuumAgg5mWriter() {
        return chunk -> {
            List<? extends VacuumAgg5mDto> items = chunk.getItems();
            if (items.isEmpty()) {
                return;
            }

            StringBuilder sql = new StringBuilder("""
                INSERT INTO vacuum_metrics_agg_5m (
                    database_id, instance_id, collected_at,
                    total_vacuum_sessions, active_vacuum_sessions, 
                    autovacuum_sessions, manual_vacuum_sessions,
                    avg_dead_tuples, max_dead_tuples, total_dead_tuples,
                    avg_progress, tables_with_dead_tuples, tables_being_vacuumed,
                    avg_elapsed_seconds, max_elapsed_seconds,
                    top_table_1, top_table_1_dead_tuples,
                    top_table_2, top_table_2_dead_tuples,
                    top_table_3, top_table_3_dead_tuples,
                    top_table_4, top_table_4_dead_tuples,
                    top_table_5, top_table_5_dead_tuples,
                    created_at
                ) VALUES 
                """);

            for (int i = 0; i < items.size(); i++) {
                VacuumAgg5mDto item = items.get(i);
                sql.append(String.format(
                        "(%d, %d, '%s', %d, %d, %d, %d, %d, %d, %d, %.2f, %d, %d, %.2f, %.2f, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, NOW())",
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
                        sqlString(item.getTopTable1()),
                        sqlLong(item.getTopTable1DeadTuples()),
                        sqlString(item.getTopTable2()),
                        sqlLong(item.getTopTable2DeadTuples()),
                        sqlString(item.getTopTable3()),
                        sqlLong(item.getTopTable3DeadTuples()),
                        sqlString(item.getTopTable4()),
                        sqlLong(item.getTopTable4DeadTuples()),
                        sqlString(item.getTopTable5()),
                        sqlLong(item.getTopTable5DeadTuples())
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
                    top_table_1 = EXCLUDED.top_table_1,
                    top_table_1_dead_tuples = EXCLUDED.top_table_1_dead_tuples,
                    top_table_2 = EXCLUDED.top_table_2,
                    top_table_2_dead_tuples = EXCLUDED.top_table_2_dead_tuples,
                    top_table_3 = EXCLUDED.top_table_3,
                    top_table_3_dead_tuples = EXCLUDED.top_table_3_dead_tuples,
                    top_table_4 = EXCLUDED.top_table_4,
                    top_table_4_dead_tuples = EXCLUDED.top_table_4_dead_tuples,
                    top_table_5 = EXCLUDED.top_table_5,
                    top_table_5_dead_tuples = EXCLUDED.top_table_5_dead_tuples,
                    created_at = NOW()
                """);

            try (var connection = dataSource.getConnection();
                 var statement = connection.createStatement()) {

                statement.execute(sql.toString());
                log.info("📊 [VACUUM AGG 5M] ✅ Vacuum 5분 집계 데이터 {} 건 저장 완료", items.size());

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

    /**
     * RowMapper
     */
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
                    .build();
        }
    }
}