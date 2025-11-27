/** 작성자 : 서샘이 */
package com.dajanggan.domain.metric.batch;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 메트릭 집계 Batch Job 공통 스케줄러 (병렬 처리)
 * 세션, 쿼리, 락 등 모든 1분/5분 집계 Job을 병렬로 스케줄링
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MetricAggregationScheduler {

    private final JobLauncher jobLauncher;

    @Qualifier("sessionAgg1mJob")
    private final Job sessionAgg1mJob;

    @Qualifier("sessionAgg5mJob")
    private final Job sessionAgg5mJob;

    @Qualifier("queryAgg1mJob")
    private final Job queryAgg1mJob;

    @Qualifier("databaseMetricsAggJob")
    private final Job databaseMetricsAggJob;

    @Qualifier("queryAgg5mJob")
    private final Job queryAgg5mJob;

    @Qualifier("vacuumAgg1mJob")
    private final Job vacuumAgg1mJob;

    @Qualifier("vacuumAgg5mJob")
    private final Job vacuumAgg5mJob;

    @Qualifier("cpuAgg1mJob")
    private final Job cpuAgg1mJob;

    @Qualifier("diskIoAgg1mJob")
    private final Job diskIoAgg1mJob;

    @Qualifier("cpuAgg5mJob")
    private final Job cpuAgg5mJob;

    @Qualifier("diskIoAgg5mJob")
    private final Job diskIoAgg5mJob;

    @Qualifier("memoryAgg1mJob")
    private final Job memoryAgg1mJob;

    @Qualifier("memoryAgg5mJob")
    private final Job memoryAgg5mJob;

    @Qualifier("checkpointAgg1mJob")
    private final Job checkpointAgg1mJob;

    @Qualifier("checkpointAgg5mJob")
    private final Job checkpointAgg5mJob;

    @Qualifier("bgWriterAgg1mJob")
    private final Job bgWriterAgg1mJob;

    @Qualifier("bgWriterAgg5mJob")
    private final Job bgWriterAgg5mJob;

    // 병렬 실행을 위한 ExecutorService (스레드 풀 크기: 10)
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    /**
     * 1분마다 1분 집계 Job들 병렬 실행
     * 매분 30초에 실행
     */
    @Scheduled(cron = "30 * * * * *")
    public void runAgg1mJobs() {
        LocalDateTime runTime = LocalDateTime.now();
        log.info("========== 1분 집계 배치 시작 (병렬): {} ==========", runTime);

        List<CompletableFuture<Void>> futures = List.of(
                runJobAsync(sessionAgg1mJob, "세션 1분 집계", runTime),
                runJobAsync(queryAgg1mJob, "쿼리 1분 집계", runTime),
                runJobAsync(vacuumAgg1mJob, "Vacuum 1분 집계", runTime),
                runJobAsync(cpuAgg1mJob, "CPU 1분 집계", runTime),
                runJobAsync(bgWriterAgg1mJob, "BGWriter 1분 집계", runTime),
                runJobAsync(diskIoAgg1mJob, "Disk I/O 1분 집계", runTime),
                runJobAsync(memoryAgg1mJob, "Memory 1분 집계", runTime),
                runJobAsync(checkpointAgg1mJob, "Checkpoint 1분 집계", runTime)
        );

        // 모든 Job 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("========== 1분 집계 배치 완료 (병렬) ==========");
    }

    /**
     * 5분마다 5분 집계 Job들 병렬 실행
     * 매 45분의 10초에 실행
     */
    @Scheduled(cron = "10 */45 * * * *")
    public void runAgg5mJobs() {
        LocalDateTime runTime = LocalDateTime.now();
        log.info("========== 5분 집계 배치 시작 (병렬): {} ==========", runTime);

        List<CompletableFuture<Void>> futures = List.of(
                runJobAsync(sessionAgg5mJob, "세션 5분 집계", runTime),
                runJobAsync(queryAgg5mJob, "쿼리 5분 집계", runTime),
                runJobAsync(vacuumAgg5mJob, "Vacuum 5분 집계", runTime),
                runJobAsync(cpuAgg5mJob, "CPU 5분 집계", runTime),
                runJobAsync(bgWriterAgg5mJob, "BGWriter 5분 집계", runTime),
                runJobAsync(diskIoAgg5mJob, "Disk I/O 5분 집계", runTime),
                runJobAsync(memoryAgg5mJob, "Memory 5분 집계", runTime),
                runJobAsync(checkpointAgg5mJob, "Checkpoint 5분 집계", runTime)
        );

        // 모든 Job 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("========== 5분 집계 배치 완료 (병렬) ==========");
    }

    /**
     * 데이터베이스 메트릭 집계 (매 1분마다, 30초에 실행)
     * - 도메인별 1분 집계가 완료된 후 실행되도록 시간 조정
     */
    @Scheduled(cron = "30 * * * * *")
    public void aggregateDatabaseMetrics() {
        LocalDateTime runTime = LocalDateTime.now();
        log.info("=== 데이터베이스 메트릭 집계 작업 시작: {} ===", runTime);

        runJob(databaseMetricsAggJob, "데이터베이스 메트릭 집계", runTime);

        log.info("=== 데이터베이스 메트릭 집계 작업 완료 ===");
    }

    /**
     * Job 비동기 실행 메서드
     */
    private CompletableFuture<Void> runJobAsync(Job job, String jobName, LocalDateTime runTime) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info(">>>>>>>>>>>>>>>>>>>>>> {} 시작 (Thread: {})",
                        jobName, Thread.currentThread().getName());

                JobParameters params = new JobParametersBuilder()
                        .addLocalDateTime("runTime", runTime)
                        .toJobParameters();

                jobLauncher.run(job, params);

                log.info("{} 완료", jobName);

            } catch (Exception e) {
                log.error("{} 실패: {}", jobName, e.getMessage(), e);
            }
        }, executorService);
    }

    /**
     * Job 동기 실행 메서드 (데이터베이스 메트릭 집계용)
     */
    private void runJob(Job job, String jobName, LocalDateTime runTime) {
        try {
            log.info(">>>>>>>>>>>>>>>>>>>>>> {} 시작", jobName);

            JobParameters params = new JobParametersBuilder()
                    .addLocalDateTime("runTime", runTime)
                    .toJobParameters();

            jobLauncher.run(job, params);

            log.info("{} 완료", jobName);
        } catch (Exception e) {
            log.error("{} 실패: {}", jobName, e.getMessage(), e);
        }
    }

    /**
     * 애플리케이션 종료 시 ExecutorService 정리
     */
    @PreDestroy
    public void shutdown() {
        log.info("ExecutorService 종료 시작");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("ExecutorService 종료 완료");
    }
}
