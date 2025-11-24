package com.dajanggan.domain.osmetric.scheduler;

import com.dajanggan.domain.osmetric.service.OsMetricSseService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * OS Metric SSE 브로드캐스트 스케줄러
 * - 5초마다 Redis의 실시간 데이터를 SSE로 프론트엔드에 전송
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OsMetricSseBroadcastScheduler {

    private final OsMetricSseService sseService;

    @PostConstruct
    public void init() {
        log.info("========== OsMetricSseBroadcastScheduler 초기화 완료 ==========");
    }

    /**
     * 5초마다 실행 (Agent가 5초마다 데이터를 보내므로 동기화)
     * Redis의 최신 데이터를 SSE로 모든 연결된 클라이언트에게 브로드캐스트
     */
    @Scheduled(fixedRate = 5000)
    public void broadcastRealtimeMetrics() {
        try {
            sseService.broadcastAllInstances();
        } catch (Exception e) {
            log.error("SSE 브로드캐스트 중 오류 발생", e);
        }
    }
}









