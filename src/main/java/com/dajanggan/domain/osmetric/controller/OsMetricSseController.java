// 작성자 : 김동현
package com.dajanggan.domain.osmetric.controller;

import com.dajanggan.domain.osmetric.service.OsMetricSseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * OS Metric SSE 컨트롤러
 */
@Slf4j
@Tag(name = "OS Metrics SSE", description = "OS 메트릭 실시간 스트리밍 API (Server-Sent Events)")
@RestController
@RequestMapping("/api/osmetric")
@RequiredArgsConstructor
public class OsMetricSseController {
    
    private final OsMetricSseService sseService;
    
    /**
     * SSE 연결 엔드포인트
     */
    @Operation(
        summary = "OS 메트릭 실시간 스트리밍 연결", 
        description = "Server-Sent Events를 통해 실시간 OS 메트릭 데이터를 스트리밍 (5초마다 갱신)"
    )
    @GetMapping(value = "/stream/{instanceId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMetrics(
            @Parameter(description = "PostgreSQL 인스턴스 ID", required = true, example = "1")
            @PathVariable Long instanceId) {
        log.info("SSE 스트림 연결 요청: instanceId={}", instanceId);
        return sseService.createEmitter(instanceId);
    }
    
    /**
     * 특정 인스턴스의 SSE 연결 종료
     */
    @Operation(summary = "SSE 스트림 종료", description = "특정 인스턴스의 모든 SSE 연결 종료")
    @DeleteMapping("/{instanceId}")
    public void closeStream(
            @Parameter(description = "PostgreSQL 인스턴스 ID", required = true, example = "1")
            @PathVariable Long instanceId) {
        log.info("SSE 스트림 종료 요청: instanceId={}", instanceId);
        sseService.closeAllEmitters(instanceId);
    }
}
