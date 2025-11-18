package com.dajanggan.domain.system.cpu.scheduler;

import com.dajanggan.domain.system.cpu.domain.CpuAgg30m;
import com.dajanggan.domain.system.cpu.repository.CpuMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * CPU 30분 집계 스케줄러
 * 30분마다 실행하여 cpu_agg 데이터를 30분 단위로 재집계
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CpuAgg30mScheduler {

    private final DataSource dataSource;
    private final CpuMapper cpuMapper;

    @PostConstruct
    public void init() {
        log.info("========== CpuAgg30mScheduler 초기화 완료 ==========");
    }

    /**
     * 30분마다 실행 (0, 30분)
     */
    @Scheduled(cron = "0 */30 * * * *")
    public void aggregate30m() {
        log.info("========== CPU 30분 집계 시작 ==========");

        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime timeBucket = now.truncatedTo(ChronoUnit.HOURS)
                    .plusMinutes((now.getMinute() / 30) * 30);
            
            OffsetDateTime startTime = timeBucket.minusMinutes(30);
            OffsetDateTime endTime = timeBucket;

            log.info("집계 시간 범위: {} ~ {}", startTime, endTime);

            // 활성 인스턴스 조회
            List<Long> instanceIds = cpuMapper.selectActiveInstanceIds();
            log.info("처리 대상 인스턴스: {} 개", instanceIds.size());

            int successCount = 0;
            int failCount = 0;

            for (Long instanceId : instanceIds) {
                try {
                    aggregateInstance30m(instanceId, timeBucket, startTime, endTime);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("CPU 30분 집계 실패: instanceId={}", instanceId, e);
                }
            }

            log.info("========== CPU 30분 집계 완료: 성공={}, 실패={} ==========", 
                    successCount, failCount);

        } catch (Exception e) {
            log.error("CPU 30분 집계 중 오류 발생", e);
        }
    }

    /**
     * 인스턴스별 30분 집계 처리
     */
    private void aggregateInstance30m(Long instanceId, OffsetDateTime timeBucket,
                                       OffsetDateTime startTime, OffsetDateTime endTime) {
        
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // cpu_agg_1m 테이블에서 30분간 데이터 집계
        String sql = """
            SELECT
                ? as time_bucket,
                ? as instance_id,
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
                MAX(max_query_duration_sec) as max_query_duration_sec,
                SUM(delta_xact_commit) as total_xact_commit,
                SUM(delta_xact_rollback) as total_xact_rollback,
                ROUND(AVG(xact_commit_rate)::numeric, 2) as avg_xact_commit_rate,
                ROUND(AVG(xact_rollback_rate)::numeric, 2) as avg_xact_rollback_rate,
                COUNT(*) as record_count
            FROM cpu_agg_1m
            WHERE instance_id = ?
              AND collected_at >= ?
              AND collected_at < ?
            """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                sql, timeBucket, instanceId, instanceId, startTime, endTime);

        if (results.isEmpty() || results.get(0).get("record_count") == null 
                || ((Number) results.get(0).get("record_count")).longValue() == 0) {
            log.debug("집계할 데이터 없음: instanceId={}, timeBucket={}", instanceId, timeBucket);
            return;
        }

        Map<String, Object> aggData = results.get(0);

        // CpuAgg30m 엔티티 생성
        CpuAgg30m agg30m = CpuAgg30m.builder()
                .instanceId(instanceId)
                .timeBucket(timeBucket)
                .avgTotalConnections(getDouble(aggData, "avg_total_connections"))
                .avgActiveConnections(getDouble(aggData, "avg_active_connections"))
                .avgIdleConnections(getDouble(aggData, "avg_idle_connections"))
                .avgIdleInTransaction(getDouble(aggData, "avg_idle_in_transaction"))
                .avgWaitingSessions(getDouble(aggData, "avg_waiting_sessions"))
                .avgWaitingForLock(getDouble(aggData, "avg_waiting_for_lock"))
                .avgWaitingForIo(getDouble(aggData, "avg_waiting_for_io"))
                .avgWaitEventClient(getDouble(aggData, "avg_wait_event_client"))
                .avgWaitEventActivity(getDouble(aggData, "avg_wait_event_activity"))
                .avgWaitEventBufferpin(getDouble(aggData, "avg_wait_event_bufferpin"))
                .avgWaitEventLwlock(getDouble(aggData, "avg_wait_event_lwlock"))
                .avgWaitEventTimeout(getDouble(aggData, "avg_wait_event_timeout"))
                .avgWaitEventIpc(getDouble(aggData, "avg_wait_event_ipc"))
                .avgClientBackend(getDouble(aggData, "avg_client_backend"))
                .avgAutovacuumWorker(getDouble(aggData, "avg_autovacuum_worker"))
                .avgParallelWorker(getDouble(aggData, "avg_parallel_worker"))
                .avgBackgroundWorker(getDouble(aggData, "avg_background_worker"))
                .avgLongRunningQueries(getDouble(aggData, "avg_long_running_queries"))
                .maxQueryDurationSec(getDouble(aggData, "max_query_duration_sec"))
                .totalXactCommit(getLong(aggData, "total_xact_commit"))
                .totalXactRollback(getLong(aggData, "total_xact_rollback"))
                .avgXactCommitRate(getDouble(aggData, "avg_xact_commit_rate"))
                .avgXactRollbackRate(getDouble(aggData, "avg_xact_rollback_rate"))
                .recordCount(getLong(aggData, "record_count"))
                .build();

        // CPU 30분 집계 데이터 저장
        cpuMapper.insertAgg30m(agg30m);
        
        log.debug("CPU 30분 집계 완료: instanceId={}, timeBucket={}, records={}", 
                instanceId, timeBucket, agg30m.getRecordCount());
    }

    private Double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0.0;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0L;
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }
}
