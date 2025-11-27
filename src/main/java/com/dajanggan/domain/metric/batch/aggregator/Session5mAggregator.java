/** 작성자 : 서샘이 */
package com.dajanggan.domain.metric.batch.aggregator;

import com.dajanggan.domain.session.dto.agg5m.SessionAgg5mDto;
import com.dajanggan.domain.session.repository.SessionAgg5mRepository;
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
 * 5분 단위 세션 집계 Batch Job
 * 1분 집계 데이터를 재집계하여 session_metrics_agg_5m에 저장
 * - 기본 통계 + Top 5 사용자 포함
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class Session5mAggregator {

    private final JobRepository jobRepository;
    private final DataSource dataSource;
    private final SessionAgg5mRepository sessionAgg5mRepository;
    private final PlatformTransactionManager transactionManager;

    /**
     * Job 정의
     */
    @Bean
    public Job sessionAgg5mJob() {
        return new JobBuilder("sessionAgg5mJob", jobRepository)
                .start(sessionAgg5mStep())
                .build();
    }

    /**
     * Step 정의
     */
    @Bean
    public Step sessionAgg5mStep() {
        return new StepBuilder("sessionAgg5mStep", jobRepository)
                .<SessionAgg5mDto, SessionAgg5mDto>chunk(100, transactionManager)
                .reader(sessionAgg5mReader())
                .processor(sessionAgg5mProcessor())
                .writer(sessionAgg5mWriter())
                .build();
    }

    /**
     * Reader: 1분 집계 데이터를 5분 단위로 재집계
     * - 최근 5~10분 사이 데이터를 5분 단위로 묶음
     * - raw 데이터에서 Top 5 사용자 추출
     */
    @Bean
    public JdbcCursorItemReader<SessionAgg5mDto> sessionAgg5mReader() {
        String sql = """
            WITH agg_5m AS (
                -- 1분 집계 데이터를 5분 단위로 재집계
                SELECT 
                    database_id,
                    instance_id,
                    DATE_TRUNC('hour', collected_at) + 
                        INTERVAL '5 minutes' * FLOOR(EXTRACT(MINUTE FROM collected_at) / 5) as collected_at,
                    
                    -- 평균값들 (1분 집계의 평균을 다시 평균)
                    AVG(total_sessions) as total_sessions,
                    AVG(active_sessions) as active_sessions,
                    AVG(idle_sessions) as idle_sessions,
                    AVG(idle_txn_sessions) as idle_txn_sessions,
                    AVG(waiting_sessions) as waiting_sessions,
                    
                    -- 트랜잭션 시간 평균
                    AVG(avg_tx_duration_sec) as avg_tx_duration_sec,
                    
                    -- 데드락은 합계
                    SUM(deadlock_count) as deadlock_count,
                    
                    -- 커넥션 (최대값 사용)
                    MAX(used_connections) as used_connections,
                    MAX(max_connections) as max_connections
                    
                FROM session_metrics_agg_1m
                WHERE collected_at >= NOW() - INTERVAL '10 minutes'
                  AND collected_at < NOW() - INTERVAL '5 minutes'
                GROUP BY database_id, instance_id, 
                         DATE_TRUNC('hour', collected_at) + 
                         INTERVAL '5 minutes' * FLOOR(EXTRACT(MINUTE FROM collected_at) / 5)
            ),
            top_users AS (
                -- raw 데이터에서 사용자별 세션 수 집계
                SELECT 
                    database_id,
                    instance_id,
                    DATE_TRUNC('hour', collected_at) + 
                        INTERVAL '5 minutes' * FLOOR(EXTRACT(MINUTE FROM collected_at) / 5) as collected_at,
                    username,
                    COUNT(*) as session_count,
                    ROW_NUMBER() OVER (
                        PARTITION BY database_id, instance_id, 
                                     DATE_TRUNC('hour', collected_at) + 
                                     INTERVAL '5 minutes' * FLOOR(EXTRACT(MINUTE FROM collected_at) / 5)
                        ORDER BY COUNT(*) DESC
                    ) as rank
                FROM session_metrics_raw
                WHERE collected_at >= NOW() - INTERVAL '10 minutes'
                  AND collected_at < NOW() - INTERVAL '5 minutes'
                  AND username IS NOT NULL
                GROUP BY database_id, instance_id, 
                         DATE_TRUNC('hour', collected_at) + 
                         INTERVAL '5 minutes' * FLOOR(EXTRACT(MINUTE FROM collected_at) / 5),
                         username
            )
            SELECT 
                a.database_id,
                a.instance_id,
                a.collected_at,
                a.total_sessions,
                a.active_sessions,
                a.idle_sessions,
                a.idle_txn_sessions,
                a.waiting_sessions,
                a.avg_tx_duration_sec,
                a.deadlock_count,
                a.used_connections,
                a.max_connections,
                
                -- Top 5 사용자
                MAX(CASE WHEN t.rank = 1 THEN t.username END) as top_user_1,
                MAX(CASE WHEN t.rank = 1 THEN t.session_count END) as top_user_1_sessions,
                MAX(CASE WHEN t.rank = 2 THEN t.username END) as top_user_2,
                MAX(CASE WHEN t.rank = 2 THEN t.session_count END) as top_user_2_sessions,
                MAX(CASE WHEN t.rank = 3 THEN t.username END) as top_user_3,
                MAX(CASE WHEN t.rank = 3 THEN t.session_count END) as top_user_3_sessions,
                MAX(CASE WHEN t.rank = 4 THEN t.username END) as top_user_4,
                MAX(CASE WHEN t.rank = 4 THEN t.session_count END) as top_user_4_sessions,
                MAX(CASE WHEN t.rank = 5 THEN t.username END) as top_user_5,
                MAX(CASE WHEN t.rank = 5 THEN t.session_count END) as top_user_5_sessions
                
            FROM agg_5m a
            LEFT JOIN top_users t
                ON a.database_id = t.database_id
                AND a.instance_id = t.instance_id
                AND a.collected_at = t.collected_at
                AND t.rank <= 5
            GROUP BY a.database_id, a.instance_id, a.collected_at,
                     a.total_sessions, a.active_sessions, a.idle_sessions,
                     a.idle_txn_sessions, a.waiting_sessions,
                     a.avg_tx_duration_sec, a.deadlock_count,
                     a.used_connections, a.max_connections
            ORDER BY a.database_id, a.instance_id, a.collected_at
            """;

        return new JdbcCursorItemReaderBuilder<SessionAgg5mDto>()
                .name("sessionAgg5mReader")
                .dataSource(dataSource)
                .sql(sql)
                .rowMapper(new SessionAgg5mDtoRowMapper())
                .build();
    }

    /**
     * Processor: 비율 계산 및 후처리
     */
    @Bean
    public ItemProcessor<SessionAgg5mDto, SessionAgg5mDto> sessionAgg5mProcessor() {
        return item -> {
            // 생성 시간 설정
            item.setCreatedAt(OffsetDateTime.now());
            
            // 활성 비율 계산
            if (item.getTotalSessions() != null && item.getTotalSessions() > 0) {
                double activeRatio = (item.getActiveSessions() / item.getTotalSessions()) * 100;
                item.setActiveRatio(Math.round(activeRatio * 100.0) / 100.0); // 소수점 2자리
                
                double waitRatio = (item.getWaitingSessions() / item.getTotalSessions()) * 100;
                item.setWaitRatio(Math.round(waitRatio * 100.0) / 100.0);
            } else {
                item.setActiveRatio(0.0);
                item.setWaitRatio(0.0);
            }
            
            // null 값 처리
            if (item.getAvgTxDurationSec() == null) {
                item.setAvgTxDurationSec(0.0);
            }
            if (item.getDeadlockCount() == null) {
                item.setDeadlockCount(0);
            }
            
            return item;
        };
    }

    /**
     * Writer: 5분 집계 데이터 저장
     */
    @Bean
    @SuppressWarnings("unchecked")
    public ItemWriter<SessionAgg5mDto> sessionAgg5mWriter() {
        return chunk -> {
            List<SessionAgg5mDto> items = (List<SessionAgg5mDto>) chunk.getItems();
            
            if (!items.isEmpty()) {
                sessionAgg5mRepository.insert5mAgg(items);
                log.info(">>>>>>>>>>>>>>5분 집계 완료: {} 건 저장", items.size());
            }
        };
    }

    /**
     * RowMapper: DB 결과를 DTO로 매핑
     */
    private static class SessionAgg5mDtoRowMapper implements RowMapper<SessionAgg5mDto> {
        @Override
        public SessionAgg5mDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            SessionAgg5mDto dto = new SessionAgg5mDto();
            
            dto.setDatabaseId(rs.getLong("database_id"));
            dto.setInstanceId(rs.getLong("instance_id"));
            
            Timestamp collectedAtTs = rs.getTimestamp("collected_at");
            if (collectedAtTs != null) {
                dto.setCollectedAt(OffsetDateTime.ofInstant(
                    collectedAtTs.toInstant(), 
                    ZoneId.systemDefault()
                ));
            }
            
            // 세션 통계
            dto.setTotalSessions(rs.getDouble("total_sessions"));
            dto.setActiveSessions(rs.getDouble("active_sessions"));
            dto.setIdleSessions(rs.getDouble("idle_sessions"));
            dto.setIdleTxnSessions(rs.getDouble("idle_txn_sessions"));
            dto.setWaitingSessions(rs.getDouble("waiting_sessions"));
            
            // 트랜잭션 & 데드락
            Double avgTxDuration = rs.getDouble("avg_tx_duration_sec");
            dto.setAvgTxDurationSec(rs.wasNull() ? null : avgTxDuration);
            dto.setDeadlockCount(rs.getInt("deadlock_count"));
            
            // 커넥션
            dto.setUsedConnections(rs.getDouble("used_connections"));
            dto.setMaxConnections(rs.getDouble("max_connections"));
            
            // Top 5 사용자
            dto.setTopUser1(rs.getString("top_user_1"));
            Double topUser1Sessions = rs.getDouble("top_user_1_sessions");
            dto.setTopUser1Sessions(rs.wasNull() ? null : topUser1Sessions);
            
            dto.setTopUser2(rs.getString("top_user_2"));
            Double topUser2Sessions = rs.getDouble("top_user_2_sessions");
            dto.setTopUser2Sessions(rs.wasNull() ? null : topUser2Sessions);
            
            dto.setTopUser3(rs.getString("top_user_3"));
            Double topUser3Sessions = rs.getDouble("top_user_3_sessions");
            dto.setTopUser3Sessions(rs.wasNull() ? null : topUser3Sessions);
            
            dto.setTopUser4(rs.getString("top_user_4"));
            Double topUser4Sessions = rs.getDouble("top_user_4_sessions");
            dto.setTopUser4Sessions(rs.wasNull() ? null : topUser4Sessions);
            
            dto.setTopUser5(rs.getString("top_user_5"));
            Double topUser5Sessions = rs.getDouble("top_user_5_sessions");
            dto.setTopUser5Sessions(rs.wasNull() ? null : topUser5Sessions);
            
            return dto;
        }
    }
}
