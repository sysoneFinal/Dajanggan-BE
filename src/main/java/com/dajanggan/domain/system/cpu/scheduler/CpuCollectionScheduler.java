// 작성자 : 김동현
package com.dajanggan.domain.system.cpu.scheduler;

import com.dajanggan.domain.common.util.MetricCollectionUtils;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.repository.InstanceRepository;
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
        log.info("스케줄러 등록 확인: @Scheduled 메서드가 1분마다 실행됩니다.");
    }

    /**
     * 1분마다 실행 (매분 5초) - metric/batch 스타일에 맞춤
     * 수집이 완료된 후 집계되도록 시간 조정
     */
    @Scheduled(cron = "5 * * * * *")
    public void collectCpuMetrics() {
        log.info("========== CPU 메트릭 수집 시작 (pg_stat_activity) ==========");

        try {
            OffsetDateTime collectedAt = OffsetDateTime.now(ZoneOffset.UTC)
                    .truncatedTo(ChronoUnit.MINUTES);
            
            List<Long> instanceIds = cpuMapper.selectActiveInstanceIds();
            log.info("처리 대상 인스턴스: {} 개", instanceIds.size());
            
            if (instanceIds.isEmpty()) {
                log.warn("활성 인스턴스가 없습니다. DB의 instance 테이블에 is_active=true인 인스턴스가 있는지 확인하세요.");
                return;
            }

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

        // 5. Raw 데이터 생성 및 저장 (Agg 생성은 배치로 처리)
        CpuRaw raw = buildCpuRaw(instanceId, collectedAt, activityData, databaseData);
        cpuMapper.insertRaw(raw);
        log.debug("Raw 데이터 저장 완료: instanceId={}", instanceId);
        
        // 디버깅: Backend 타입별 카운트 로깅
        log.info("Raw 데이터 저장 - Backend 타입별 카운트: instanceId={}, client_backend={}, autovacuum_worker={}, parallel_worker={}, background_worker={}",
                instanceId,
                raw.getClientBackendCount(),
                raw.getAutovacuumWorkerCount(),
                raw.getParallelWorkerCount(),
                raw.getBackgroundWorkerCount());
        
        log.info("메트릭 처리 완료: instanceId={}, totalConn={}, activeConn={}", 
                instanceId, raw.getTotalConnections(), raw.getActiveConnections());
    }

    /**
     * pg_stat_activity에서 데이터 수집
     */
    private Map<String, Object> collectFromPgStatActivity(JdbcTemplate jdbcTemplate) {
        // 디버깅: 실제 backend_type 값 확인
        String debugSql = """
            SELECT DISTINCT backend_type, COUNT(*) as count
            FROM pg_stat_activity
            WHERE pid != pg_backend_pid()
            GROUP BY backend_type
            ORDER BY backend_type
            """;
        try {
            List<Map<String, Object>> backendTypes = jdbcTemplate.queryForList(debugSql);
            log.debug("pg_stat_activity의 backend_type 분포: {}", backendTypes);
        } catch (Exception e) {
            log.warn("backend_type 디버깅 쿼리 실패: {}", e.getMessage());
        }
        
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

        Map<String, Object> result = jdbcTemplate.queryForMap(sql);
        
        // 디버깅: Backend 타입별 카운트 로깅
        log.debug("Backend 타입별 카운트 - client_backend: {}, autovacuum_worker: {}, parallel_worker: {}, background_worker: {}",
                result.get("client_backend_count"),
                result.get("autovacuum_worker_count"),
                result.get("parallel_worker_count"),
                result.get("background_worker_count"));
        
        return result;
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
                .totalConnections(MetricCollectionUtils.getLongValue(activityData, "total_connections"))
                .activeConnections(MetricCollectionUtils.getLongValue(activityData, "active_connections"))
                .idleConnections(MetricCollectionUtils.getLongValue(activityData, "idle_connections"))
                .idleInTransaction(MetricCollectionUtils.getLongValue(activityData, "idle_in_transaction"))
                .waitingSessions(MetricCollectionUtils.getLongValue(activityData, "waiting_sessions"))
                .waitingForLock(MetricCollectionUtils.getLongValue(activityData, "waiting_for_lock"))
                .waitingForIo(MetricCollectionUtils.getLongValue(activityData, "waiting_for_io"))
                .waitEventClient(MetricCollectionUtils.getLongValue(activityData, "wait_event_client"))
                .waitEventActivity(MetricCollectionUtils.getLongValue(activityData, "wait_event_activity"))
                .waitEventBufferpin(MetricCollectionUtils.getLongValue(activityData, "wait_event_bufferpin"))
                .waitEventLwlock(MetricCollectionUtils.getLongValue(activityData, "wait_event_lwlock"))
                .waitEventTimeout(MetricCollectionUtils.getLongValue(activityData, "wait_event_timeout"))
                .waitEventIpc(MetricCollectionUtils.getLongValue(activityData, "wait_event_ipc"))
                .clientBackendCount(MetricCollectionUtils.getLongValue(activityData, "client_backend_count"))
                .autovacuumWorkerCount(MetricCollectionUtils.getLongValue(activityData, "autovacuum_worker_count"))
                .parallelWorkerCount(MetricCollectionUtils.getLongValue(activityData, "parallel_worker_count"))
                .backgroundWorkerCount(MetricCollectionUtils.getLongValue(activityData, "background_worker_count"))
                .longRunningQueries(MetricCollectionUtils.getLongValue(activityData, "long_running_queries"))
                .maxQueryDurationSec(MetricCollectionUtils.getDoubleValue(activityData, "max_query_duration_sec"))
                // pg_stat_database 트랜잭션 통계
                .databaseName(MetricCollectionUtils.getStringValue(databaseData, "database_name"))
                .xactCommit(MetricCollectionUtils.getLongValue(databaseData, "xact_commit"))
                .xactRollback(MetricCollectionUtils.getLongValue(databaseData, "xact_rollback"))
                .build();
    }


}
