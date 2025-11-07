package com.dajanggan.domain.vacuum.controller;

import com.dajanggan.domain.vacuum.dto.VacuumMaintenanceDto;
import com.dajanggan.domain.vacuum.service.VacuumMaintenanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Vacuum Maintenance Controller
 * - VacuumPage.tsx 대시보드 데이터 제공
 */
@Slf4j
@RestController
@RequestMapping("/api/vacuum")
@RequiredArgsConstructor
public class VacuumMaintenanceController {

    private final VacuumMaintenanceService vacuumMaintenanceService;

    /**
     * 대시보드 전체 데이터 조회
     * GET /api/vacuum/dashboard?hours=24
     */
    @GetMapping("/dashboard")
    public ResponseEntity<VacuumMaintenanceDto.Response> getDashboard(
            @RequestParam(defaultValue = "24") int hours) {

        VacuumMaintenanceDto.Response dashboard = vacuumMaintenanceService.getDashboardData(hours);
        return ResponseEntity.ok(dashboard);
    }

    /**
     * 실시간 세션 목록만 조회
     * GET /api/vacuum/sessions
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<VacuumMaintenanceDto.Session>> getCurrentSessions() {
        return ResponseEntity.ok(vacuumMaintenanceService.getCurrentSessions());
    }
}