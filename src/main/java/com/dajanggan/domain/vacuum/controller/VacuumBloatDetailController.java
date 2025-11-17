package com.dajanggan.domain.vacuum.controller;

import com.dajanggan.domain.vacuum.dto.VacuumBloatDetailDto;
import com.dajanggan.domain.vacuum.service.VacuumBloatDetailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/vacuum/bloat/detail")
@RequiredArgsConstructor
public class VacuumBloatDetailController {

    private final VacuumBloatDetailService vacuumBloatDetailService;

    /**
     * 전체 대시보드 데이터 조회 (한번에 모두)
     */
    @GetMapping("/dashboard")
    public ResponseEntity<VacuumBloatDetailDto.Response> getDashboard(
            @RequestParam Long databaseId,
            @RequestParam String tableName
    ) {
        log.info("GET /api/vacuum/bloat/detail/dashboard - database: {}, table: {}",
                databaseId, tableName);

        VacuumBloatDetailDto.Response response = vacuumBloatDetailService.getBloatDetail(databaseId, tableName);
        return ResponseEntity.ok(response);
    }

    /**
     * KPI 데이터만 조회
     */
    @GetMapping("/kpi")
    public ResponseEntity<VacuumBloatDetailDto.Kpi> getKpi(
            @RequestParam Long databaseId,
            @RequestParam String tableName
    ) {
        log.info("GET /api/vacuum/bloat/detail/kpi - database: {}, table: {}",
                databaseId, tableName);

        VacuumBloatDetailDto.Kpi kpi = vacuumBloatDetailService.getKpi(databaseId, tableName);
        return ResponseEntity.ok(kpi);
    }

    /**
     * Bloat % Trend 데이터 조회
     */
    @GetMapping("/bloat-trend")
    public ResponseEntity<VacuumBloatDetailDto.BloatTrend> getBloatTrend(
            @RequestParam Long databaseId,
            @RequestParam String tableName,
            @RequestParam(defaultValue = "30") int days
    ) {
        log.info("GET /api/vacuum/bloat/detail/bloat-trend - database: {}, table: {}, days: {}",
                databaseId, tableName, days);

        VacuumBloatDetailDto.BloatTrend trend = vacuumBloatDetailService.getBloatTrend(databaseId, tableName, days);
        return ResponseEntity.ok(trend);
    }

    /**
     * Dead Tuples Trend 데이터 조회
     */
    @GetMapping("/dead-tuples-trend")
    public ResponseEntity<VacuumBloatDetailDto.DeadTuplesTrend> getDeadTuplesTrend(
            @RequestParam Long databaseId,
            @RequestParam String tableName,
            @RequestParam(defaultValue = "30") int days
    ) {
        log.info("GET /api/vacuum/bloat/detail/dead-tuples-trend - database: {}, table: {}, days: {}",
                databaseId, tableName, days);

        VacuumBloatDetailDto.DeadTuplesTrend trend = vacuumBloatDetailService.getDeadTuplesTrend(databaseId, tableName, days);
        return ResponseEntity.ok(trend);
    }

    /**
     * Index Bloat Trend 데이터 조회
     */
    @GetMapping("/index-bloat-trend")
    public ResponseEntity<VacuumBloatDetailDto.IndexBloatTrend> getIndexBloatTrend(
            @RequestParam Long databaseId,
            @RequestParam String tableName,
            @RequestParam(defaultValue = "30") int days
    ) {
        log.info("GET /api/vacuum/bloat/detail/index-bloat-trend - database: {}, table: {}, days: {}",
                databaseId, tableName, days);

        VacuumBloatDetailDto.IndexBloatTrend trend = vacuumBloatDetailService.getIndexBloatTrend(databaseId, tableName, days);
        return ResponseEntity.ok(trend);
    }

    /**
     * 데이터베이스 내 테이블 목록 조회
     */
    @GetMapping("/tables")
    public ResponseEntity<List<String>> getTableList(@RequestParam Long databaseId) {
        log.info("GET /api/vacuum/bloat/detail/tables - database: {}", databaseId);

        List<String> tables = vacuumBloatDetailService.getTableList(databaseId);
        return ResponseEntity.ok(tables);
    }
}