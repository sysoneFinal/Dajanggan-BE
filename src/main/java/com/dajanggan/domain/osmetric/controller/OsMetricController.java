package com.dajanggan.domain.osmetric.controller;

import com.dajanggan.domain.osmetric.dto.OsMetricAggResponse;
import com.dajanggan.domain.osmetric.dto.OsMetricResponse;
import com.dajanggan.domain.osmetric.service.OsMetricService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * OS 메트릭 조회 API 컨트롤러
 * (Agent 데이터 수신은 AgentOsMetricController가 담당)
 */
@Slf4j
@Tag(name = "OS Metrics", description = "OS 레벨 메트릭 조회 API")
@RestController
@RequestMapping("/api/os-metrics")
@RequiredArgsConstructor
public class OsMetricController {
    
    private final OsMetricService osMetricService;
    
    /**
     * 실시간 메트릭 조회 (Redis)
     * 최근 5분간의 데이터 (5초 간격)
     */
    @Operation(summary = "실시간 메트릭 조회", description = "Redis에서 최근 5분간의 실시간 메트릭 조회")
    @GetMapping("/realtime/{instanceId}/{metricType}")
    public ResponseEntity<List<OsMetricResponse>> getRealTimeMetrics(
            @PathVariable Long instanceId,
            @PathVariable String metricType) {
        
        log.debug("Getting real-time metrics: instanceId={}, metricType={}", instanceId, metricType);
        
        List<OsMetricResponse> metrics = osMetricService.getRealTimeMetrics(instanceId, metricType);
        
        return ResponseEntity.ok(metrics);
    }
    
    /**
     * 과거 집계 데이터 조회 (PostgreSQL)
     * 1분 단위 집계 데이터
     */
    @Operation(summary = "과거 집계 데이터 조회", description = "PostgreSQL에서 1분 단위 집계 데이터 조회")
    @GetMapping("/aggregated/{instanceId}/{metricType}")
    public ResponseEntity<List<OsMetricAggResponse>> getAggregatedMetrics(
            @PathVariable Long instanceId,
            @PathVariable String metricType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        
        log.debug("Getting aggregated metrics: instanceId={}, metricType={}, period=[{} ~ {}]", 
                instanceId, metricType, startTime, endTime);
        
        OffsetDateTime start = OffsetDateTime.ofInstant(
                startTime.atZone(ZoneId.systemDefault()).toInstant(), 
                ZoneId.systemDefault());
        OffsetDateTime end = OffsetDateTime.ofInstant(
                endTime.atZone(ZoneId.systemDefault()).toInstant(), 
                ZoneId.systemDefault());
        
        List<OsMetricAggResponse> metrics = osMetricService.getAggregatedMetrics(
                instanceId, metricType, start, end);
        
        return ResponseEntity.ok(metrics);
    }
}
