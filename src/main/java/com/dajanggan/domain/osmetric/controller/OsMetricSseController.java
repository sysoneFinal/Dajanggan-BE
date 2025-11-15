package com.dajanggan.domain.osmetric.controller;

import com.dajanggan.domain.osmetric.service.OsMetricSseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * OS Metric SSE 컨트롤러
 * - 프론트엔드에 실시간 OS 메트릭 데이터를 SSE로 전송
 * - Agent가 Redis에 저장한 데이터를 실시간으로 스트리밍
 */
@Slf4j
@RestController
@RequestMapping("/api/sse/os-metrics")
@RequiredArgsConstructor
public class OsMetricSseController {
    
    private final OsMetricSseService sseService;
    
    /**
     * SSE 연결 엔드포인트
     * 
     * @param instanceId 모니터링 인스턴스 ID
     * @return SseEmitter
     */
    @GetMapping(value = "/stream/{instanceId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMetrics(@PathVariable Long instanceId) {
        log.info("SSE 스트림 연결 요청: instanceId={}", instanceId);
        return sseService.createEmitter(instanceId);
    }
    
    /**
     * 특정 인스턴스의 SSE 연결 종료
     * 
     * @param instanceId 인스턴스 ID
     */
    @DeleteMapping("/{instanceId}")
    public void closeStream(@PathVariable Long instanceId) {
        log.info("SSE 스트림 종료 요청: instanceId={}", instanceId);
        sseService.closeAllEmitters(instanceId);
    }
}
