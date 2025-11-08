package com.dajanggan.domain.engine.checkpoint.controller;

import com.dajanggan.domain.engine.checkpoint.dto.CheckpointDashboardDto;
import com.dajanggan.domain.engine.checkpoint.service.CheckpointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/engine/checkpoint")
@RequiredArgsConstructor
public class CheckpointController {

    private final CheckpointService checkpointService;

    /**
     * Checkpoint 대시보드 데이터 조회
     * GET /api/engine/checkpoint/dashboard?instanceId=1
     * @param instanceId 인스턴스 ID (필수)
     * @return Checkpoint 대시보드 전체 데이터
     */
    @GetMapping("/dashboard")
    public ResponseEntity<CheckpointDashboardDto> getDashboard(
            @RequestParam(name = "instanceId", required = true) Long instanceId
    ) {
        log.info("GET /api/engine/checkpoint/dashboard - instanceId: {}", instanceId);
        CheckpointDashboardDto dashboard = checkpointService.getDashboardData(instanceId);
        log.debug("Checkpoint dashboard data retrieved successfully for instance: {}", instanceId);
        return ResponseEntity.ok(dashboard);
    }
}
