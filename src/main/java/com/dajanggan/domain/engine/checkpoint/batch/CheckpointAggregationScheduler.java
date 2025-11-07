package com.dajanggan.domain.engine.checkpoint.batch;

import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Checkpoint Aggregation Scheduler
 * - 1분마다 실행
 * - 모든 활성화된 인스턴스의 Checkpoint 데이터 집계
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckpointAggregationScheduler {

    private final CheckpointAggregationService aggregationService;
    private final InstanceRepository instanceRepository;

    /**
     * 1분마다 Checkpoint 데이터 집계
     * - fixedRate: 이전 작업 시작 시점부터 1분 후 재실행
     * - initialDelay: 애플리케이션 시작 후 60초 대기 (수집 데이터 확보)
     */
    @Scheduled(fixedRate = 60000, initialDelay = 60000)
    public void aggregateCheckpointData() {
        log.info("========== Checkpoint 집계 시작 ==========");

        try {
            // 1. 모든 인스턴스 조회
            List<Instance> instances = instanceRepository.findAll();

            if (instances.isEmpty()) {
                log.warn("등록된 인스턴스가 없습니다. Checkpoint 집계를 건너뜁니다.");
                return;
            }

            log.info("등록된 인스턴스 {}개 발견", instances.size());

            // 2. 집계 시간 범위 설정 (이전 1분)
            OffsetDateTime endTime = OffsetDateTime.now().truncatedTo(ChronoUnit.MINUTES);
            OffsetDateTime startTime = endTime.minusMinutes(1);

            log.debug("집계 기간: {} ~ {}", startTime, endTime);

            // 3. 각 인스턴스별로 집계 수행
            int successCount = 0;
            int failCount = 0;

            for (Instance instance : instances) {
                try {
                    aggregationService.aggregateHourlyData(
                            instance.getInstanceId(),
                            startTime,
                            endTime
                    );
                    successCount++;
                    log.debug("Checkpoint 집계 완료: {} ({})",
                            instance.getInstanceName(), instance.getInstanceId());
                } catch (Exception e) {
                    failCount++;
                    log.error("Checkpoint 집계 실패: {} ({})",
                            instance.getInstanceName(), instance.getInstanceId(), e);
                    // 다른 인스턴스 집계를 위해 계속 진행
                }
            }

            log.info("Checkpoint 집계 완료 - 성공: {}, 실패: {}", successCount, failCount);

        } catch (Exception e) {
            log.error("Checkpoint 집계 중 예상치 못한 오류 발생", e);
        }

        log.info("========== Checkpoint 집계 종료 ==========");
    }
}
