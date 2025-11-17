package com.dajanggan.domain.metric.batch.aggregator;

import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.overview.dto.DatabaseMetricsAgg;
import com.dajanggan.domain.overview.service.*;
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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 데이터베이스 메트릭 집계 Batch Job
 * 각 도메인 서비스를 호출하여 database_metrics_agg에 저장
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DatabaseMetricsAggregator {

    private final JobRepository jobRepository;
    private final DataSource dataSource;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final PlatformTransactionManager batchTransactionManager;

    // 도메인 서비스들
    private final TransactionMetricsService transactionMetricsService;
    private final SessionMetricsService sessionMetricsService;
    private final DiskMetricsService diskMetricsService;
    private final QueryMetricsService queryMetricsService;
    private final EventMetricsService eventMetricsService;

    /**
     * Job 정의
     */
    @Bean
    public Job databaseMetricsAggJob() {
        return new JobBuilder("databaseMetricsAggJob", jobRepository)
                .start(databaseMetricsAggStep())
                .build();
    }

    /**
     * Step 정의
     */
    @Bean
    public Step databaseMetricsAggStep() {
        return new StepBuilder("databaseMetricsAggStep", jobRepository)
                .<DatabaseInfo, DatabaseMetricsAgg>chunk(10, batchTransactionManager)
                .reader(databaseInfoReader())
                .processor(databaseMetricsProcessor())
                .writer(databaseMetricsWriter())
                .build();
    }

    /**
     * Reader: 활성화된 데이터베이스 정보 읽기
     */
    @Bean
    public JdbcCursorItemReader<DatabaseInfo> databaseInfoReader() {
        String sql = """
                SELECT md.instance_id, md.database_id, md.database_name,
                       mi.instance_name, mi.host, mi.port, mi.username, mi.secret_ref, mi.sslmode
                FROM monitor_database md
                JOIN monitor_instance mi ON md.instance_id = mi.instance_id
                WHERE md.is_enabled = true
                ORDER BY md.instance_id, md.database_id
                """;

        return new JdbcCursorItemReaderBuilder<DatabaseInfo>()
                .name("databaseInfoReader")
                .dataSource(dataSource)
                .sql(sql)
                .rowMapper(new DatabaseInfoRowMapper())
                .build();
    }

    /**
     * Processor: 각 데이터베이스별 메트릭 집계
     */
    @Bean
    public ItemProcessor<DatabaseInfo, DatabaseMetricsAgg> databaseMetricsProcessor() {
        return dbInfo -> {
            try {
                OffsetDateTime collectedAt = OffsetDateTime.now().withSecond(0).withNano(0);
                OffsetDateTime oneMinuteAgo = collectedAt.minusMinutes(1);

                Long instanceId = dbInfo.instance.getInstanceId();
                Long databaseId = dbInfo.databaseId;
                String databaseName = dbInfo.databaseName;

                // 1. TPS 계산
                var txStats = transactionMetricsService.calculateTPS(
                        dbInfo.instance, databaseName, instanceId, databaseId
                );

                // 2. 세션 통계
                var sessionStats = sessionMetricsService.aggregate(
                        dbInfo.instance, databaseName, instanceId, databaseId, oneMinuteAgo, collectedAt
                );

                // 3. 디스크 통계
                var diskStats = diskMetricsService.aggregate(
                        dbInfo.instance, databaseName, instanceId, databaseId, oneMinuteAgo, collectedAt
                );

                // 4. 쿼리 통계
                var queryStats = queryMetricsService.aggregate(
                        dbInfo.instance, databaseName, instanceId, databaseId
                );

                // 5. 이벤트 통계
                var eventStats = eventMetricsService.aggregate(
                        dbInfo.instance, databaseName, instanceId, databaseId, collectedAt
                );

                // DTO 생성
                DatabaseMetricsAgg dto = new DatabaseMetricsAgg();
                dto.setInstanceId(instanceId);
                dto.setDatabaseId(databaseId);
                dto.setCollectedAt(collectedAt);

                // 세션/커넥션
                dto.setActiveSessions((int) sessionStats.avgActiveSessions());
                dto.setUsedConnections((int) sessionStats.usedConnections());
                dto.setMaxConnections(sessionStats.maxConnections());
                dto.setConnectionUsagePercent(sessionStats.connectionUsagePercent());

                // 트랜잭션
                dto.setTpsTotal(txStats.tps());
                dto.setXactCommit(txStats.xactCommit());
                dto.setXactRollback(txStats.xactRollback());

                // 대기 이벤트
                dto.setLockWaitCount(diskStats.lockWaitCount());
                dto.setIoWaitCount(diskStats.ioWaitCount());

                // Disk I/O
                dto.setBlksHit(diskStats.totalBlksHit());
                dto.setBlksRead(diskStats.totalBlksRead());
                dto.setCacheHitRatio(diskStats.avgCacheHitRatio());
                dto.setAvgReadLatencyMs(diskStats.avgReadLatency());
                dto.setAvgWriteLatencyMs(diskStats.avgWriteLatency());

                // 슬로우 쿼리
                dto.setSlowQueryCount(queryStats.slowQueryCount());
                dto.setTopSlowQuery1(queryStats.topSlowQuery1());
                dto.setTopSlowQuery1Time(queryStats.topSlowQuery1Time());
                dto.setTopSlowQuery2(queryStats.topSlowQuery2());
                dto.setTopSlowQuery2Time(queryStats.topSlowQuery2Time());
                dto.setTopSlowQuery3(queryStats.topSlowQuery3());
                dto.setTopSlowQuery3Time(queryStats.topSlowQuery3Time());
                dto.setDeadTupleTotal(queryStats.deadTupleTotal());

                // 시스템 이벤트
                dto.setInfoEventCount(eventStats.infoCount());
                dto.setWarningEventCount(eventStats.warningCount());
                dto.setCriticalEventCount(eventStats.criticalCount());
                dto.setRecentEventType(eventStats.recentEventType());
                dto.setRecentEventLevel(eventStats.recentEventLevel());
                dto.setRecentEventAgeMin(eventStats.recentEventAgeMin());

                dto.setCreatedAt(OffsetDateTime.now());

                log.debug("데이터베이스 메트릭 처리 완료: instanceId={}, databaseId={}, TPS={}",
                        instanceId, databaseId, txStats.tps());

                return dto;

            } catch (Exception e) {
                log.error("데이터베이스 메트릭 처리 실패: instanceId={}, databaseId={}, error={}",
                        dbInfo.instance.getInstanceId(), dbInfo.databaseId, e.getMessage(), e);
                return null; // null 반환 시 해당 아이템은 Writer로 전달되지 않음
            }
        };
    }

    /**
     * Writer: 집계 데이터 저장
     */
    @Bean
    public ItemWriter<DatabaseMetricsAgg> databaseMetricsWriter() {
        return (Chunk<? extends DatabaseMetricsAgg> chunk) -> {
            List<? extends DatabaseMetricsAgg> items = chunk.getItems();

            if (items.isEmpty()) {
                return;
            }

            String sql = """
                INSERT INTO database_metrics_agg (
                    instance_id, database_id, collected_at,
                    active_sessions, used_connections, max_connections, connection_usage_percent,
                    tps_total, xact_commit, xact_rollback,
                    lock_wait_count, io_wait_count,
                    blks_hit, blks_read, cache_hit_ratio, avg_read_latency_ms, avg_write_latency_ms,
                    slow_query_count, top_slow_query_1, top_slow_query_1_time,
                    top_slow_query_2, top_slow_query_2_time,
                    top_slow_query_3, top_slow_query_3_time,
                    dead_tuple_total,
                    info_event_count, warning_event_count, critical_event_count,
                    recent_event_type, recent_event_level, recent_event_age_min
                )
                VALUES (
                    :instanceId, :databaseId, :collectedAt,
                    :activeSessions, :usedConnections, :maxConnections, :connectionUsagePercent,
                    :tpsTotal, :xactCommit, :xactRollback,
                    :lockWaitCount, :ioWaitCount,
                    :blksHit, :blksRead, :cacheHitRatio, :avgReadLatencyMs, :avgWriteLatencyMs,
                    :slowQueryCount, :topSlowQuery1, :topSlowQuery1Time,
                    :topSlowQuery2, :topSlowQuery2Time,
                    :topSlowQuery3, :topSlowQuery3Time,
                    :deadTupleTotal,
                    :infoEventCount, :warningEventCount, :criticalEventCount,
                    :recentEventType, :recentEventLevel, :recentEventAgeMin
                )
                ON CONFLICT (instance_id, database_id, collected_at)
                DO UPDATE SET
                    active_sessions = EXCLUDED.active_sessions,
                    used_connections = EXCLUDED.used_connections,
                    max_connections = EXCLUDED.max_connections,
                    connection_usage_percent = EXCLUDED.connection_usage_percent,
                    tps_total = EXCLUDED.tps_total,
                    xact_commit = EXCLUDED.xact_commit,
                    xact_rollback = EXCLUDED.xact_rollback,
                    lock_wait_count = EXCLUDED.lock_wait_count,
                    io_wait_count = EXCLUDED.io_wait_count,
                    blks_hit = EXCLUDED.blks_hit,
                    blks_read = EXCLUDED.blks_read,
                    cache_hit_ratio = EXCLUDED.cache_hit_ratio,
                    avg_read_latency_ms = EXCLUDED.avg_read_latency_ms,
                    avg_write_latency_ms = EXCLUDED.avg_write_latency_ms,
                    slow_query_count = EXCLUDED.slow_query_count,
                    top_slow_query_1 = EXCLUDED.top_slow_query_1,
                    top_slow_query_1_time = EXCLUDED.top_slow_query_1_time,
                    top_slow_query_2 = EXCLUDED.top_slow_query_2,
                    top_slow_query_2_time = EXCLUDED.top_slow_query_2_time,
                    top_slow_query_3 = EXCLUDED.top_slow_query_3,
                    top_slow_query_3_time = EXCLUDED.top_slow_query_3_time,
                    dead_tuple_total = EXCLUDED.dead_tuple_total,
                    info_event_count = EXCLUDED.info_event_count,
                    warning_event_count = EXCLUDED.warning_event_count,
                    critical_event_count = EXCLUDED.critical_event_count,
                    recent_event_type = EXCLUDED.recent_event_type,
                    recent_event_level = EXCLUDED.recent_event_level,
                    recent_event_age_min = EXCLUDED.recent_event_age_min,
                    created_at = NOW()
                """;

            int totalInserted = 0;
            for (DatabaseMetricsAgg item : items) {
                MapSqlParameterSource params = new MapSqlParameterSource()
                        .addValue("instanceId", item.getInstanceId())
                        .addValue("databaseId", item.getDatabaseId())
                        .addValue("collectedAt", item.getCollectedAt())
                        .addValue("activeSessions", item.getActiveSessions())
                        .addValue("usedConnections", item.getUsedConnections())
                        .addValue("maxConnections", item.getMaxConnections())
                        .addValue("connectionUsagePercent", item.getConnectionUsagePercent())
                        .addValue("tpsTotal", item.getTpsTotal())
                        .addValue("xactCommit", item.getXactCommit())
                        .addValue("xactRollback", item.getXactRollback())
                        .addValue("lockWaitCount", item.getLockWaitCount())
                        .addValue("ioWaitCount", item.getIoWaitCount())
                        .addValue("blksHit", item.getBlksHit())
                        .addValue("blksRead", item.getBlksRead())
                        .addValue("cacheHitRatio", item.getCacheHitRatio())
                        .addValue("avgReadLatencyMs", item.getAvgReadLatencyMs())
                        .addValue("avgWriteLatencyMs", item.getAvgWriteLatencyMs())
                        .addValue("slowQueryCount", item.getSlowQueryCount())
                        .addValue("topSlowQuery1", item.getTopSlowQuery1())
                        .addValue("topSlowQuery1Time", item.getTopSlowQuery1Time())
                        .addValue("topSlowQuery2", item.getTopSlowQuery2())
                        .addValue("topSlowQuery2Time", item.getTopSlowQuery2Time())
                        .addValue("topSlowQuery3", item.getTopSlowQuery3())
                        .addValue("topSlowQuery3Time", item.getTopSlowQuery3Time())
                        .addValue("deadTupleTotal", item.getDeadTupleTotal())
                        .addValue("infoEventCount", item.getInfoEventCount())
                        .addValue("warningEventCount", item.getWarningEventCount())
                        .addValue("criticalEventCount", item.getCriticalEventCount())
                        .addValue("recentEventType", item.getRecentEventType())
                        .addValue("recentEventLevel", item.getRecentEventLevel())
                        .addValue("recentEventAgeMin", item.getRecentEventAgeMin());

                totalInserted += namedJdbcTemplate.update(sql, params);
            }

            log.info(">>>>>>>>>>>>>> 데이터베이스 메트릭 집계 완료: {} 건 저장", totalInserted);
        };
    }

    /**
     * 데이터베이스 정보 DTO
     */
    private static class DatabaseInfo {
        Instance instance;
        Long databaseId;
        String databaseName;

        DatabaseInfo(Instance instance, Long databaseId, String databaseName) {
            this.instance = instance;
            this.databaseId = databaseId;
            this.databaseName = databaseName;
        }
    }

    /**
     * RowMapper: DB 결과를 DatabaseInfo로 매핑
     */
    private static class DatabaseInfoRowMapper implements RowMapper<DatabaseInfo> {
        @Override
        public DatabaseInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
            Instance instance = Instance.builder()
                    .instanceId(rs.getLong("instance_id"))
                    .instanceName(rs.getString("instance_name"))
                    .host(rs.getString("host"))
                    .port(rs.getInt("port"))
                    .userName(rs.getString("username"))
                    .secretRef(rs.getString("secret_ref"))
                    .sslmode(rs.getString("sslmode"))
                    .build();

            return new DatabaseInfo(
                    instance,
                    rs.getLong("database_id"),
                    rs.getString("database_name")
            );
        }
    }
}