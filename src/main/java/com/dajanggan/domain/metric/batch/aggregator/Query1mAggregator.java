package com.dajanggan.domain.metric.batch.aggregator;

import com.dajanggan.domain.query.dto.agg1m.QueryAgg1mDto;
import com.dajanggan.domain.query.repository.QueryAgg1mRepository;
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
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 1분 단위 쿼리 집계 Batch Job
 * DB에서 GROUP BY로 집계된 데이터를 읽어서 query_metrics_agg_1m에 저장
 * @author 이해든
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class Query1mAggregator {

    private final JobRepository jobRepository;
    private final DataSource dataSource;
    private final QueryAgg1mRepository queryAgg1mRepository;
    private final PlatformTransactionManager batchTransactionManager;

    /**
     * Job 정의
     */
    @Bean
    public Job queryAgg1mJob() {
        return new JobBuilder("queryAgg1mJob", jobRepository)
                .start(queryAgg1mStep())
                .build();
    }

    /**
     * Step 정의
     */
    @Bean
    public Step queryAgg1mStep() {
        return new StepBuilder("queryAgg1mStep", jobRepository)
                .<QueryAgg1mDto, QueryAgg1mDto>chunk(100, batchTransactionManager)
                .reader(queryAgg1mReader())
                .processor(queryAgg1mProcessor())
                .writer(queryAgg1mWriter())
                .build();
    }

    /**
     * Reader: DB에서 이미 집계된 데이터 읽기 (GROUP BY 사용)
     * 지난 1~2분 사이 데이터를 database_id, instance_id별로 집계
     */
    @Bean
    public JdbcCursorItemReader<QueryAgg1mDto> queryAgg1mReader() {
        String sql = """
            SELECT 
                instance_id,
                database_id,
                DATE_TRUNC('minute', collected_at) as collected_at,
                
                -- 쿼리 수 집계
                COUNT(*) as total_queries,
                COUNT(*) FILTER (WHERE query_type = 'SELECT') as select_queries,
                COUNT(*) FILTER (WHERE query_type = 'INSERT') as insert_queries,
                COUNT(*) FILTER (WHERE query_type = 'UPDATE') as update_queries,
                COUNT(*) FILTER (WHERE query_type = 'DELETE') as delete_queries,
                COUNT(*) FILTER (WHERE query_type NOT IN ('SELECT', 'INSERT', 'UPDATE', 'DELETE')) as other_queries,
                
                -- 실행 시간 집계
                AVG(execution_time_ms) as avg_execution_time_ms,
                MAX(execution_time_ms) as max_execution_time_ms,
                AVG(planning_time_ms) as avg_planning_time_ms,
                
                -- IO 블록 집계
                SUM(io_blocks) as total_io_blocks,
                AVG(io_blocks) as avg_io_blocks,
                
                -- 슬로우 쿼리 (1초 초과)
                COUNT(*) FILTER (WHERE execution_time_ms > 1000) as slow_query_count
                
            FROM query_metrics_raw
            WHERE collected_at >= NOW() - INTERVAL '2 minutes'
              AND collected_at < NOW() - INTERVAL '1 minute'
            GROUP BY instance_id, database_id, DATE_TRUNC('minute', collected_at)
            ORDER BY instance_id, database_id, collected_at
            """;

        return new JdbcCursorItemReaderBuilder<QueryAgg1mDto>()
                .name("queryAgg1mReader")
                .dataSource(dataSource)
                .sql(sql)
                .rowMapper(new QueryAgg1mDtoRowMapper())
                .build();
    }

    /**
     * Processor: 간단한 후처리
     */
    @Bean
    public ItemProcessor<QueryAgg1mDto, QueryAgg1mDto> queryAgg1mProcessor() {
        return item -> {
            item.setCreatedAt(OffsetDateTime.now());

            // null 값 처리
            if (item.getAvgExecutionTimeMs() == null) {
                item.setAvgExecutionTimeMs(0.0);
            }
            if (item.getAvgPlanningTimeMs() == null) {
                item.setAvgPlanningTimeMs(0.0);
            }
            if (item.getAvgIoBlocks() == null) {
                item.setAvgIoBlocks(0.0);
            }

            return item;
        };
    }

    /**
     * Writer: 집계 데이터 저장
     */
    @Bean
    @SuppressWarnings("unchecked")
    public ItemWriter<QueryAgg1mDto> queryAgg1mWriter() {
        return chunk -> {
            List<QueryAgg1mDto> items = (List<QueryAgg1mDto>) chunk.getItems();

            if (!items.isEmpty()) {
                queryAgg1mRepository.insertAgg1m(items);
                log.info(">>>>>>>>>>>>>>1분 집계 완료: {} 건 저장", items.size());
            }
        };
    }

    /**
     * RowMapper: DB 결과를 DTO로 매핑
     */
    private static class QueryAgg1mDtoRowMapper implements RowMapper<QueryAgg1mDto> {
        @Override
        public QueryAgg1mDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            QueryAgg1mDto dto = new QueryAgg1mDto();

            dto.setDatabaseId(rs.getLong("database_id"));
            dto.setInstanceId(rs.getLong("instance_id"));

            Timestamp collectedAtTs = rs.getTimestamp("collected_at");
            if (collectedAtTs != null) {
                dto.setCollectedAt(OffsetDateTime.ofInstant(
                        collectedAtTs.toInstant(),
                        ZoneId.systemDefault()
                ));
            }

            // 쿼리 수
            dto.setTotalQueries(rs.getInt("total_queries"));
            dto.setSelectQueries(rs.getInt("select_queries"));
            dto.setInsertQueries(rs.getInt("insert_queries"));
            dto.setUpdateQueries(rs.getInt("update_queries"));
            dto.setDeleteQueries(rs.getInt("delete_queries"));
            dto.setOtherQueries(rs.getInt("other_queries"));

            // 실행 시간
            Double avgExecTime = rs.getDouble("avg_execution_time_ms");
            dto.setAvgExecutionTimeMs(rs.wasNull() ? null : avgExecTime);

            Double maxExecTime = rs.getDouble("max_execution_time_ms");
            dto.setMaxExecutionTimeMs(rs.wasNull() ? null : maxExecTime);

            Double avgPlanTime = rs.getDouble("avg_planning_time_ms");
            dto.setAvgPlanningTimeMs(rs.wasNull() ? null : avgPlanTime);

            // IO 블록
            dto.setTotalIoBlocks(rs.getLong("total_io_blocks"));

            Double avgIo = rs.getDouble("avg_io_blocks");
            dto.setAvgIoBlocks(rs.wasNull() ? null : avgIo);

            // 슬로우 쿼리
            dto.setSlowQueryCount(rs.getInt("slow_query_count"));

            return dto;
        }
    }
}