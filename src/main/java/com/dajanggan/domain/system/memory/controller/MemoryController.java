package com.dajanggan.domain.system.memory.controller;

import com.dajanggan.domain.system.memory.dto.*;
import com.dajanggan.domain.system.memory.service.MemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/system/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;

    /**
     * Memory 대시보드 데이터 조회
     * @param instanceId PostgreSQL 인스턴스 ID
     * @return Memory 대시보드 데이터
     */
    @GetMapping
    public ResponseEntity<MemoryDashboardResponse> getMemoryDashboard(
            @RequestParam Long instanceId) {
        log.info("========== Memory 대시보드 조회 API 호출: instanceId={} ==========", instanceId);

        MemoryDashboardResponse response = memoryService.getMemoryDashboard(instanceId);

        return ResponseEntity.ok(response);
    }

    /**
     * Memory 리스트 데이터 조회
     * @param instanceId PostgreSQL 인스턴스 ID
     * @param timeRange 시간 범위 (1h, 6h, 24h, 7d)
     * @param status 상태 필터 (정상, 주의, 위험) - 콤마로 구분
     * @return Memory 리스트 데이터
     */
    @GetMapping("/list")
    public ResponseEntity<MemoryListResponse> getMemoryList(
            @RequestParam Long instanceId,
            @RequestParam(defaultValue = "24h") String timeRange,
            @RequestParam(required = false) String status) {
        log.info("Memory 리스트 조회 API 호출 - instanceId: {}, timeRange: {}, status: {}",
                instanceId, timeRange, status);

        // status 파라미터를 List로 변환
        List<String> statusList = null;
        if (status != null && !status.isEmpty()) {
            statusList = List.of(status.split(","));
        }

        MemoryListResponse response = memoryService.getMemoryList(instanceId, timeRange, statusList);

        return ResponseEntity.ok(response);
    }
}