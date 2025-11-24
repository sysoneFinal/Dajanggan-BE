package com.dajanggan.domain.system.memory.controller;

import com.dajanggan.domain.system.memory.dto.*;
import com.dajanggan.domain.system.memory.service.MemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

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
     * @param timeRange 시간 범위 (1h, 6h, 24h, 7d) - 기본값: 24h
     * @param status 상태 필터 (정상, 주의, 위험) - 콤마로 구분
     * @param page 페이지 번호 (0부터 시작) - 기본값: 0
     * @param size 페이지당 항목 수 - 기본값: 20
     * @return Memory 리스트 데이터
     */
    @GetMapping("/list")
    public ResponseEntity<MemoryListResponse> getMemoryList(
            @RequestParam Long instanceId,
            @RequestParam(defaultValue = "24h") String timeRange,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        log.info("Memory 리스트 조회 API 호출 - instanceId: {}, timeRange: {}, status: {}, page: {}, size: {}",
                instanceId, timeRange, status, page, size);

        // status 파라미터를 List로 변환
        List<String> statusList = null;
        if (status != null && !status.isEmpty()) {
            statusList = List.of(status.split(","));
        }

        MemoryListResponse response = memoryService.getMemoryList(instanceId, timeRange, statusList, page, size);

        return ResponseEntity.ok(response);
    }

    /**
     * 낮은 캐시 히트율 리스트 조회
     * @param instanceId PostgreSQL 인스턴스 ID
     * @param timeRange 시간 범위 (1h, 6h, 24h, 7d) - 기본값: 24h
     * @param status 상태 필터 (정상, 주의, 위험) - 콤마로 구분
     * @param type 타입 필터 (table, index) - 콤마로 구분
     * @return 낮은 캐시 히트율 리스트
     */
    @GetMapping("/list/low-cache-hit")
    public ResponseEntity<List<MemoryListResponse.LowCacheHitItem>> getLowCacheHitList(
            @RequestParam Long instanceId,
            @RequestParam(defaultValue = "24h") String timeRange,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        log.info("낮은 캐시 히트율 리스트 조회 API 호출 - instanceId: {}, timeRange: {}, status: {}, type: {}",
                instanceId, timeRange, status, type);

        // status 파라미터를 List로 변환
        List<String> statusList = null;
        if (status != null && !status.isEmpty()) {
            statusList = List.of(status.split(","));
        }

        // type 파라미터를 List로 변환 (table -> r, index -> i)
        List<String> typeList = null;
        if (type != null && !type.isEmpty()) {
            typeList = List.of(type.split(",")).stream()
                    .map(t -> {
                        if ("table".equalsIgnoreCase(t.trim())) return "r";
                        if ("index".equalsIgnoreCase(t.trim())) return "i";
                        return t.trim();
                    })
                    .collect(Collectors.toList());
        }

        List<MemoryListResponse.LowCacheHitItem> response = 
                memoryService.getLowCacheHitListOnly(instanceId, timeRange, statusList, typeList);

        return ResponseEntity.ok(response);
    }
}