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

    private final VacuumBloatDetailService bloatDetailService;

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

        VacuumBloatDetailDto.Response response = bloatDetailService.getBloatDetail(databaseId, tableName);
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

        VacuumBloatDetailDto.Kpi kpi = bloatDetailService.getKpi(databaseId, tableName);
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

        VacuumBloatDetailDto.BloatTrend trend = bloatDetailService.getBloatTrend(databaseId, tableName, days);
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

        VacuumBloatDetailDto.DeadTuplesTrend trend = bloatDetailService.getDeadTuplesTrend(databaseId, tableName, days);
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

        VacuumBloatDetailDto.IndexBloatTrend trend = bloatDetailService.getIndexBloatTrend(databaseId, tableName, days);
        return ResponseEntity.ok(trend);
    }

    /**
     * 데이터베이스 내 테이블 목록 조회
     */
    @GetMapping("/tables")
    public ResponseEntity<List<String>> getTableList(@RequestParam Long databaseId) {
        log.info("GET /api/vacuum/bloat/detail/tables - database: {}", databaseId);

        List<String> tables = bloatDetailService.getTableList(databaseId);
        return ResponseEntity.ok(tables);
    }
}