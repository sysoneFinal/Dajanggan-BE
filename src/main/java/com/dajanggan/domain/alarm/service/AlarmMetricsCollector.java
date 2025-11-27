package com.dajanggan.domain.alarm.service;

import com.dajanggan.domain.instance.dto.DatabaseResponse;
import com.dajanggan.domain.instance.dto.InstanceResponse;
import com.dajanggan.domain.instance.service.DatabaseService;
import com.dajanggan.domain.instance.service.InstanceService;
import com.dajanggan.domain.instance.service.PostgresConnectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AlarmMetrics 수집기
 *
 * 주요 책임:
 * - 주기적 메트릭 수집 (1분)
 * - 알람 규칙 체크
 * - DB 연결 관리
 *
 * <pre>
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-14  김민서    1. 최초작성
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmMetricsCollector {

    private final GenericAlarmCollector alarmCollector;
    private final DatabaseService databaseService;
    private final InstanceService instanceService;
    private final PostgresConnectionService connectionService;

    private volatile boolean testMode = false;

    @Scheduled(fixedDelay = 60000) // 1분마다
    public void collectMetrics() {
        if (testMode) {
            log.debug("테스트 모드: 스케줄러 건너뜀");
            return;
        }

        log.info("========== 알람 규칙 체크 시작 ==========");

        // 1. 활성화된 데이터베이스 조회 (DTO)
        List<DatabaseResponse> databases = databaseService.findAllEnabled();

        if (databases.isEmpty()) {
            log.debug("활성화된 데이터베이스 없음");
            return;
        }

        log.info("알람 체크 대상 데이터베이스: {} 개", databases.size());

        // 2. 인스턴스 정보 조회 (N+1 방지)
        List<Long> instanceIds = databases.stream()
                .map(DatabaseResponse::getInstanceId)
                .distinct()
                .toList();

        Map<Long, InstanceResponse> instanceMap = instanceService.findAll()
                .stream()
                .filter(instance -> instanceIds.contains(instance.getInstanceId()))
                .collect(Collectors.toMap(InstanceResponse::getInstanceId, i -> i));

        int checkedCount = 0;
        int failedCount = 0;

        // 3. 각 데이터베이스별 알람 체크
        for (DatabaseResponse db : databases) {
            InstanceResponse instance = instanceMap.get(db.getInstanceId());

            if (instance == null) {
                log.error("인스턴스를 찾을 수 없음: instanceId={}", db.getInstanceId());
                failedCount++;
                continue;
            }

            try (Connection conn = connectionService.createConnection(
                    db.getInstanceId(),
                    db.getDatabaseName())) {

                log.debug("알람 체크: {}:{}/{}",
                        instance.getHost(), instance.getPort(), db.getDatabaseName());

                // 메트릭 체크
                checkAllMetrics(conn, instance, db);

                checkedCount++;

            } catch (Exception e) {
                failedCount++;
                log.error("알람 체크 실패: {}:{}/{} - {}",
                        instance.getHost(), instance.getPort(), db.getDatabaseName(), e.getMessage());
            }
        }

        log.info("========== 알람 규칙 체크 완료: 성공={}, 실패={} ==========",
                checkedCount, failedCount);
    }

    /**
     * 모든 메트릭 체크
     */
    private void checkAllMetrics(
            Connection conn,
            InstanceResponse instance,
            DatabaseResponse db
    ) {
        // Vacuum 관련
        checkMetricSafely(conn, instance.getInstanceId(), db.getDatabaseId(), "autovacuum_worker_utilization");
        checkMetricSafely(conn, instance.getInstanceId(), db.getDatabaseId(), "transaction_age");
        checkMetricSafely(conn, instance.getInstanceId(), db.getDatabaseId(), "wraparound_progress");

        // 세션 관련
        checkMetricSafely(conn, instance.getInstanceId(), db.getDatabaseId(), "long_running_queries");
        checkMetricSafely(conn, instance.getInstanceId(), db.getDatabaseId(), "lock_waits");
        checkMetricSafely(conn, instance.getInstanceId(), db.getDatabaseId(), "long_idle_sessions");
        checkMetricSafely(conn, instance.getInstanceId(), db.getDatabaseId(), "blocking_sessions");

        // 쿼리 관련
        checkMetricSafely(conn, instance.getInstanceId(), db.getDatabaseId(), "slow_query_spike");
        checkMetricSafely(conn, instance.getInstanceId(), db.getDatabaseId(), "avg_execution_spike");
        checkMetricSafely(conn, instance.getInstanceId(), db.getDatabaseId(), "qps_spike");
    }

    /**
     * 안전하게 메트릭 체크 (개별 실패 시 계속 진행)
     */
    private void checkMetricSafely(
            Connection conn,
            Long instanceId,
            Long databaseId,
            String metricType
    ) {
        try {
            alarmCollector.checkMetric(conn, instanceId, databaseId, metricType);
        } catch (Exception e) {
            log.error("메트릭 체크 실패: instanceId={}, databaseId={}, metric={}",
                    instanceId, databaseId, metricType, e);
        }
    }

    /**
     * 테스트 모드 설정
     */
    public void setTestMode(boolean enabled) {
        this.testMode = enabled;
        log.info("테스트 모드: {}", enabled ? "활성화" : "비활성화");
    }
}