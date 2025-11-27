package com.dajanggan.domain.vacuum.controller;

import com.dajanggan.domain.vacuum.dto.VacuumMaintenanceDto;
import com.dajanggan.domain.vacuum.service.VacuumMaintenanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * VacuumMaintenance 컨트롤러
 *
 * 주요 기능:
 * - Vacuum 대시보드 데이터 조회
 * - 현재 Vacuum 세션 조회
 *
 *
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-04  김민서    1. 최초작성
 *
 */
@Slf4j
@Tag(name = "Vacuum Maintenance", description = "Vacuum 유지보수 페이지 API")
@RestController
@RequestMapping("/api/vacuum")
@RequiredArgsConstructor
public class VacuumMaintenanceController {

    private final VacuumMaintenanceService vacuumMaintenanceService;

    /**
     * Vacuum 대시보드 조회
     */
    @Operation(
            summary = "Vacuum 대시보드 조회",
            description = "Dead Tuple, Autovacuum, 지연 시간 추이를 포함한 대시보드 데이터를 조회합니다."
    )
    @GetMapping("/dashboard")
    public ResponseEntity<VacuumMaintenanceDto.Response> getDashboard(
            @Parameter(description = "조회 시간 (시간 단위)", example = "24")
            @RequestParam(defaultValue = "24") int hours,
            @Parameter(description = "데이터베이스 ID", required = true)
            @RequestParam Long databaseId,
            @Parameter(description = "인스턴스 ID", required = true)
            @RequestParam Long instanceId,
            @Parameter(description = "테이블명")
            @RequestParam(required = false) String tableName
    ) {
        log.info("Vacuum 대시보드 조회: databaseId={}, instanceId={}, hours={}, table={}",
                databaseId, instanceId, hours, tableName);

        VacuumMaintenanceDto.Response dashboard =
                vacuumMaintenanceService.getDashboardData(hours, databaseId, instanceId, tableName);

        return ResponseEntity.ok(dashboard);
    }

    /**
     * 현재 Vacuum 세션 조회
     */
    @Operation(
            summary = "현재 Vacuum 세션 조회",
            description = "실행 중인 Vacuum 세션 목록을 조회합니다."
    )
    @GetMapping("/sessions")
    public ResponseEntity<List<VacuumMaintenanceDto.Session>> getCurrentSessions(
            @Parameter(description = "데이터베이스 ID")
            @RequestParam(required = false) Long databaseId,
            @Parameter(description = "테이블명")
            @RequestParam(required = false) String tableName
    ) {
        log.info("Vacuum 세션 조회: databaseId={}, table={}", databaseId, tableName);

        List<VacuumMaintenanceDto.Session> sessions =
                vacuumMaintenanceService.getCurrentSessions(databaseId, tableName);

        return ResponseEntity.ok(sessions);
    }
}
