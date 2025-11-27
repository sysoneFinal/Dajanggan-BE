// 작성자 : 김동현
package com.dajanggan.global.scheduler;

import com.dajanggan.domain.osmetric.service.OsMetricSseService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SSE 브로드캐스트 스케줄러
 * - 5초마다 실행하여 Redis의 실시간 데이터를 모든 연결된 클라이언트에게 전송
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseBroadcastScheduler {

    private final OsMetricSseService osMetricSseService;

    @PostConstruct
    public void init() {
        log.info("========== SseBroadcastScheduler 초기화 완료 ==========");
    }

    /**
     * 5초마다 실행 - OS 메트릭 브로드캐스트
     * Agent가 5초마다 데이터를 보내므로, 동일한 주기로 브로드캐스트
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void broadcastOsMetrics() {
        try {
            log.info("========== SSE 브로드캐스트 시작 ==========");
            osMetricSseService.broadcastAllInstances();
            log.info("========== SSE 브로드캐스트 완료 ==========");
        } catch (Exception e) {
            log.error("SSE 브로드캐스트 중 오류 발생", e);
        }
    }
}
