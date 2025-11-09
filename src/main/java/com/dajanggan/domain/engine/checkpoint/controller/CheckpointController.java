package com.dajanggan.domain.engine.checkpoint.controller;

import com.dajanggan.domain.engine.checkpoint.dto.CheckpointDashboardDto;
import com.dajanggan.domain.engine.checkpoint.dto.CheckpointListRequest;
import com.dajanggan.domain.engine.checkpoint.dto.CheckpointListResponse;
import com.dajanggan.domain.engine.checkpoint.service.CheckpointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    /**
     * Checkpoint 리스트 조회 (필터링 + 페이징)
     * GET /api/engine/checkpoint/list?instanceId=1&period=24h&type=timed,requested&status=정상,주의&page=1&size=10
     * @param instanceId 인스턴스 ID (필수)
     * @param period 조회 기간 (1h, 6h, 24h, 7d) - 기본값: 24h
     * @param type Checkpoint 유형 (timed, requested) - 다중 선택 가능
     * @param status 상태 (정상, 주의, 위험) - 다중 선택 가능
     * @param page 페이지 번호 (기본값: 1)
     * @param size 페이지 크기 (기본값: 10)
     * @return 페이징된 Checkpoint 리스트
     */
    @GetMapping("/list")
    public ResponseEntity<CheckpointListResponse> getCheckpointList(
            @RequestParam(name = "instanceId", required = true) Long instanceId,
            @RequestParam(name = "period", required = false, defaultValue = "24h") String period,
            @RequestParam(name = "type", required = false) List<String> type,
            @RequestParam(name = "status", required = false) List<String> status,
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "10") Integer size
    ) {
        log.info("GET /api/engine/checkpoint/list - instanceId: {}, period: {}, type: {}, status: {}, page: {}, size: {}",
                instanceId, period, type, status, page, size);

        CheckpointListRequest request = CheckpointListRequest.builder()
                .instanceId(instanceId)
                .period(period)
                .types(type)
                .statuses(status)
                .page(page)
                .size(size)
                .build();

        CheckpointListResponse response = checkpointService.getCheckpointList(request);
        log.debug("Checkpoint list retrieved successfully - totalElements: {}, totalPages: {}",
                response.getTotalElements(), response.getTotalPages());

        return ResponseEntity.ok(response);
    }
}
