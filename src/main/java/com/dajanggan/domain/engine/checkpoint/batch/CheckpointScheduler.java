package com.dajanggan.domain.engine.checkpoint.batch;

import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Checkpoint 데이터 수집 Scheduler
 * - 1분마다 실행
 * - 모든 활성화된 인스턴스의 Checkpoint 데이터 수집
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckpointScheduler {

    private final CheckpointCollectService checkpointCollectService;
    private final InstanceRepository instanceRepository;

    /**
     * 1분마다 Checkpoint 데이터 수집
     * - fixedRate: 이전 작업 시작 시점부터 1분 후 재실행
     * - initialDelay: 애플리케이션 시작 후 10초 대기
     */
    @Scheduled(fixedRate = 60000, initialDelay = 10000)
    public void collectCheckpointData() {
        log.info("========== Checkpoint 수집 시작 ==========");

        try {
            // 1. 모든 인스턴스 조회
            List<Instance> instances = instanceRepository.findAll();

            if (instances.isEmpty()) {
                log.warn("등록된 인스턴스가 없습니다. Checkpoint 수집을 건너뜁니다.");
                return;
            }

            log.info("등록된 인스턴스 {}개 발견", instances.size());

            // 2. 각 인스턴스별로 데이터 수집
            int successCount = 0;
            int failCount = 0;

            for (Instance instance : instances) {
                try {
                    checkpointCollectService.collectCheckpointData(instance.getInstanceId());
                    successCount++;
                    log.debug("Checkpoint 수집 완료: {} ({})", 
                             instance.getInstanceName(), instance.getInstanceId());
                } catch (Exception e) {
                    failCount++;
                    log.error("Checkpoint 수집 실패: {} ({})", 
                             instance.getInstanceName(), instance.getInstanceId(), e);
                    // 다른 인스턴스 수집을 위해 계속 진행
                }
            }

            log.info("Checkpoint 수집 완료 - 성공: {}, 실패: {}", successCount, failCount);

        } catch (Exception e) {
            log.error("Checkpoint 수집 중 예상치 못한 오류 발생", e);
        }

        log.info("========== Checkpoint 수집 종료 ==========");
    }
}
