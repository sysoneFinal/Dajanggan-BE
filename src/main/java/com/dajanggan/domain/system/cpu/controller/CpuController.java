// 작성자 : 김동현
package com.dajanggan.domain.system.cpu.controller;

import com.dajanggan.domain.system.cpu.dto.*;
import com.dajanggan.domain.system.cpu.service.CpuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/system/cpu")
@RequiredArgsConstructor
public class CpuController {

    private final CpuService cpuService;

    /**
     * CPU 대시보드 데이터 조회
     *
     * @param instanceId PostgreSQL 인스턴스 ID (required)
     * @return CPU 대시보드 데이터
     */
    @GetMapping
    public ResponseEntity<CpuDto.DashboardResponse> getCpuDashboard(
            @RequestParam Long instanceId) {
        log.debug("CPU 대시보드 조회 요청 - instanceId: {}", instanceId);

        CpuDto.DashboardResponse response = cpuService.getCpuDashboard(instanceId);

        return ResponseEntity.ok(response);
    }

    /**
     * CPU 리스트 데이터 조회
     *
     * @param instanceId   PostgreSQL 인스턴스 ID (required)
     * @param timeRange    시간 범위 (1h, 6h, 24h, 7d) - 기본값: 24h
     * @param status       상태 필터 (정상,주의,위험 - 콤마 구분)
     * @param page         페이지 번호 (0부터 시작) - 기본값: 0
     * @param size         페이지당 항목 수 - 기본값: 20
     * @return CPU 리스트 데이터
     */
    @GetMapping("/list")
    public ResponseEntity<CpuDto.ListResponse> getCpuList(
            @RequestParam Long instanceId,
            @RequestParam(defaultValue = "24h") String timeRange,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        log.debug("CPU 리스트 조회 요청 - instanceId: {}, timeRange: {}, status: {}, page: {}, size: {}",
                instanceId, timeRange, status, page, size);

        CpuListRequest request = CpuListRequest.builder()
                .timeRange(timeRange)
                .status(status)
                .page(page)
                .size(size)
                .build();

        CpuDto.ListResponse response = cpuService.getCpuList(instanceId, request);

        return ResponseEntity.ok(response);
    }


}