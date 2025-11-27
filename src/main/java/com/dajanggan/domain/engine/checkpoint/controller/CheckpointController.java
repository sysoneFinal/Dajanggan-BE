// 작성자 : 김동현
package com.dajanggan.domain.engine.checkpoint.controller;

import com.dajanggan.domain.engine.checkpoint.dto.*;
import com.dajanggan.domain.engine.checkpoint.service.CheckpointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "Checkpoint", description = "Checkpoint 메트릭 모니터링 API")
@RestController
@RequestMapping("/api/engine/checkpoint")
@RequiredArgsConstructor
public class CheckpointController {

    private final CheckpointService checkpointService;

    /**
     * Checkpoint 대시보드 데이터 조회
     */
    @Operation(summary = "Checkpoint 대시보드 조회", description = "실시간 Checkpoint 빈도, 평균 시간 등 대시보드 정보 조회")
    @GetMapping
    public ResponseEntity<CheckpointDashboardResponse> getCheckpointDashboard(
            @Parameter(description = "PostgreSQL 인스턴스 ID", example = "1")
            @RequestParam(required = false) Long instanceId) {
        log.debug("Checkpoint 대시보드 조회 요청 - instanceId: {}", instanceId);
        
        CheckpointDashboardResponse response = checkpointService.getCheckpointDashboard(instanceId);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Checkpoint 리스트 데이터 조회
     */
    @Operation(summary = "Checkpoint 리스트 조회", description = "시간 범위 및 상태별로 필터링된 Checkpoint 메트릭 리스트 조회 (페이징)")
    @GetMapping("/list")
    public ResponseEntity<CheckpointListResponse> getCheckpointList(
            @Parameter(description = "PostgreSQL 인스턴스 ID", example = "1")
            @RequestParam(required = false) Long instanceId,
            @Parameter(description = "시간 범위 (1h, 6h, 24h, 7d)", example = "24h")
            @RequestParam(defaultValue = "24h") String timeRange,
            @Parameter(description = "상태 필터 (정상,주의,위험 - 콤마 구분)", example = "주의,위험")
            @RequestParam(required = false) String status,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "페이지당 항목 수", example = "20")
            @RequestParam(defaultValue = "20") Integer size) {
        log.debug("Checkpoint 리스트 조회 요청 - instanceId: {}, timeRange: {}, status: {}, page: {}, size: {}", 
                instanceId, timeRange, status, page, size);
        
        // status 파라미터를 List로 변환
        List<String> statusList = null;
        if (status != null && !status.isEmpty()) {
            statusList = List.of(status.split(","));
        }
        
        CheckpointListResponse response = checkpointService.getCheckpointList(instanceId, timeRange, statusList, page, size);
        
        return ResponseEntity.ok(response);
    }
}
