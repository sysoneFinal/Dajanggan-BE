package com.dajanggan.domain.system.memory.service;

import com.dajanggan.domain.system.memory.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Memory SSE 서비스
 * - 실시간 Memory 메트릭을 SSE로 전송
 * - 5초마다 실시간 위젯 데이터를 전송
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemorySseService {

    private final MemoryService memoryService;
    private final ObjectMapper objectMapper;

    // instanceId -> List<SseEmitter>
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // SSE 타임아웃 (30분)
    private static final Long TIMEOUT = 30 * 60 * 1000L;

    /**
     * SSE Emitter 생성
     */
    public SseEmitter createEmitter(Long instanceId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT);

        // 인스턴스별 emitter 리스트 가져오기 (없으면 생성)
        CopyOnWriteArrayList<SseEmitter> instanceEmitters = emitters.computeIfAbsent(
                instanceId,
                k -> new CopyOnWriteArrayList<>()
        );

        // emitter 추가
        instanceEmitters.add(emitter);
        log.info("SSE Emitter 추가: instanceId={}, 현재 연결 수={}", instanceId, instanceEmitters.size());

        // 타임아웃 또는 에러 시 제거
        emitter.onTimeout(() -> {
            log.warn("SSE 타임아웃: instanceId={}", instanceId);
            removeEmitter(instanceId, emitter);
        });

        emitter.onError(e -> {
            log.error("SSE 에러: instanceId={}", instanceId, e);
            removeEmitter(instanceId, emitter);
        });

        emitter.onCompletion(() -> {
            log.info("SSE 연결 종료: instanceId={}", instanceId);
            removeEmitter(instanceId, emitter);
        });

        // 초기 연결 메시지 전송
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("Connected to Memory metrics stream"));
        } catch (IOException e) {
            log.error("초기 연결 메시지 전송 실패: instanceId={}", instanceId, e);
            removeEmitter(instanceId, emitter);
        }

        return emitter;
    }

    /**
     * Emitter 제거
     */
    private void removeEmitter(Long instanceId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> instanceEmitters = emitters.get(instanceId);
        if (instanceEmitters != null) {
            instanceEmitters.remove(emitter);
            log.info("SSE Emitter 제거: instanceId={}, 남은 연결 수={}", instanceId, instanceEmitters.size());

            // 연결이 없으면 맵에서 제거
            if (instanceEmitters.isEmpty()) {
                emitters.remove(instanceId);
                log.info("인스턴스의 모든 연결 종료: instanceId={}", instanceId);
            }
        }
    }

    /**
     * 특정 인스턴스의 모든 Emitter 닫기
     */
    public void closeAllEmitters(Long instanceId) {
        CopyOnWriteArrayList<SseEmitter> instanceEmitters = emitters.remove(instanceId);
        if (instanceEmitters != null) {
            instanceEmitters.forEach(emitter -> {
                try {
                    emitter.complete();
                } catch (Exception e) {
                    log.error("Emitter 종료 실패", e);
                }
            });
            log.info("인스턴스의 모든 SSE 연결 종료: instanceId={}, 종료된 연결 수={}", 
                    instanceId, instanceEmitters.size());
        }
    }

    /**
     * 5초마다 실시간 Memory 메트릭 전송
     */
    @Scheduled(fixedRate = 5000)
    public void sendRealtimeMetrics() {
        if (emitters.isEmpty()) {
            return;
        }

        emitters.forEach((instanceId, instanceEmitters) -> {
            if (instanceEmitters.isEmpty()) {
                return;
            }

            try {
                // 실시간 위젯 데이터 조회
                RealtimeMemoryMetrics realtimeData = getRealtimeMetrics(instanceId);

                // JSON 변환
                String jsonData = objectMapper.writeValueAsString(realtimeData);

                // 모든 emitter에 전송 (실패한 emitter는 자동 제거)
                instanceEmitters.removeIf(emitter -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("memory-metrics")
                                .data(jsonData));
                        return false; // 성공하면 유지
                    } catch (IOException e) {
                        log.warn("SSE 메트릭 전송 실패 (연결 끊김): instanceId={}", instanceId);
                        try {
                            emitter.completeWithError(e);
                        } catch (Exception ex) {
                            // ignore
                        }
                        return true; // 실패하면 제거
                    }
                });

                log.debug("Memory 메트릭 전송 완료: instanceId={}, 전송된 연결 수={}", 
                        instanceId, instanceEmitters.size());

                // 모든 emitter가 제거되었으면 인스턴스도 제거
                if (instanceEmitters.isEmpty()) {
                    emitters.remove(instanceId);
                    log.info("인스턴스의 모든 SSE 연결 종료됨: instanceId={}", instanceId);
                }

            } catch (Exception e) {
                log.error("실시간 Memory 메트릭 조회 실패: instanceId={}", instanceId, e);
            }
        });
    }

    /**
     * 실시간 위젯 데이터 조회
     */
    private RealtimeMemoryMetrics getRealtimeMetrics(Long instanceId) {
        // MemoryService에서 위젯 데이터 조회
        MemoryDashboardResponse.OsMemoryUsageWidget osMemoryUsage = memoryService.getOsMemoryUsageWidget(instanceId);
        MemoryDashboardResponse.SwapUsageWidget swapUsage = memoryService.getSwapUsageWidget(instanceId);
        MemoryDashboardResponse.SharedBufferHitWidget sharedBufferHit = memoryService.getSharedBufferHitWidget(instanceId);
        MemoryDashboardResponse.BufferUsageWidget bufferUsage = memoryService.getBufferUsageWidget(instanceId);
        MemoryDashboardResponse.TempFileUsageWidget tempFileUsage = memoryService.getTempFileUsageWidget(instanceId);

        return RealtimeMemoryMetrics.builder()
                .osMemoryUsage(osMemoryUsage)
                .swapUsage(swapUsage)
                .sharedBufferHit(sharedBufferHit)
                .bufferUsage(bufferUsage)
                .tempFileUsage(tempFileUsage)
                .build();
    }
}
