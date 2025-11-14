package com.dajanggan.domain.metric.batch;

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

/**
 * 메트릭 집계 Batch Job 공통 스케줄러
 * 세션, 쿼리, 락 등 모든 1분/5분 집계 Job을 스케줄링
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricAggregationScheduler {

    private final JobLauncher jobLauncher;
    
    @Qualifier("sessionAgg1mJob")
    private final Job sessionAgg1mJob;

    @Qualifier("queryAgg1mJob")
    private final Job queryAgg1mJob;

    @Qualifier("queryAgg5mJob")
    private final Job queryAgg5mJob;
    /**
     * 1분마다 1분 집계 Job들 실행
     * 매분 5초에 실행 (수집이 완료된 후 실행하기 위해)
     */
    @Scheduled(cron = "5 * * * * *")  // 매분 5초
    public void runAgg1mJobs() {
        LocalDateTime runTime = LocalDateTime.now();
        log.info("========== 1분 집계 배치 시작: {} ==========", runTime);
        
        // 세션 1분 집계
        runJob(sessionAgg1mJob, "세션 1분 집계", runTime);
        runJob(queryAgg1mJob, "쿼리 1분 집계", runTime);
        
        log.info("========== 1분 집계 배치 완료 ==========");
    }

    /**
     * 5분마다 5분 집계 Job들 실행
     * 매 5분의 10초에 실행
     */
    @Scheduled(cron = "10 */5 * * * *")  // 매 5분의 10초 (0:10, 5:10, 10:10, ...)
    public void runAgg5mJobs() {
        LocalDateTime runTime = LocalDateTime.now();
        log.info("========== 5분 집계 배치 시작: {} ==========", runTime);
        
        // 향후 추가
        // runJob(sessionAgg5mJob, "세션 5분 집계", runTime);
         runJob(queryAgg5mJob, "쿼리 5분 집계", runTime);
        
        log.info("========== 5분 집계 배치 완료 ==========");
    }

    /**
     * Job 실행 공통 메서드
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
}
