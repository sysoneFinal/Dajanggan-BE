package com.dajanggan.domain.vacuum.controller;

import com.dajanggan.domain.vacuum.dto.VacuumBloatDto;
import com.dajanggan.domain.vacuum.service.VacuumBloatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@Slf4j
@RestController
@RequestMapping("/api/vacuum/bloat")
@RequiredArgsConstructor
public class VacuumBloatController {

    private final VacuumBloatService vacuumBloatService;

    @GetMapping("/dashboard")
    public ResponseEntity<VacuumBloatDto.Response> getDashboard(
            @RequestParam(required = false) Long databaseId) {

        log.info("GET /api/vacuum/bloat/dashboard - databaseId: {}", databaseId);
        VacuumBloatDto.Response dashboard = vacuumBloatService.getDashboardData(databaseId);
        return ResponseEntity.ok(dashboard);
    }

    @GetMapping("/xmin-horizon")
    public ResponseEntity<VacuumBloatDto.XminHorizonMonitor> getXminHorizon(
            @RequestParam(required = false) Long databaseId,
            @RequestParam(required = false) OffsetDateTime startTime,
            @RequestParam(required = false) OffsetDateTime endTime) {

        // 기본값: 최근 7일
        if (startTime == null) {
            startTime = OffsetDateTime.now().minusDays(7);
        }
        if (endTime == null) {
            endTime = OffsetDateTime.now();
        }

        log.info("GET /api/vacuum/bloat/xmin-horizon - databaseId: {}, period: {} ~ {}",
                databaseId, startTime, endTime);

        VacuumBloatDto.XminHorizonMonitor data = vacuumBloatService.getXminHorizonData(
                databaseId, startTime, endTime);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/trend")
    public ResponseEntity<VacuumBloatDto.BloatTrend> getTrend(
            @RequestParam(required = false) Long databaseId,
            @RequestParam(defaultValue = "30") int days) {

        log.info("GET /api/vacuum/bloat/trend - databaseId: {}, days: {}", databaseId, days);
        VacuumBloatDto.BloatTrend data = vacuumBloatService.getBloatTrendData(databaseId, days);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/distribution")
    public ResponseEntity<VacuumBloatDto.BloatDistribution> getDistribution(
            @RequestParam(required = false) Long databaseId) {

        log.info("GET /api/vacuum/bloat/distribution - databaseId: {}", databaseId);
        VacuumBloatDto.BloatDistribution data = vacuumBloatService.getBloatDistributionData(databaseId);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/kpi")
    public ResponseEntity<VacuumBloatDto.Kpi> getKpi(
            @RequestParam(required = false) Long databaseId) {

        log.info("GET /api/vacuum/bloat/kpi - databaseId: {}", databaseId);
        VacuumBloatDto.Kpi data = vacuumBloatService.getKpiData(databaseId);
        return ResponseEntity.ok(data);
    }
}