// 작성자 : 김동현
package com.dajanggan.domain.system.memory.controller;

import com.dajanggan.domain.system.memory.service.MemorySseService;
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
    @GetMapping(value = "/realtime", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMemoryMetrics(@RequestParam Long instanceId) {
        log.info("Memory SSE 스트림 연결 요청: instanceId={}", instanceId);
        return memorySseService.createEmitter(instanceId);
    }
    
    /**
     * 특정 인스턴스의 Memory SSE 연결 종료
     * 
     * @param instanceId 인스턴스 ID
     */
    @DeleteMapping("/realtime/{instanceId}")
    public void closeStream(@PathVariable Long instanceId) {
        log.info("Memory SSE 스트림 종료 요청: instanceId={}", instanceId);
        memorySseService.closeAllEmitters(instanceId);
    }
}
