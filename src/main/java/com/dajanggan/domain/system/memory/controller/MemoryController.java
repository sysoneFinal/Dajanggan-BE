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

        try {
            MemoryDashboardResponse response = memoryService.getMemoryDashboard(instanceId);
            log.info("========== Memory 대시보드 조회 완료: instanceId={} ==========", instanceId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("========== Memory 대시보드 조회 실패: instanceId={} ==========", instanceId, e);
            throw e;
        }
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

    /**
     * 낮은 캐시 히트율 테이블 리스트 조회
     * @param instanceId PostgreSQL 인스턴스 ID
     * @param timeRange 시간 범위 (1h, 6h, 24h, 7d)
     * @param status 상태 필터 (정상, 주의, 위험) - 콤마로 구분
     * @param type 타입 필터 (table, index) - 콤마로 구분
     * @return 낮은 캐시 히트율 테이블 리스트
     */
    @GetMapping("/list/low-cache-hit")
    public ResponseEntity<List<MemoryListResponse.LowCacheHitItem>> getLowCacheHitList(
            @RequestParam Long instanceId,
            @RequestParam(defaultValue = "24h") String timeRange,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        log.info("낮은 캐시 히트율 리스트 조회 API 호출 - instanceId: {}, timeRange: {}, status: {}, type: {}",
                instanceId, timeRange, status, type);

        List<String> statusList = null;
        if (status != null && !status.isEmpty()) {
            statusList = List.of(status.split(","));
        }

        List<String> typeList = null;
        if (type != null && !type.isEmpty()) {
            typeList = List.of(type.split(","));
        }

        List<MemoryListResponse.LowCacheHitItem> response = memoryService.getLowCacheHitList(instanceId, timeRange, statusList, typeList);

        return ResponseEntity.ok(response);
    }
}
