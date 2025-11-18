package com.dajanggan.domain.vacuum.controller;

import com.dajanggan.domain.vacuum.dto.VacuumBloatDto;
import com.dajanggan.domain.vacuum.service.VacuumBloatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/vacuum/bloat")
@RequiredArgsConstructor
public class VacuumBloatController {

    private final VacuumBloatService vacuumBloatService;

    @GetMapping("/dashboard")
    public ResponseEntity<VacuumBloatDto.Response> getDashboard(
            @RequestParam Long databaseId,
            @RequestParam Long instanceId) {

        log.info("GET /api/vacuum/bloat/dashboard - databaseId: {}, instanceId: {}",
                databaseId, instanceId);

        VacuumBloatDto.Response dashboard = vacuumBloatService.getDashboardData(
                databaseId, instanceId);

        return ResponseEntity.ok(dashboard);
    }

    @GetMapping("/kpi")
    public ResponseEntity<VacuumBloatDto.Kpi> getKpi(
            @RequestParam Long databaseId,
            @RequestParam Long instanceId) {

        log.info("GET /api/vacuum/bloat/kpi - databaseId: {}, instanceId: {}",
                databaseId, instanceId);

        VacuumBloatDto.Kpi data = vacuumBloatService.getKpiData(databaseId, instanceId);

        return ResponseEntity.ok(data);
    }
}