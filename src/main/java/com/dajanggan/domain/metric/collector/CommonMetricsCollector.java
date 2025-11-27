/** 작성자 : 서샘이 */
package com.dajanggan.domain.metric.collector;

import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.repository.DatabaseRepository;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.global.crypto.AesGcmService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final AesGcmService aesGcmService;

    // 병렬 처리용 ExecutorService
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    // 중복 실행 방지용 플래그
    private final AtomicBoolean isCollecting = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        log.info("CommonMetricsCollector 스레드 풀 초기화 완료 (크기: 10)");
    }

    @PreDestroy
    public void destroy() {
        if (executorService != null) {
            executorService.shutdown();
            log.info("CommonMetricsCollector 스레드 풀 종료");
        }
    }

    /** 1분마다 전체 활성화된 데이터베이스의 메트릭 수집 */
    @Scheduled(cron = "0 * * * * *")
    public void collectAllDatabases() {
        // 중복 실행 방지
        if (!isCollecting.compareAndSet(false, true)) {
            log.warn("⚠️ 이전 수집 작업이 아직 실행 중 - 이번 주기 스킵");
            return;
        }

        try {
            doCollect();
        } finally {
            isCollecting.set(false);
        }
    }

    private void doCollect() {
        long totalStartTime = System.currentTimeMillis();
        OffsetDateTime collectedAt = OffsetDateTime.now();
        log.info("========== 원시 데이터 수집 시작 at {} ==========", collectedAt);

        // Step 1: DB 목록 조회
        long step1Start = System.currentTimeMillis();
        List<Database> databases = databaseRepository.findAllEnabled();
        long step1Time = System.currentTimeMillis() - step1Start;

        if (databases.isEmpty()) {
            log.warn("수집 대상 데이터베이스가 없습니다.");
            return;
        }

        // Step 2: 인스턴스 정보 조회 + 복호화
        long step2Start = System.currentTimeMillis();
        List<Long> instanceIds = databases.stream()
                .map(Database::getInstanceId)
                .distinct()
                .toList();

        Map<Long, Instance> instanceMap = instanceRepository.findAllWithSecrets(instanceIds).stream()
                .collect(Collectors.toMap(Instance::getInstanceId, i -> i));

        Map<Long, String> decryptedPasswordMap = new ConcurrentHashMap<>();
        for (Instance instance : instanceMap.values()) {
            try {
                String decrypted = aesGcmService.decryptToString(instance.getSecretRef());
                decryptedPasswordMap.put(instance.getInstanceId(), decrypted);
                log.debug("비밀번호 복호화 완료: instanceId={}, name={}",
                        instance.getInstanceId(), instance.getInstanceName());
            } catch (Exception e) {
                log.error("비밀번호 복호화 실패: instanceId={}, name={}, error={}",
                        instance.getInstanceId(), instance.getInstanceName(), e.getMessage());
            }
        }
        long step2Time = System.currentTimeMillis() - step2Start;

        // Step 3: 병렬 수집
        long step3Start = System.currentTimeMillis();
        List<CompletableFuture<CollectionResult>> futures = databases.stream()
                .map(database -> CompletableFuture.supplyAsync(() -> {
                    Instance instance = instanceMap.get(database.getInstanceId());
                    String decryptedPassword = decryptedPasswordMap.get(database.getInstanceId());
                    return collectForDatabase(database, instance, decryptedPassword, collectedAt);
                }, executorService))
                .toList();

        List<CollectionResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        long step3Time = System.currentTimeMillis() - step3Start;

        // 결과 출력
        long successCount = results.stream().filter(CollectionResult::isSuccess).count();
        long failureCount = results.size() - successCount;
        long totalElapsedTime = System.currentTimeMillis() - totalStartTime;

        log.info("========== 원시 데이터 수집 완료 ==========");
        log.info(">> 1단계 (DB 목록 조회): {}ms", step1Time);
        log.info(">> 2단계 (인스턴스 조회 + 복호화): {}ms", step2Time);
        log.info(">> 3단계 (메트릭 수집): {}ms", step3Time);
        log.info(">> 총 소요시간: {}ms ({} 초)", totalElapsedTime, totalElapsedTime / 1000.0);
        log.info(">> 성공/실패: {}/{}", successCount, failureCount);
        log.info("==========================================");
    }

    private CollectionResult collectForDatabase(Database database, Instance instance,
                                                String decryptedPassword, OffsetDateTime collectedAt) {
        if (instance == null) {
            log.error("************* Database ID {} - 연결된 Instance를 찾을 수 없음 (instance_id: {})",
                    database.getDatabaseId(), database.getInstanceId());
            return CollectionResult.failure();
        }

        if (decryptedPassword == null) {
            log.error("************* Database ID {} - 복호화된 비밀번호가 없음 (instance_id: {})",
                    database.getDatabaseId(), database.getInstanceId());
            return CollectionResult.failure();
        }

        long dbStartTime = System.currentTimeMillis();
        try {
            log.info(">>>>>>>>>>>>>>>> [{}] Collecting metrics from {}:{} / {}",
                    collectedAt,
                    instance.getHost(),
                    instance.getPort(),
                    database.getDatabaseName());

            sessionMetricsCollector.collect(instance, database, decryptedPassword, collectedAt);
            queryMetricsCollector.collect(instance, database, decryptedPassword, collectedAt);
            vacuumMetricsCollector.collect(instance, database, decryptedPassword, collectedAt);

            long dbElapsedTime = System.currentTimeMillis() - dbStartTime;
            log.info("================ [{}] {}:{}/{} 수집 완료 (소요시간: {}ms)",
                    collectedAt,
                    instance.getHost(),
                    instance.getPort(),
                    database.getDatabaseName(),
                    dbElapsedTime);
            return CollectionResult.success();

        } catch (Exception e) {
            long dbElapsedTime = System.currentTimeMillis() - dbStartTime;
            log.error("************* [{}] {}:{}/{} 수집 실패 (소요시간: {}ms): {}",
                    collectedAt,
                    instance.getHost(),
                    instance.getPort(),
                    database.getDatabaseName(),
                    dbElapsedTime,
                    e.getMessage(),
                    e);
            return CollectionResult.failure();
        }
    }

    @Getter
    @AllArgsConstructor
    private static class CollectionResult {
        private final boolean success;

        static CollectionResult success() {
            return new CollectionResult(true);
        }

        static CollectionResult failure() {
            return new CollectionResult(false);
        }
    }
}