package com.dajanggan.domain.osmetric.controller;

import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.domain.osmetric.dto.AgentOsMetricRequest;
import com.dajanggan.domain.osmetric.dto.RedisOsMetricData;
import com.dajanggan.domain.osmetric.service.OsMetricRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Agent로부터 OS 메트릭 데이터를 수신하는 컨트롤러
 * - Agent는 5초마다 이 엔드포인트로 데이터 전송
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AgentOsMetricController {
    
    private final OsMetricRedisService redisService;
    private final InstanceRepository instanceRepository;
    
    /**
     * Agent로부터 OS 메트릭 데이터 수신 (5초마다 호출됨)
     * 경로: /api/os-metrics (Agent 전송 경로와 일치)
     * 
     * @param request Agent 메트릭 요청 데이터
     * @return 성공 응답
     */
    @PostMapping("/os-metrics")
    public ResponseEntity<String> receiveMetrics(@RequestBody AgentOsMetricRequest request) {
        try {
            log.info("========== Agent 데이터 수신: instanceName={}, metricType={}, timestamp={} ==========", 
                    request.getInstanceName(),
                    request.getMetricType(),
                    request.getTimestamp());
            
            // 1. instanceName으로 instanceId 조회
            Optional<Long> instanceIdOpt = instanceRepository.findIdByInstanceName(
                    request.getInstanceName());
            
            if (instanceIdOpt.isEmpty()) {
                log.warn("인스턴스를 찾을 수 없음: instanceName={}", request.getInstanceName());
                return ResponseEntity.badRequest()
                        .body("ERROR: Instance not found - " + request.getInstanceName());
            }
            
            Long instanceId = instanceIdOpt.get();
            
            // 2. 수집 시각이 없으면 현재 시각 사용
            LocalDateTime collectedAt = request.getTimestamp() != null 
                    ? request.getTimestamp() 
                    : LocalDateTime.now();
            
            // 3. Redis에 저장
            RedisOsMetricData redisData = RedisOsMetricData.builder()
                    .instanceId(instanceId)
                    .metricType(request.getMetricType())
                    .details(request.getDetails())
                    .collectedAt(collectedAt)
                    .build();
            
            redisService.save(redisData);
            
            log.info("Agent 데이터 Redis 저장 완료: instanceId={}, instanceName={}, metricType={}", 
                    instanceId, request.getInstanceName(), request.getMetricType());
            
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
    @GetMapping("/os-metrics/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("OK");
    }
}
