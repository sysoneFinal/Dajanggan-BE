package com.dajanggan.domain.system.cpu.controller;

import com.dajanggan.domain.system.cpu.dto.CpuDto;
import com.dajanggan.domain.system.cpu.service.CpuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/system/cpu")
@RequiredArgsConstructor
public class CpuController {

    private final CpuService cpuService;

    /**
     * CPU 대시보드 데이터 조회
     * @param instanceId PostgreSQL 인스턴스 ID (optional, 기본값은 설정된 기본 인스턴스)
     * @return CPU 대시보드 데이터
     */
    @GetMapping
    public ResponseEntity<CpuDto.DashboardResponse> getCpuDashboard(
            @RequestParam(required = false) Long instanceId) {
        log.debug("CPU 대시보드 조회 요청 - instanceId: {}", instanceId);

        CpuDto.DashboardResponse response = cpuService.getCpuDashboard(instanceId);

        return ResponseEntity.ok(response);
    }

    /**
     * CPU 리스트 데이터 조회
     * @param instanceId PostgreSQL 인스턴스 ID (optional)
     * @param timeRange 시간 범위 (1h, 6h, 24h, 7d)
     * @param status 상태 필터 (정상, 주의, 위험) - 콤마로 구분
     * @return CPU 리스트 데이터
     */
    @GetMapping("/list")
    public ResponseEntity<CpuDto.ListResponse> getCpuList(
            @RequestParam(required = false) Long instanceId,
            @RequestParam(defaultValue = "1h") String timeRange,
            @RequestParam(required = false) String status) {
        log.debug("CPU 리스트 조회 요청 - instanceId: {}, timeRange: {}, status: {}",
                instanceId, timeRange, status);

        // status 파라미터를 List로 변환
        List<String> statusList = null;
        if (status != null && !status.isEmpty()) {
            statusList = List.of(status.split(","));
        }

        CpuDto.ListResponse response = cpuService.getCpuList(instanceId, timeRange, statusList);

        return ResponseEntity.ok(response);
    }

    /**
     * 간단한 CPU 사용률 트렌드 조회 (os_metric_agg 사용)
     * @param instanceId PostgreSQL 인스턴스 ID
     * @param hours 조회 시간 (기본 1시간)
     * @return CPU 사용률 트렌드
     */
    @GetMapping("/trend")
    public ResponseEntity<CpuDto.CpuUsageTrend> getSimpleCpuTrend(
            @RequestParam Long instanceId,
            @RequestParam(defaultValue = "1") int hours) {
        log.debug("간단한 CPU 트렌드 조회 요청 - instanceId: {}, hours: {}", instanceId, hours);

        CpuDto.CpuUsageTrend response = cpuService.getSimpleCpuTrend(instanceId, hours);

        return ResponseEntity.ok(response);
    }

    /**
     * 현재 CPU 사용률 조회 (os_metric_agg 사용)
     * @param instanceId PostgreSQL 인스턴스 ID
     * @return 현재 CPU 사용률
     */
    @GetMapping("/current")
    public ResponseEntity<CpuDto.CpuUsage> getSimpleCpuUsage(
            @RequestParam Long instanceId) {
        log.debug("현재 CPU 사용률 조회 요청 - instanceId: {}", instanceId);

        CpuDto.CpuUsage response = cpuService.getSimpleCpuUsage(instanceId);

        return ResponseEntity.ok(response);
    }
}