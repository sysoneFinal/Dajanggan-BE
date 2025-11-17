package com.dajanggan.domain.system.cpu.scheduler;

import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.domain.system.cpu.domain.CpuAgg;
import com.dajanggan.domain.system.cpu.domain.CpuRaw;
import com.dajanggan.domain.system.cpu.repository.CpuMapper;
import com.dajanggan.infrastructure.datasource.DataSourceFactory;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * CPU 메트릭 수집 스케줄러 (pg_stat_activity 기반)
 * 1분마다 실행:
 * 1. pg_stat_activity에서 연결 및 활동 상태 데이터 수집
 * 2. Raw 데이터 저장
 * 3. Agg 데이터 저장 (Raw 데이터를 그대로 복사)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CpuCollectionScheduler {

    private final CpuMapper cpuMapper;
    private final InstanceRepository instanceRepository;
    private final DataSourceFactory dataSourceFactory;

    @PostConstruct
    public void init() {
        log.info("========== CpuCollectionScheduler 초기화 완료 ==========");
    }

    /**
     * 1분마다 실행 (매분 0초)
     */
    @Scheduled(cron = "0 * * * * *")
    public void collectCpuMetrics() {
        log.info("========== CPU 메트릭 수집 시작 (pg_stat_activity) ==========");

        try {
            OffsetDateTime collectedAt = OffsetDateTime.now(ZoneOffset.UTC)
                    .truncatedTo(ChronoUnit.MINUTES);
            
            List<Long> instanceIds = cpuMapper.selectActiveInstanceIds();
            log.info("처리 대상 인스턴스: {} 개", instanceIds.size());

            int successCount = 0;
            int failCount = 0;

            for (Long instanceId : instanceIds) {
                try {
                    processInstanceMetrics(instanceId, collectedAt);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("CPU 메트릭 처리 실패: instanceId={}", instanceId, e);
                }
            }

            log.info("========== CPU 메트릭 수집 완료: 성공={}, 실패={} ==========", 
                    successCount, failCount);

        } catch (Exception e) {
            log.error("CPU 메트릭 수집 중 오류 발생", e);
        }
    }

    /**
     * 특정 인스턴스의 메트릭 처리
     */
    private void processInstanceMetrics(Long instanceId, OffsetDateTime collectedAt) {
        // 1. Instance 정보 조회
        Instance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("인스턴스를 찾을 수 없습니다: " + instanceId));

        // 2. JdbcTemplate 생성 (postgres 데이터베이스에 연결)
        JdbcTemplate jdbcTemplate = dataSourceFactory.createJdbcTemplate(instance, "postgres");

        // 3. pg_stat_activity에서 데이터 수집
        Map<String, Object> activityData = collectFromPgStatActivity(jdbcTemplate);
        
        // 4. pg_stat_database에서 트랜잭션 통계 수집
        Map<String, Object> databaseData = collectFromPgStatDatabase(jdbcTemplate);

        // 5. Raw 데이터 생성 및 저장
        CpuRaw raw = buildCpuRaw(instanceId, collectedAt, activityData, databaseData);
        cpuMapper.insertRaw(raw);
        log.debug("Raw 데이터 저장 완료: instanceId={}", instanceId);

        // 6. 이전 Raw 데이터 조회 (트랜잭션 증분 계산용)
        CpuRaw previousRaw = cpuMapper.selectPreviousRaw(instanceId);

        // 7. Agg 데이터 생성 및 저장
        CpuAgg agg = buildCpuAgg(instanceId, collectedAt, raw, previousRaw);
        cpuMapper.insertAgg(agg);
        log.debug("Agg 데이터 저장 완료: instanceId={}", instanceId);

        log.info("메트릭 처리 완료: instanceId={}, totalConn={}, activeConn={}, tps={}", 
                instanceId, raw.getTotalConnections(), raw.getActiveConnections(), 
                agg.getXactCommitRate());
    }

    /**
     * pg_stat_activity에서 데이터 수집
     */
    private Map<String, Object> collectFromPgStatActivity(JdbcTemplate jdbcTemplate) {
        String sql = """
            SELECT 
                -- 전체 연결 통계
                COUNT(*) as total_connections,
                COUNT(*) FILTER (WHERE state = 'active') as active_connections,
                COUNT(*) FILTER (WHERE state = 'idle') as idle_connections,
                COUNT(*) FILTER (WHERE state = 'idle in transaction') as idle_in_transaction,
                
                -- 대기 상태 분석 (wait_event_type별)
                COUNT(*) FILTER (WHERE wait_event IS NOT NULL) as waiting_sessions,
                COUNT(*) FILTER (WHERE wait_event_type = 'Lock') as waiting_for_lock,
                COUNT(*) FILTER (WHERE wait_event_type = 'IO') as waiting_for_io,
                COUNT(*) FILTER (WHERE wait_event_type = 'Client') as wait_event_client,
                COUNT(*) FILTER (WHERE wait_event_type = 'Activity') as wait_event_activity,
                COUNT(*) FILTER (WHERE wait_event_type = 'BufferPin') as wait_event_bufferpin,
                COUNT(*) FILTER (WHERE wait_event_type = 'LWLock') as wait_event_lwlock,
                COUNT(*) FILTER (WHERE wait_event_type = 'Timeout') as wait_event_timeout,
                COUNT(*) FILTER (WHERE wait_event_type = 'IPC') as wait_event_ipc,
                
                -- Backend 타입별 분석
                COUNT(*) FILTER (WHERE backend_type = 'client backend') as client_backend_count,
                COUNT(*) FILTER (WHERE backend_type = 'autovacuum worker') as autovacuum_worker_count,
                COUNT(*) FILTER (WHERE backend_type = 'parallel worker') as parallel_worker_count,
                COUNT(*) FILTER (WHERE backend_type NOT IN ('client backend', 'autovacuum worker', 'parallel worker')) 
                    as background_worker_count,
                
                -- 쿼리 실행 시간 분석
                COUNT(*) FILTER (WHERE state = 'active' 
                                  AND state_change < NOW() - INTERVAL '5 minutes') as long_running_queries,
                COALESCE(MAX(EXTRACT(EPOCH FROM (NOW() - state_change))) 
                         FILTER (WHERE state = 'active'), 0) as max_query_duration_sec
                
            FROM pg_stat_activity
            WHERE pid != pg_backend_pid()
            """;

        return jdbcTemplate.queryForMap(sql);
    }

    /**
     * pg_stat_database에서 트랜잭션 통계 수집
     */
    private Map<String, Object> collectFromPgStatDatabase(JdbcTemplate jdbcTemplate) {
        String sql = """
            SELECT 
                datname as database_name,
                COALESCE(xact_commit, 0) as xact_commit,
                COALESCE(xact_rollback, 0) as xact_rollback
            FROM pg_stat_database
            WHERE datname = current_database()
            """;

        return jdbcTemplate.queryForMap(sql);
    }

    /**
     * CpuRaw 객체 생성
     */
    private CpuRaw buildCpuRaw(Long instanceId, OffsetDateTime collectedAt, 
                                Map<String, Object> activityData, Map<String, Object> databaseData) {
        return CpuRaw.builder()
                .instanceId(instanceId)
                .collectedAt(collectedAt)
                .totalConnections(getLong(activityData, "total_connections"))
                .activeConnections(getLong(activityData, "active_connections"))
                .idleConnections(getLong(activityData, "idle_connections"))
                .idleInTransaction(getLong(activityData, "idle_in_transaction"))
                .waitingSessions(getLong(activityData, "waiting_sessions"))
                .waitingForLock(getLong(activityData, "waiting_for_lock"))
                .waitingForIo(getLong(activityData, "waiting_for_io"))
                .waitEventClient(getLong(activityData, "wait_event_client"))
                .waitEventActivity(getLong(activityData, "wait_event_activity"))
                .waitEventBufferpin(getLong(activityData, "wait_event_bufferpin"))
                .waitEventLwlock(getLong(activityData, "wait_event_lwlock"))
                .waitEventTimeout(getLong(activityData, "wait_event_timeout"))
                .waitEventIpc(getLong(activityData, "wait_event_ipc"))
                .clientBackendCount(getLong(activityData, "client_backend_count"))
                .autovacuumWorkerCount(getLong(activityData, "autovacuum_worker_count"))
                .parallelWorkerCount(getLong(activityData, "parallel_worker_count"))
                .backgroundWorkerCount(getLong(activityData, "background_worker_count"))
                .longRunningQueries(getLong(activityData, "long_running_queries"))
                .maxQueryDurationSec(getDouble(activityData, "max_query_duration_sec"))
                // pg_stat_database 트랜잭션 통계
                .databaseName(getString(databaseData, "database_name"))
                .xactCommit(getLong(databaseData, "xact_commit"))
                .xactRollback(getLong(databaseData, "xact_rollback"))
                .build();
    }

    /**
     * CpuAgg 객체 생성
     */
    private CpuAgg buildCpuAgg(Long instanceId, OffsetDateTime collectedAt, 
                                CpuRaw raw, CpuRaw previousRaw) {
        // 상태 판정 로직
        String status = determineStatus(raw);
        
        // 트랜잭션 증분 및 TPS 계산
        Long deltaXactCommit = 0L;
        Long deltaXactRollback = 0L;
        Double xactCommitRate = 0.0;
        Double xactRollbackRate = 0.0;
        
        if (previousRaw != null) {
            deltaXactCommit = raw.getXactCommit() - previousRaw.getXactCommit();
            deltaXactRollback = raw.getXactRollback() - previousRaw.getXactRollback();
            
            // 수집 간격(초) 계산 (기본 60초)
            long intervalSeconds = ChronoUnit.SECONDS.between(
                previousRaw.getCollectedAt(), raw.getCollectedAt()
            );
            if (intervalSeconds <= 0) {
                intervalSeconds = 60;
            }
            
            // TPS 계산: 초당 트랜잭션 수
            xactCommitRate = deltaXactCommit.doubleValue() / intervalSeconds;
            xactRollbackRate = deltaXactRollback.doubleValue() / intervalSeconds;
        }

        return CpuAgg.builder()
                .instanceId(instanceId)
                .collectedAt(collectedAt)
                .avgTotalConnections((double) raw.getTotalConnections())
                .avgActiveConnections((double) raw.getActiveConnections())
                .avgIdleConnections((double) raw.getIdleConnections())
                .avgIdleInTransaction((double) raw.getIdleInTransaction())
                .avgWaitingSessions((double) raw.getWaitingSessions())
                .avgWaitingForLock((double) raw.getWaitingForLock())
                .avgWaitingForIo((double) raw.getWaitingForIo())
                .avgWaitEventClient((double) raw.getWaitEventClient())
                .avgWaitEventActivity((double) raw.getWaitEventActivity())
                .avgWaitEventBufferpin((double) raw.getWaitEventBufferpin())
                .avgWaitEventLwlock((double) raw.getWaitEventLwlock())
                .avgWaitEventTimeout((double) raw.getWaitEventTimeout())
                .avgWaitEventIpc((double) raw.getWaitEventIpc())
                .avgClientBackend((double) raw.getClientBackendCount())
                .avgAutovacuumWorker((double) raw.getAutovacuumWorkerCount())
                .avgParallelWorker((double) raw.getParallelWorkerCount())
                .avgBackgroundWorker((double) raw.getBackgroundWorkerCount())
                .avgLongRunningQueries((double) raw.getLongRunningQueries())
                .maxQueryDurationSec(raw.getMaxQueryDurationSec())
                // 트랜잭션 통계
                .databaseName(raw.getDatabaseName())
                .deltaXactCommit(deltaXactCommit)
                .deltaXactRollback(deltaXactRollback)
                .xactCommitRate(xactCommitRate)
                .xactRollbackRate(xactRollbackRate)
                .status(status)
                .build();
    }

    /**
     * 상태 판정 로직
     * - 정상: waiting_sessions < 5 AND long_running_queries < 3
     * - 주의: waiting_sessions < 10 AND long_running_queries < 5
     * - 위험: 그 외
     */
    private String determineStatus(CpuRaw raw) {
        long waitingSessions = raw.getWaitingSessions();
        long longRunningQueries = raw.getLongRunningQueries();

        if (waitingSessions < 5 && longRunningQueries < 3) {
            return "정상";
        } else if (waitingSessions < 10 && longRunningQueries < 5) {
            return "주의";
        } else {
            return "위험";
        }
    }

    /**
     * Map에서 Long 값 추출 (null-safe)
     */
    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0L;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

    /**
     * Map에서 Double 값 추출 (null-safe)
     */
    private Double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Float) {
            return ((Float) value).doubleValue();
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    /**
     * Map에서 String 값 추출 (null-safe)
     */
    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
