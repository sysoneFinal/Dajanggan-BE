// 작성자 : 김동현
package com.dajanggan.domain.system.memory.controller;

import com.dajanggan.domain.system.memory.dto.*;
import com.dajanggan.domain.system.memory.service.MemoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Tag(name = "Memory", description = "Memory 메트릭 모니터링 API")
@RestController
@RequestMapping("/api/system/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;

    /**
     * Memory 대시보드 데이터 조회
     */
    @Operation(summary = "Memory 대시보드 조회", description = "실시간 Memory 사용률, Cache Hit Rate 등 대시보드 정보 조회")
    @GetMapping
    public ResponseEntity<MemoryDashboardResponse> getMemoryDashboard(
            @Parameter(description = "PostgreSQL 인스턴스 ID", required = true, example = "1")
            @RequestParam Long instanceId) {
        log.info("========== Memory 대시보드 조회 API 호출: instanceId={} ==========", instanceId);

        MemoryDashboardResponse response = memoryService.getMemoryDashboard(instanceId);

        return ResponseEntity.ok(response);
    }

    /**
     * Memory 리스트 데이터 조회
     */
    @Operation(summary = "Memory 리스트 조회", description = "시간 범위 및 상태별로 필터링된 Memory 메트릭 리스트 조회 (페이징)")
    @GetMapping("/list")
    public ResponseEntity<MemoryListResponse> getMemoryList(
            @Parameter(description = "PostgreSQL 인스턴스 ID", required = true, example = "1")
            @RequestParam Long instanceId,
            @Parameter(description = "시간 범위 (1h, 6h, 24h, 7d)", example = "24h")
            @RequestParam(defaultValue = "24h") String timeRange,
            @Parameter(description = "상태 필터 (정상,주의,위험 - 콤마 구분)", example = "주의,위험")
            @RequestParam(required = false) String status,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "페이지당 항목 수", example = "20")
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
     */
    @Operation(summary = "낮은 캐시 히트율 리스트 조회", description = "Cache Hit Rate가 낮은 테이블/인덱스 리스트 조회")
    @GetMapping("/list/low-cache-hit")
    public ResponseEntity<List<MemoryListResponse.LowCacheHitItem>> getLowCacheHitList(
            @Parameter(description = "PostgreSQL 인스턴스 ID", required = true, example = "1")
            @RequestParam Long instanceId,
            @Parameter(description = "시간 범위 (1h, 6h, 24h, 7d)", example = "24h")
            @RequestParam(defaultValue = "24h") String timeRange,
            @Parameter(description = "상태 필터 (정상,주의,위험 - 콤마 구분)", example = "위험")
            @RequestParam(required = false) String status,
            @Parameter(description = "타입 필터 (table, index - 콤마 구분)", example = "table,index")
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