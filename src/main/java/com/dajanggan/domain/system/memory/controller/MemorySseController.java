// 작성자 : 김동현
package com.dajanggan.domain.system.memory.controller;

import com.dajanggan.domain.system.memory.service.MemorySseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Memory SSE 컨트롤러
 * - 프론트엔드에 실시간 Memory 메트릭 데이터를 SSE로 전송
 */
@Slf4j
@Tag(name = "Memory SSE", description = "Memory 메트릭 실시간 스트리밍 API (Server-Sent Events)")
@RestController
@RequestMapping("/api/system/memory")
@RequiredArgsConstructor
public class MemorySseController {
    
    private final MemorySseService memorySseService;
    
    /**
     * Memory 실시간 데이터 SSE 연결
     * 
     * @param instanceId 모니터링 인스턴스 ID
     * @return SseEmitter
     */
    @Operation(
        summary = "Memory 메트릭 실시간 스트리밍 연결", 
        description = "Server-Sent Events를 통해 실시간 Memory 메트릭 데이터를 스트리밍"
    )
    @GetMapping(value = "/realtime", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMemoryMetrics(
            @Parameter(description = "PostgreSQL 인스턴스 ID", required = true, example = "1")
            @RequestParam Long instanceId) {
        log.info("Memory SSE 스트림 연결 요청: instanceId={}", instanceId);
        return memorySseService.createEmitter(instanceId);
    }
    
    /**
     * 특정 인스턴스의 Memory SSE 연결 종료
     * 
     * @param instanceId 인스턴스 ID
     */
    @Operation(summary = "Memory SSE 스트림 종료", description = "특정 인스턴스의 모든 Memory SSE 연결 종료")
    @DeleteMapping("/realtime/{instanceId}")
    public void closeStream(
            @Parameter(description = "PostgreSQL 인스턴스 ID", required = true, example = "1")
            @PathVariable Long instanceId) {
        log.info("Memory SSE 스트림 종료 요청: instanceId={}", instanceId);
        memorySseService.closeAllEmitters(instanceId);
    }
}
