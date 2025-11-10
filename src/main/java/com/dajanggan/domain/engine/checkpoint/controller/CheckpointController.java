package com.dajanggan.domain.engine.checkpoint.controller;

import com.dajanggan.domain.engine.checkpoint.dto.CheckpointDto;
import com.dajanggan.domain.engine.checkpoint.service.CheckpointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/engine/checkpoint")
@RequiredArgsConstructor
public class CheckpointController {

    private final CheckpointService checkpointService;

    /**
     * Checkpoint 대시보드 데이터 조회
     * @param instanceId PostgreSQL 인스턴스 ID (optional, 기본값은 설정된 기본 인스턴스)
     * @return Checkpoint 대시보드 데이터
     */
    @GetMapping
    public ResponseEntity<CheckpointDto.DashboardResponse> getCheckpointDashboard(
            @RequestParam(required = false) Long instanceId) {
        log.debug("Checkpoint 대시보드 조회 요청 - instanceId: {}", instanceId);
        
        CheckpointDto.DashboardResponse response = checkpointService.getCheckpointDashboard(instanceId);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Checkpoint 리스트 데이터 조회
     * @param instanceId PostgreSQL 인스턴스 ID (optional)
     * @param timeRange 시간 범위 (1h, 6h, 24h, 7d)
     * @param status 상태 필터 (정상, 주의, 위험) - 콤마로 구분
     * @return Checkpoint 리스트 데이터
     */
    @GetMapping("/list")
    public ResponseEntity<CheckpointDto.ListResponse> getCheckpointList(
            @RequestParam(required = false) Long instanceId,
            @RequestParam(defaultValue = "7d") String timeRange,
            @RequestParam(required = false) String status) {
        log.debug("Checkpoint 리스트 조회 요청 - instanceId: {}, timeRange: {}, status: {}", 
                instanceId, timeRange, status);
        
        // status 파라미터를 List로 변환
        List<String> statusList = null;
        if (status != null && !status.isEmpty()) {
            statusList = List.of(status.split(","));
        }
        
        CheckpointDto.ListResponse response = checkpointService.getCheckpointList(instanceId, timeRange, statusList);
        
        return ResponseEntity.ok(response);
    }
}
