// 작성자 : 김동현
package com.dajanggan.domain.system.cpu.controller;

import com.dajanggan.domain.system.cpu.dto.*;
import com.dajanggan.domain.system.cpu.service.CpuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "CPU", description = "CPU 메트릭 모니터링 API")
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
    @Operation(summary = "CPU 대시보드 조회", description = "실시간 CPU 사용률, 평균 값, 상태별 건수 등 대시보드 정보 조회")
    @GetMapping
    public ResponseEntity<CpuDto.DashboardResponse> getCpuDashboard(
            @Parameter(description = "PostgreSQL 인스턴스 ID", required = true, example = "1")
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
    @Operation(summary = "CPU 리스트 조회", description = "시간 범위 및 상태별로 필터링된 CPU 메트릭 리스트 조회 (페이징)")
    @GetMapping("/list")
    public ResponseEntity<CpuDto.ListResponse> getCpuList(
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