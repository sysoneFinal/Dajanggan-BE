/** 작성자 : 서샘이 */
package com.dajanggan.domain.metric.batch.aggregator;

import com.dajanggan.domain.session.dto.agg1m.SessionAgg1mDto;
import com.dajanggan.domain.session.repository.SessionAgg1mRepository;
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
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 1분 단위 세션 집계 Batch Job
 * DB에서 GROUP BY로 집계된 데이터를 읽어서 session_metrics_agg_1m에 저장
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class Session1mAggregator {

    private final JobRepository jobRepository;
    private final DataSource dataSource;
    private final SessionAgg1mRepository sessionAgg1mRepository;
    private final PlatformTransactionManager batchTransactionManager;


    /**
     * Job 정의
     */
    @Bean
    public Job sessionAgg1mJob() {
        return new JobBuilder("sessionAgg1mJob", jobRepository)
                .start(sessionAgg1mStep())
                .build();
    }

    /**
     * Step 정의
     */
    @Bean
    public Step sessionAgg1mStep() {
        return new StepBuilder("sessionAgg1mStep", jobRepository)
                .<SessionAgg1mDto, SessionAgg1mDto>chunk(100, batchTransactionManager)
                .reader(sessionAgg1mReader())
                .processor(sessionAgg1mProcessor())
                .writer(sessionAgg1mWriter())
                .build();
    }
    /**
     * Reader: DB에서 이미 집계된 데이터 읽기 (GROUP BY 사용)
     * 지난 1~2분 사이 데이터를 database_id, instance_id별로 집계
     */
    @Bean
    public JdbcCursorItemReader<SessionAgg1mDto> sessionAgg1mReader() {
        String sql = """
        SELECT 
            database_id,
            instance_id,
            DATE_TRUNC('minute', collected_at) as collected_at,
            
            -- 세션 수 집계
            COUNT(*) as total_sessions,
            COUNT(*) FILTER (WHERE state = 'active') as active_sessions,
            COUNT(*) FILTER (WHERE state = 'idle') as idle_sessions,
            COUNT(*) FILTER (WHERE state = 'idle in transaction') as idle_in_txn_sessions,
            COUNT(*) FILTER (WHERE state = 'idle in transaction (aborted)') as idle_in_txn_aborted_sessions,
            COUNT(*) FILTER (
                WHERE state NOT IN ('idle', 'idle in transaction (aborted)')
                  AND wait_event_type IN ('Lock', 'LWLock', 'IO', 'Client')
            ) AS waiting_sessions,
            
            -- Wait Event 카운트
            COUNT(*) FILTER (WHERE wait_event_type = 'Lock') as lock_wait_count,
            COUNT(*) FILTER (WHERE wait_event_type = 'IO') as io_wait_count,
            COUNT(*) FILTER (WHERE wait_event_type = 'LWLock') as lwlock_wait_count,
            COUNT(*) FILTER (WHERE wait_event_type = 'Client') as client_wait_count,
            
            -- 평균 계산
            AVG(wait_duration_sec) FILTER (WHERE wait_duration_sec IS NOT NULL) as avg_lock_wait_sec,
            AVG(query_age_sec) FILTER (WHERE query_age_sec IS NOT NULL) as avg_tx_duration_sec,
            
            -- 커넥션 정보 (원시 데이터에서 가져옴)
            COUNT(*) as used_connections,
            MAX(max_connections) as max_connections,
            
            -- 데드락 카운트
            COUNT(*) FILTER (WHERE is_deadlock = true) as deadlock_count
            
        FROM session_metrics_raw
        WHERE collected_at >= NOW() - INTERVAL '2 minutes'
          AND collected_at < NOW() - INTERVAL '1 minute'
        GROUP BY database_id, instance_id, DATE_TRUNC('minute', collected_at)
        ORDER BY database_id, instance_id, collected_at
        """;

        return new JdbcCursorItemReaderBuilder<SessionAgg1mDto>()
                .name("sessionAgg1mReader")
                .dataSource(dataSource)
                .sql(sql)
                .rowMapper(new SessionAgg1mDtoRowMapper())
                .build();
    }

    /**
     * Processor: 간단한 후처리
     * DB에서 이미 집계했으므로 거의 변환 없음
     */
    @Bean
    public ItemProcessor<SessionAgg1mDto, SessionAgg1mDto> sessionAgg1mProcessor() {
        return item -> {
            // 생성 시간 설정
            item.setCreatedAt(OffsetDateTime.now());
            
            // null 값 처리
            if (item.getAvgLockWaitSec() == null) {
                item.setAvgLockWaitSec(0.0);
            }
            if (item.getAvgTxDurationSec() == null) {
                item.setAvgTxDurationSec(0.0);
            }
            
            return item;
        };
    }

    /**
     * Writer: 집계 데이터 저장
     */
    @Bean
    public ItemWriter<SessionAgg1mDto> sessionAgg1mWriter() {
        return (Chunk<? extends SessionAgg1mDto> chunk) -> {
            List<? extends SessionAgg1mDto> items = chunk.getItems();

            if (!items.isEmpty()) {
                sessionAgg1mRepository.insertAgg1m(new ArrayList<>(items));
                log.info(">>>>>>>>>>>>>> 1분 집계 완료: {} 건 저장", items.size());
            }
        };
    }


    /**
     * RowMapper: DB 결과를 DTO로 매핑
     */
    private static class SessionAgg1mDtoRowMapper implements RowMapper<SessionAgg1mDto> {
        @Override
        public SessionAgg1mDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            SessionAgg1mDto dto = new SessionAgg1mDto();
            
            dto.setDatabaseId(rs.getLong("database_id"));
            dto.setInstanceId(rs.getLong("instance_id"));
            
            Timestamp collectedAtTs = rs.getTimestamp("collected_at");
            if (collectedAtTs != null) {
                dto.setCollectedAt(OffsetDateTime.ofInstant(
                    collectedAtTs.toInstant(), 
                    ZoneId.systemDefault()
                ));
            }
            
            // 세션 수
            dto.setTotalSessions(rs.getDouble("total_sessions"));
            dto.setActiveSessions(rs.getDouble("active_sessions"));
            dto.setIdleSessions(rs.getDouble("idle_sessions"));
            dto.setIdleInTxSessions(rs.getDouble("idle_in_txn_sessions"));
            dto.setIdleInTxAbortedSessions(rs.getDouble("idle_in_txn_aborted_sessions"));
            dto.setWaitingSessions(rs.getDouble("waiting_sessions"));
            
            // Wait Event 카운트
            dto.setLockWaitCount(rs.getInt("lock_wait_count"));
            dto.setIoWaitCount(rs.getInt("io_wait_count"));
            dto.setLwlockWaitCount(rs.getInt("lwlock_wait_count"));
            dto.setClientWaitCount(rs.getInt("client_wait_count"));
            
            // 평균값
            Double avgLockWait = rs.getDouble("avg_lock_wait_sec");
            dto.setAvgLockWaitSec(rs.wasNull() ? null : avgLockWait);
            
            Double avgTxDuration = rs.getDouble("avg_tx_duration_sec");
            dto.setAvgTxDurationSec(rs.wasNull() ? null : avgTxDuration);
            
            // 커넥션
            dto.setUsedConnections(rs.getDouble("used_connections"));
            dto.setMaxConnections(rs.getDouble("max_connections"));
            
            // 데드락
            dto.setDeadlockCount(rs.getInt("deadlock_count"));
            
            return dto;
        }
    }
}
