package com.dajanggan.domain.vacuum.controller;

import com.dajanggan.domain.vacuum.dto.VacuumRiskDto;
import com.dajanggan.domain.vacuum.service.VacuumRiskService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Tag(name = "Vacuum-Risk", description = "vacuum risk 페이지 관련 API")
@RestController
@RequestMapping("/api/vacuum/risk")
@RequiredArgsConstructor
public class VacuumRiskController {

    private final VacuumRiskService vacuumRiskService;

    /* ---------------------- Dashboard(집계) ---------------------- */
    @GetMapping("/dashboard")
    public ResponseEntity<VacuumRiskDto.Response> getDashboard(
            @RequestParam(required = false) Long databaseId,
            @RequestParam(defaultValue = "24") int hours) {

        log.info("GET /api/vacuum/risk/dashboard - databaseId: {}, hours: {}", databaseId, hours);
        VacuumRiskDto.Response data = vacuumRiskService.getRiskData(databaseId, hours);
        return ResponseEntity.ok(data);
    }

    /* ---------------------- 차트/표 개별 API ---------------------- */

    // Blockers per Hour (시간 구간)
    @GetMapping("/blockers-per-hour")
    public ResponseEntity<List<VacuumRiskDto.BlockersPerHourRaw>> getBlockersPerHour(
            @RequestParam(required = false) Long databaseId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {

        if (startTime == null || endTime == null) {
            endTime = (endTime == null) ? OffsetDateTime.now() : endTime;
            startTime = (startTime == null) ? endTime.minusHours(24) : startTime;
        }
        log.info("GET /api/vacuum/risk/blockers-per-hour - databaseId: {}, period: {} ~ {}",
                databaseId, startTime, endTime);

        return ResponseEntity.ok(
                vacuumRiskService.getBlockersPerHour(databaseId, startTime, endTime)
        );
    }

    // Top Bloat Tables (시간 구간 + limit)
    @GetMapping("/top-bloat")
    public ResponseEntity<List<VacuumRiskDto.TopBloatRaw>> getTopBloat(
            @RequestParam(required = false) Long databaseId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {

        if (endTime == null) endTime = OffsetDateTime.now();
        if (startTime == null) startTime = endTime.minusHours(24);
        log.info("GET /api/vacuum/risk/top-bloat - databaseId: {}, limit: {}, period: {} ~ {}",
                databaseId, limit, startTime, endTime);

        return ResponseEntity.ok(
                vacuumRiskService.getTopBloatTables(databaseId, limit, startTime, endTime)
        );
    }

    // Vacuum Blockers 상세 (시간 구간)
    @GetMapping("/blockers")
    public ResponseEntity<List<VacuumRiskDto.VacuumBlockerDetailRaw>> getBlockers(
            @RequestParam(required = false) Long databaseId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {

        if (endTime == null) endTime = OffsetDateTime.now();
        if (startTime == null) startTime = endTime.minusHours(24);
        log.info("GET /api/vacuum/risk/blockers - databaseId: {}, period: {} ~ {}",
                databaseId, startTime, endTime);

        return ResponseEntity.ok(
                vacuumRiskService.getVacuumBlockers(databaseId, startTime, endTime)
        );
    }

    // Wraparound Progress (시간 구간)
    @GetMapping("/wraparound")
    public ResponseEntity<List<VacuumRiskDto.WraparoundProgressRaw>> getWraparound(
            @RequestParam(required = false) Long databaseId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {

        if (endTime == null) endTime = OffsetDateTime.now();
        if (startTime == null) startTime = endTime.minusHours(24);
        log.info("GET /api/vacuum/risk/wraparound - databaseId: {}, period: {} ~ {}",
                databaseId, startTime, endTime);

        return ResponseEntity.ok(
                vacuumRiskService.getWraparoundProgress(databaseId, startTime, endTime)
        );
    }

    // Transaction Age vs Block Duration 산포도 (시간 구간)
    @GetMapping("/tx-scatter")
    public ResponseEntity<VacuumRiskDto.ScatterDto> getTxScatter(
            @RequestParam(required = false) Long databaseId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {

        if (endTime == null) endTime = OffsetDateTime.now();
        if (startTime == null) startTime = endTime.minusHours(24);
        log.info("GET /api/vacuum/risk/tx-scatter - databaseId: {}, period: {} ~ {}",
                databaseId, startTime, endTime);

        return ResponseEntity.ok(
                vacuumRiskService.getTransactionScatter(databaseId, startTime, endTime)
        );
    }
}