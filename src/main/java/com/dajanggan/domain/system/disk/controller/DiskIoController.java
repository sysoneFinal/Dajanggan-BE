package com.dajanggan.domain.system.disk.controller;



import com.dajanggan.domain.system.disk.dto.*;
import com.dajanggan.domain.system.disk.service.DiskIoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
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
    @GetMapping
    public ResponseEntity<DiskIoDashboardResponse> getDiskIoDashboard(
            @RequestParam(required = false) Long instanceId) {
        log.debug("Disk I/O 대시보드 조회 요청 - instanceId: {}", instanceId);

        DiskIoDashboardResponse response = diskIoService.getDiskIoDashboard(instanceId);

        return ResponseEntity.ok(response);
    }

    /**
     * Disk I/O 리스트 데이터 조회
     * @param instanceId PostgreSQL 인스턴스 ID (optional)
     * @param timeRange 시간 범위 (1h, 6h, 24h, 7d)
     * @param status 상태 필터 (정상, 주의, 위험) - 콤마로 구분
     * @return Disk I/O 리스트 데이터
     */
    @GetMapping("/list")
    public ResponseEntity<DiskIoListResponse> getDiskIoList(
            @RequestParam(required = false) Long instanceId,
            @RequestParam(defaultValue = "7d") String timeRange,
            @RequestParam(required = false) String status) {
        log.debug("Disk I/O 리스트 조회 요청 - instanceId: {}, timeRange: {}, status: {}",
                instanceId, timeRange, status);

        // status 파라미터를 List로 변환
        List<String> statusList = null;
        if (status != null && !status.isEmpty()) {
            statusList = List.of(status.split(","));
        }

        DiskIoListResponse response = diskIoService.getDiskIoList(instanceId, timeRange, statusList);

        return ResponseEntity.ok(response);
    }

    /**
     * 낮은 Cache Hit Ratio 시간대 리스트 조회
     * @param instanceId PostgreSQL 인스턴스 ID
     * @param timeRange 시간 범위 (1h, 6h, 24h, 7d)
     * @param status 상태 필터 (정상, 주의, 위험) - 콤마로 구분
     * @return 낮은 Cache Hit Ratio 시간대 리스트
     */
    @GetMapping("/list/low-cache-hit")
    public ResponseEntity<List<DiskIoListResponse.LowCacheHitItem>> getLowCacheHitList(
            @RequestParam Long instanceId,
            @RequestParam(defaultValue = "7d") String timeRange,
            @RequestParam(required = false) String status) {
        log.debug("낮은 Cache Hit 리스트 조회 요청 - instanceId: {}, timeRange: {}, status: {}",
                instanceId, timeRange, status);

        List<String> statusList = null;
        if (status != null && !status.isEmpty()) {
            statusList = List.of(status.split(","));
        }

        List<DiskIoListResponse.LowCacheHitItem> response = diskIoService.getLowCacheHitList(instanceId, timeRange, statusList);

        return ResponseEntity.ok(response);
    }
}