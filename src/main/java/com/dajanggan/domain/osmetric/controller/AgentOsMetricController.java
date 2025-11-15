package com.dajanggan.domain.osmetric.controller;

import com.dajanggan.domain.osmetric.dto.AgentOsMetricRequest;
import com.dajanggan.domain.osmetric.dto.RedisOsMetricData;
import com.dajanggan.domain.osmetric.service.OsMetricRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Agent로부터 OS 메트릭 데이터를 수신하는 컨트롤러
 * - Agent는 5초마다 이 엔드포인트로 데이터 전송
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/os-metrics")
@RequiredArgsConstructor
public class AgentOsMetricController {
    
    private final OsMetricRedisService redisService;
    
    /**
     * Agent로부터 OS 메트릭 데이터 수신 (5초마다 호출됨)
     * 
     * @param request Agent 메트릭 요청 데이터
     * @return 성공 응답
     */
    @PostMapping
    public ResponseEntity<String> receiveMetrics(@RequestBody AgentOsMetricRequest request) {
        try {
            log.debug("Agent 데이터 수신: instanceId={}, collectedAt={}, metrics count={}", 
                    request.getInstanceId(), 
                    request.getCollectedAt(), 
                    request.getMetrics().size());
            
            // 수집 시각이 없으면 현재 시각 사용
            LocalDateTime collectedAt = request.getCollectedAt() != null 
                    ? request.getCollectedAt() 
                    : LocalDateTime.now();
            
            // 각 메트릭을 Redis에 저장
            for (AgentOsMetricRequest.OsMetricData metric : request.getMetrics()) {
                RedisOsMetricData redisData = RedisOsMetricData.builder()
                        .instanceId(request.getInstanceId())
                        .metricType(metric.getMetricType())
                        .value(metric.getValue())
                        .collectedAt(collectedAt)
                        .build();
                
                redisService.save(redisData);
            }
            
            log.info("Agent 데이터 Redis 저장 완료: instanceId={}, count={}", 
                    request.getInstanceId(), request.getMetrics().size());
            
            return ResponseEntity.ok("SUCCESS");
            
        } catch (Exception e) {
            log.error("Agent 데이터 수신 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body("ERROR: " + e.getMessage());
        }
    }
    
    /**
     * Agent 연결 상태 확인용 헬스체크 엔드포인트
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("OK");
    }
}
