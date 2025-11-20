package com.dajanggan.domain.vacuum.controller;

import com.dajanggan.domain.vacuum.dto.VacuumBloatDetailDto;
import com.dajanggan.domain.vacuum.service.VacuumBloatDetailService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "Vacuum-Bloat-Detail", description = "vacuum bloat 상세 페이지 관련 API")
@RestController
@RequestMapping("/api/vacuum/bloat/detail")
@RequiredArgsConstructor
public class VacuumBloatDetailController {

    private final VacuumBloatDetailService vacuumBloatDetailService;

    /**
     * 전체 대시보드 데이터 조회
     */

    @Tag(name = "Vacuum-bloat-detail-dashboard", description = "Bloat 추이, Dead Tuples 추이, Index Bloat 추이를 조회합니다")
    @GetMapping("/dashboard")
    public ResponseEntity<VacuumBloatDetailDto.Response> getDashboard(
            @RequestParam Long databaseId,
            @RequestParam Long instanceId,
            @RequestParam String tableName) {

        log.info("GET /api/vacuum/bloat/detail/dashboard - db: {}, instance: {}, table: {}",
                databaseId, instanceId, tableName);

        VacuumBloatDetailDto.Response response = vacuumBloatDetailService.getBloatDetail(
                databaseId, instanceId, tableName);

        return ResponseEntity.ok(response);
    }

    /**
     * KPI 데이터만 조회
     */
    @Tag(name = "Vacuum-bloat-detail-kpi", description = "Bloat, 테이블 크기, 낭비된 공간을 조회합니다")
    @GetMapping("/kpi")
    public ResponseEntity<VacuumBloatDetailDto.Kpi> getKpi(
            @RequestParam Long databaseId,
            @RequestParam Long instanceId,
            @RequestParam String tableName) {

        log.info("GET /api/vacuum/bloat/detail/kpi - db: {}, instance: {}, table: {}",
                databaseId, instanceId, tableName);

        VacuumBloatDetailDto.Kpi kpi = vacuumBloatDetailService.getKpi(
                databaseId, instanceId, tableName);

        return ResponseEntity.ok(kpi);
    }

    /**
     * 테이블 목록 조회
     */
    @Tag(name = "Vacuum-bloat-detail-table", description = "인스턴스, 데이터베이스에 속한 테이블을 조회합니다")
    @GetMapping("/tables")
    public ResponseEntity<List<String>> getTableList(
            @RequestParam Long databaseId,
            @RequestParam Long instanceId) {

        log.info("GET /api/vacuum/bloat/detail/tables - db: {}, instance: {}",
                databaseId, instanceId);

        List<String> tables = vacuumBloatDetailService.getTableList(databaseId, instanceId);

        return ResponseEntity.ok(tables);
    }
}