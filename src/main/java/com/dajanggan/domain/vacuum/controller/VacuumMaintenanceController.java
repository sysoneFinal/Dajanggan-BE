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

@Slf4j
@RestController
@RequestMapping("/api/vacuum")
@RequiredArgsConstructor
public class VacuumMaintenanceController {

    private final VacuumMaintenanceService vacuumMaintenanceService;

    @GetMapping("/dashboard")
    public ResponseEntity<VacuumMaintenanceDto.Response> getDashboard(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam Long databaseId,
            @RequestParam Long instanceId,
            @RequestParam(required = false) String tableName) {

        log.info("Dashboard 조회 요청 - databaseId: {}, instanceId: {}, hours: {}",
                databaseId, instanceId, hours);

        VacuumMaintenanceDto.Response dashboard =
                vacuumMaintenanceService.getDashboardData(hours, databaseId, instanceId, tableName);

        return ResponseEntity.ok(dashboard);
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<VacuumMaintenanceDto.Session>> getCurrentSessions(
            @RequestParam(required = false) Long databaseId, String tableName) {

        log.info("Sessions 조회 요청 - databaseId: {}",
                databaseId);


        return ResponseEntity.ok(
                vacuumMaintenanceService.getCurrentSessions(databaseId, tableName)
        );
    }
}