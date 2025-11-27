// 작성자 : 김동현
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
     */
    @GetMapping("/list")
    public ResponseEntity<DiskIoListResponse> getDiskIoList(
            @RequestParam(required = false) Long instanceId,
            @RequestParam(defaultValue = "24h") String timeRange,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") Integer page,
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
    @GetMapping("/list/low-cache-hit")
    public ResponseEntity<DiskIoListResponse> getLowCacheHitList(
            @RequestParam Long instanceId,
            @RequestParam(defaultValue = "7d") String timeRange,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") Integer page,
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