package com.dajanggan.domain.alarm.service;

import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.repository.DatabaseRepository;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.global.crypto.AesGcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 알람 규칙 체크 스케줄러
 * (기존 AlarmMetricsCollector.java를 개선한 버전)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlarmMetricsCollector {

    private final GenericAlarmCollector alarmCollector;
    private final InstanceRepository instanceRepository;
    private final DatabaseRepository databaseRepository;
    private final AesGcmService aesGcmService;


    @Scheduled(fixedDelay = 60000) // 1분마다
    public void collectMetrics() {
        log.info("========== 알람 규칙 체크 시작 ==========");

        // 활성화된 데이터베이스 조회
        List<Database> databases = databaseRepository.findAllEnabled();

        if (databases.isEmpty()) {
            log.debug("활성화된 데이터베이스 없음");
            return;
        }

        log.info("알람 체크 대상 데이터베이스: {} 개", databases.size());

        // 인스턴스 정보 한 번에 조회 (N+1 방지) - secret_ref 포함!
        List<Long> instanceIds = databases.stream()
                .map(Database::getInstanceId)
                .distinct()
                .toList();

        Map<Long, Instance> instanceMap = instanceRepository.findAllWithSecrets(instanceIds).stream()
                .collect(Collectors.toMap(Instance::getInstanceId, i -> i));

        int checkedCount = 0;
        int failedCount = 0;

        // 각 데이터베이스별 알람 체크
        for (Database db : databases) {
            Instance instance = instanceMap.get(db.getInstanceId());

            if (instance == null) {
                log.error("************* Database ID {} - 연결된 Instance를 찾을 수 없음 (instance_id: {})",
                        db.getDatabaseId(), db.getInstanceId());
                failedCount++;
                continue;
            }

            try (Connection conn = createConnection(instance, db.getDatabaseName())) {

                log.debug(">>>>>>>>>>>>>>>> [{}] Checking alarms: {}:{} / {}",
                        java.time.OffsetDateTime.now(),
                        instance.getHost(),
                        instance.getPort(),
                        db.getDatabaseName());

                // MetricConfig에 정의된 모든 지표 체크
                // Vacuum 관련 지표
                checkMetricSafely(conn, instance, db, "autovacuum_worker_utilization");
                checkMetricSafely(conn, instance, db, "transaction_age");
                checkMetricSafely(conn, instance, db, "wraparound_progress");
                
                // 세션 관련 지표
                checkMetricSafely(conn, instance, db, "long_running_queries");
                checkMetricSafely(conn, instance, db, "lock_waits");
                checkMetricSafely(conn, instance, db, "long_idle_sessions");
                checkMetricSafely(conn, instance, db, "blocking_sessions");
                
                // 쿼리 관련 지표 (집계 테이블 사용)
                checkMetricSafely(conn, instance, db, "slow_query_spike");
                checkMetricSafely(conn, instance, db, "avg_execution_spike");
                checkMetricSafely(conn, instance, db, "qps_spike");

                checkedCount++;

            } catch (Exception e) {
                failedCount++;
                log.error("************* [{}] {}:{}/{} 알람 체크 실패: {}",
                        java.time.OffsetDateTime.now(),
                        instance.getHost(),
                        instance.getPort(),
                        db.getDatabaseName(),
                        e.getMessage(),
                        e);
            }
        }

        log.info("========== 알람 규칙 체크 완료: 성공={}, 실패={} ==========",
                checkedCount, failedCount);
    }

    /**
     * 안전하게 지표 체크 (개별 지표 실패 시 다른 지표는 계속 진행)
     */
    private void checkMetricSafely(Connection conn, Instance instance, Database db, String metricType) {
        try {
            alarmCollector.checkMetric(conn, instance.getInstanceId(), db.getDatabaseId(), metricType);
        } catch (Exception e) {
            log.error("지표 체크 실패: instance={}, database={}, metric={}, error={}",
                    instance.getInstanceName(), db.getDatabaseName(), metricType, e.getMessage());
        }
    }

    /**
     * DB 연결 생성
     */
    private Connection createConnection(Instance instance, String databaseName) throws SQLException {
        // ✅ trim()으로 공백 제거
        String host = instance.getHost() != null ? instance.getHost().trim() : "";
        String userName = instance.getUserName() != null ? instance.getUserName().trim() : "";
        String password = aesGcmService.decryptToString(instance.getSecretRef());
        String dbName = databaseName != null ? databaseName.trim() : "";

        String url = String.format("jdbc:postgresql://%s:%d/%s",
                host,
                instance.getPort(),
                dbName);

        return DriverManager.getConnection(url, userName, password);
    }

}