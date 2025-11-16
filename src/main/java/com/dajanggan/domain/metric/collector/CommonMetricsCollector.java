package com.dajanggan.domain.metric.collector;

import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.repository.DatabaseRepository;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommonMetricsCollector {

    private final DatabaseRepository databaseRepository;
    private final InstanceRepository instanceRepository;
    private final SessionMetricsCollector sessionMetricsCollector;
    private final QueryMetricsCollector queryMetricsCollector;
    private final VacuumMetricsCollector vacuumMetricsCollector;


    /** 1분마다 전체 활성화된 데이터베이스의 메트릭 수집 */
    @Scheduled(fixedRate = 60000)  // 60,000ms = 1분
    public void collectAllDatabases() {
        OffsetDateTime collectedAt = OffsetDateTime.now();
        log.info("========== Metric Collection Started at {} ==========", collectedAt);

        // (1) 활성화된 데이터베이스 목록 조회 (is_enabled = true)
        List<Database> databases = databaseRepository.findAllEnabled();
        
        if (databases.isEmpty()) {
            log.warn("수집 대상 데이터베이스가 없습니다. (is_enabled = true인 DB가 없음)");
            return;
        }

        log.info("수집 대상 데이터베이스: {} 개", databases.size());

        for (Database db : databases) {
            log.info("  - DB: {} (database_id={}, instance_id={})",
                    db.getDatabaseName(), db.getDatabaseId(), db.getInstanceId());
        }

        // (2) 인스턴스 정보 한 번에 조회 (N+1 문제 방지) - secret_ref 포함!
        List<Long> instanceIds = databases.stream()
                .map(Database::getInstanceId)
                .distinct()
                .toList();
        
        Map<Long, Instance> instanceMap = instanceRepository.findAllWithSecrets(instanceIds).stream()
                .collect(Collectors.toMap(Instance::getInstanceId, i -> i));

        // (3) 데이터베이스별 수집 실행
        int successCount = 0;
        int failureCount = 0;

        for (Database database : databases) {
            Instance instance = instanceMap.get(database.getInstanceId());

            if (instance == null) {
                log.error("❌ Database ID {} - 연결된 Instance를 찾을 수 없음 (instance_id: {})",
                        database.getDatabaseId(), database.getInstanceId());
                failureCount++;
                continue;
            }

            try {
                log.info("▶▶▶ [{}] Collecting metrics from {}:{} / {}",
                        collectedAt,
                        instance.getHost(),
                        instance.getPort(),
                        database.getDatabaseName());

                // 세션 메트릭 수집
                try {
                    log.info("📊 1️⃣ 세션 메트릭 수집 시작");
                    sessionMetricsCollector.collect(instance, database, collectedAt);
                    log.info("📊 1️⃣ 세션 메트릭 수집 완료 ✅");
                } catch (Exception e) {
                    log.error("📊 1️⃣ 세션 메트릭 수집 실패 ❌", e);
                }

                // 쿼리 메트릭 수집
                try {
                    log.info("📊 2️⃣ 쿼리 메트릭 수집 시작");
                    queryMetricsCollector.collect(instance, database, collectedAt);
                    log.info("📊 2️⃣ 쿼리 메트릭 수집 완료 ✅");
                } catch (Exception e) {
                    log.error("📊 2️⃣ 쿼리 메트릭 수집 실패 ❌", e);
                }

                // Vacuum 메트릭 수집
                try {
                    log.info("📊 3️⃣ Vacuum 메트릭 수집 시작");
                    vacuumMetricsCollector.collect(instance, database, collectedAt);
                    log.info("📊 3️⃣ Vacuum 메트릭 수집 완료 ✅");
                } catch (Exception e) {
                    log.error("📊 3️⃣ Vacuum 메트릭 수집 실패 ❌", e);
                }

                successCount++;
                log.info("✅ {} 수집 완료", database.getDatabaseName());

            } catch (Exception e) {
                failureCount++;
                log.error("❌ [{}] {}:{}/{} 전체 수집 실패",
                        collectedAt,
                        instance.getHost(),
                        instance.getPort(),
                        database.getDatabaseName(),
                        e);
            }
        }
        log.info("========== Metric Collection Completed: Success={}, Failure={} ==========", 
                successCount, failureCount);
    }
}
