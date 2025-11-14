package com.dajanggan.domain.metric.batch.aggregator;

import com.dajanggan.domain.query.dto.agg5m.QueryAgg5mDto;
import com.dajanggan.domain.query.repository.QueryAgg5mRepository;
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
 * 5분 단위 쿼리 집계 Batch Job
 * query_metrics_raw에서 직접 읽어서 5분 단위로 집계
 * Top 5 슬로우 쿼리 포함
 * @author 이해든
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class Query5mAggregator {

    private final JobRepository jobRepository;
    private final DataSource dataSource;
    private final QueryAgg5mRepository queryAgg5mRepository;
    private final PlatformTransactionManager batchTransactionManager;

    /**
     * Job 정의 - queryAgg5mJob (이름 수정!)
     */
    @Bean
    public Job queryAgg5mJob() {
        return new JobBuilder("queryAgg5mJob", jobRepository)
                .start(queryAgg5mStep())
                .build();
    }

    /**
     * Step 정의
     */
    @Bean
    public Step queryAgg5mStep() {
        return new StepBuilder("queryAgg5mStep", jobRepository)
                .<QueryAgg5mDto, QueryAgg5mDto>chunk(100, batchTransactionManager)
                .reader(queryAgg5mReader())
                .processor(queryAgg5mProcessor())
                .writer(queryAgg5mWriter())
                .build();
    }

    /**
     * Reader: 지난 5~10분 사이 데이터를 읽어서 5분 단위로 집계
     * Top 5 슬로우 쿼리도 함께 추출
     */
    @Bean
    public JdbcCursorItemReader<QueryAgg5mDto> queryAgg5mReader() {
        String sql = """
            WITH base_agg AS (
                SELECT 
                    instance_id,
                    database_id,
                    DATE_TRUNC('hour', collected_at) + 
                        INTERVAL '5 minutes' * FLOOR(EXTRACT(MINUTE FROM collected_at) / 5) as collected_at,
                    
                    COUNT(*) as total_queries,
                    AVG(execution_time_ms) as avg_execution_time_ms,
                    COUNT(*) FILTER (WHERE execution_time_ms > 1000) as slow_query_count,
                    SUM(io_blocks) as total_io_blocks
                    
                FROM query_metrics_raw
                WHERE collected_at >= NOW() - INTERVAL '10 minutes'
                  AND collected_at < NOW() - INTERVAL '5 minutes'
                GROUP BY instance_id, database_id, 
                         DATE_TRUNC('hour', collected_at) + 
                         INTERVAL '5 minutes' * FLOOR(EXTRACT(MINUTE FROM collected_at) / 5)
            ),
            top_slow_queries AS (
                SELECT 
                    instance_id,
                    database_id,
                    DATE_TRUNC('hour', collected_at) + 
                        INTERVAL '5 minutes' * FLOOR(EXTRACT(MINUTE FROM collected_at) / 5) as collected_at,
                    short_query,
                    execution_time_ms,
                    ROW_NUMBER() OVER (
                        PARTITION BY instance_id, database_id,
                                     DATE_TRUNC('hour', collected_at) + 
                                     INTERVAL '5 minutes' * FLOOR(EXTRACT(MINUTE FROM collected_at) / 5)
                        ORDER BY execution_time_ms DESC
                    ) as rn
                FROM query_metrics_raw
                WHERE collected_at >= NOW() - INTERVAL '10 minutes'
                  AND collected_at < NOW() - INTERVAL '5 minutes'
                  AND execution_time_ms > 100
            )
            SELECT 
                ba.instance_id,
                ba.database_id,
                ba.collected_at,
                ba.total_queries,
                ba.avg_execution_time_ms,
                ba.slow_query_count,
                ba.total_io_blocks,
                
                MAX(CASE WHEN tsq.rn = 1 THEN tsq.short_query END) as top_slow_query_1,
                MAX(CASE WHEN tsq.rn = 1 THEN tsq.execution_time_ms END) as top_slow_query_1_time,
                MAX(CASE WHEN tsq.rn = 2 THEN tsq.short_query END) as top_slow_query_2,
                MAX(CASE WHEN tsq.rn = 2 THEN tsq.execution_time_ms END) as top_slow_query_2_time,
                MAX(CASE WHEN tsq.rn = 3 THEN tsq.short_query END) as top_slow_query_3,
                MAX(CASE WHEN tsq.rn = 3 THEN tsq.execution_time_ms END) as top_slow_query_3_time,
                MAX(CASE WHEN tsq.rn = 4 THEN tsq.short_query END) as top_slow_query_4,
                MAX(CASE WHEN tsq.rn = 4 THEN tsq.execution_time_ms END) as top_slow_query_4_time,
                MAX(CASE WHEN tsq.rn = 5 THEN tsq.short_query END) as top_slow_query_5,
                MAX(CASE WHEN tsq.rn = 5 THEN tsq.execution_time_ms END) as top_slow_query_5_time
                
            FROM base_agg ba
            LEFT JOIN top_slow_queries tsq 
                ON ba.instance_id = tsq.instance_id 
                AND ba.database_id = tsq.database_id
                AND ba.collected_at = tsq.collected_at
                AND tsq.rn <= 5
            GROUP BY ba.instance_id, ba.database_id, ba.collected_at,
                     ba.total_queries, ba.avg_execution_time_ms, 
                     ba.slow_query_count, ba.total_io_blocks
            ORDER BY ba.instance_id, ba.database_id, ba.collected_at
            """;

        return new JdbcCursorItemReaderBuilder<QueryAgg5mDto>()
                .name("queryAgg5mReader")
                .dataSource(dataSource)
                .sql(sql)
                .rowMapper(new QueryAgg5mDtoRowMapper())
                .build();
    }

    /**
     * Processor: 간단한 후처리
     */
    @Bean
    public ItemProcessor<QueryAgg5mDto, QueryAgg5mDto> queryAgg5mProcessor() {
        return item -> {
            item.setCreatedAt(OffsetDateTime.now());

            // null 값 처리
            if (item.getAvgExecutionTimeMs() == null) {
                item.setAvgExecutionTimeMs(0.0);
            }

            return item;
        };
    }

    /**
     * Writer: 5분 집계 데이터 저장
     */
    @Bean
    @SuppressWarnings("unchecked")
    public ItemWriter<QueryAgg5mDto> queryAgg5mWriter() {
        return chunk -> {
            List<QueryAgg5mDto> items = (List<QueryAgg5mDto>) chunk.getItems();

            if (!items.isEmpty()) {
                queryAgg5mRepository.insertAgg5m(items);
                log.info(">>>>>>>>>>>>>>5분 집계 완료: {} 건 저장", items.size());
            }
        };
    }

    /**
     * RowMapper: DB 결과를 DTO로 매핑
     */
    private static class QueryAgg5mDtoRowMapper implements RowMapper<QueryAgg5mDto> {
        @Override
        public QueryAgg5mDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            QueryAgg5mDto dto = new QueryAgg5mDto();

            dto.setDatabaseId(rs.getLong("database_id"));
            dto.setInstanceId(rs.getLong("instance_id"));

            Timestamp collectedAtTs = rs.getTimestamp("collected_at");
            if (collectedAtTs != null) {
                dto.setCollectedAt(OffsetDateTime.ofInstant(
                        collectedAtTs.toInstant(),
                        ZoneId.systemDefault()
                ));
            }

            // 기본 집계
            dto.setTotalQueries(rs.getInt("total_queries"));

            Double avgExecTime = rs.getDouble("avg_execution_time_ms");
            dto.setAvgExecutionTimeMs(rs.wasNull() ? null : avgExecTime);

            dto.setSlowQueryCount(rs.getInt("slow_query_count"));
            dto.setTotalIoBlocks(rs.getLong("total_io_blocks"));

            // Top 5 슬로우 쿼리
            dto.setTopSlowQuery1(rs.getString("top_slow_query_1"));
            Double time1 = rs.getDouble("top_slow_query_1_time");
            dto.setTopSlowQuery1Time(rs.wasNull() ? null : time1);

            dto.setTopSlowQuery2(rs.getString("top_slow_query_2"));
            Double time2 = rs.getDouble("top_slow_query_2_time");
            dto.setTopSlowQuery2Time(rs.wasNull() ? null : time2);

            dto.setTopSlowQuery3(rs.getString("top_slow_query_3"));
            Double time3 = rs.getDouble("top_slow_query_3_time");
            dto.setTopSlowQuery3Time(rs.wasNull() ? null : time3);

            dto.setTopSlowQuery4(rs.getString("top_slow_query_4"));
            Double time4 = rs.getDouble("top_slow_query_4_time");
            dto.setTopSlowQuery4Time(rs.wasNull() ? null : time4);

            dto.setTopSlowQuery5(rs.getString("top_slow_query_5"));
            Double time5 = rs.getDouble("top_slow_query_5_time");
            dto.setTopSlowQuery5Time(rs.wasNull() ? null : time5);

            return dto;
        }
    }
}