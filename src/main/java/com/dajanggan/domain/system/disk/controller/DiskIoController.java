// 작성자 : 김동현
package com.dajanggan.domain.system.disk.controller;

import com.dajanggan.domain.system.disk.dto.*;
import com.dajanggan.domain.system.disk.service.DiskIoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "Disk I/O", description = "Disk I/O 메트릭 모니터링 API")
@RestController
@RequestMapping("/api/system/diskio")
@RequiredArgsConstructor
public class DiskIoController {

    private final DiskIoService diskIoService;

    /**
     * Disk I/O 대시보드 데이터 조회
     * @param instanceId PostgreSQL 인스턴스 ID (optional, 기본값은 설정된 기본 인스턴스)
     * @return Disk I/O 대시보드 데이터
     */
    @Operation(summary = "Disk I/O 대시보드 조회", description = "실시간 Disk I/O 사용률, Cache Hit Rate 등 대시보드 정보 조회")
    @GetMapping
    public ResponseEntity<DiskIoDashboardResponse> getDiskIoDashboard(
            @Parameter(description = "PostgreSQL 인스턴스 ID", example = "1")
            @RequestParam(required = false) Long instanceId) {
        log.debug("Disk I/O 대시보드 조회 요청 - instanceId: {}", instanceId);

        DiskIoDashboardResponse response = diskIoService.getDiskIoDashboard(instanceId);

        return ResponseEntity.ok(response);
    }

    /**
     * Disk I/O 리스트 데이터 조회
     */
    @Operation(summary = "Disk I/O 리스트 조회", description = "시간 범위 및 상태별로 필터링된 Disk I/O 메트릭 리스트 조회 (페이징)")
    @GetMapping("/list")
    public ResponseEntity<DiskIoListResponse> getDiskIoList(
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
        log.debug("Disk I/O 리스트 조회 요청 - instanceId: {}, timeRange: {}, status: {}, page: {}, size: {}",
                instanceId, timeRange, status, page, size);

        // status 파라미터를 List로 변환
        List<String> statusList = null;
        if (status != null && !status.isEmpty()) {
            statusList = List.of(status.split(","));
        }

        DiskIoListResponse response = diskIoService.getDiskIoList(instanceId, timeRange, statusList, page, size);

        return ResponseEntity.ok(response);
    }

    /**
     * 낮은 Cache Hit 리스트만 조회 (페이징 포함)
     */
    @Operation(summary = "낮은 Cache Hit 리스트 조회", description = "Cache Hit Rate가 낮은 항목만 필터링하여 조회 (페이징)")
    @GetMapping("/list/low-cache-hit")
    public ResponseEntity<DiskIoListResponse> getLowCacheHitList(
            @Parameter(description = "PostgreSQL 인스턴스 ID", required = true, example = "1")
            @RequestParam Long instanceId,
            @Parameter(description = "시간 범위 (1h, 6h, 24h, 7d)", example = "7d")
            @RequestParam(defaultValue = "7d") String timeRange,
            @Parameter(description = "상태 필터 (정상,주의,위험 - 콤마 구분)", example = "위험")
            @RequestParam(required = false) String status,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "페이지당 항목 수", example = "10")
            @RequestParam(defaultValue = "10") Integer size) {
        log.info("낮은 Cache Hit 리스트 조회 API 호출 - instanceId: {}, timeRange: {}, status: {}, page: {}, size: {}",
                instanceId, timeRange, status, page, size);

        // status 파라미터를 List로 변환
        List<String> statusList = null;
        if (status != null && !status.isEmpty()) {
            statusList = List.of(status.split(","));
        }

        DiskIoListResponse response = 
                diskIoService.getLowCacheHitListWithPaging(instanceId, timeRange, statusList, page, size);

        return ResponseEntity.ok(response);
    }
}