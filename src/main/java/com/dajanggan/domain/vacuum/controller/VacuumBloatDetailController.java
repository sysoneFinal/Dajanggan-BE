package com.dajanggan.domain.vacuum.controller;

import com.dajanggan.domain.vacuum.dto.VacuumBloatDetailDto;
import com.dajanggan.domain.vacuum.service.VacuumBloatDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * VacuumBloatDetail 컨트롤러
 *
 * 주요 기능:
 * - Bloat 상세 대시보드 조회
 * - KPI 데이터 조회
 * - 테이블 목록 조회
 *
 *
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-10  김민서    1. 최초작성
 *
 */
@Slf4j
@Tag(name = "Vacuum Bloat Detail", description = "Vacuum Bloat 상세 페이지 API")
@RestController
@RequestMapping("/api/vacuum/bloat/detail")
@RequiredArgsConstructor
public class VacuumBloatDetailController {

    private final VacuumBloatDetailService vacuumBloatDetailService;

    /**
     * 전체 대시보드 데이터 조회
     */
    @Operation(
            summary = "Bloat 상세 대시보드 조회",
            description = "Bloat 추이, Dead Tuples 추이, Index Bloat 추이를 포함한 대시보드 데이터를 조회합니다."
    )
    @GetMapping("/dashboard")
    public ResponseEntity<VacuumBloatDetailDto.Response> getDashboard(
            @Parameter(description = "데이터베이스 ID", required = true)
            @RequestParam Long databaseId,
            @Parameter(description = "인스턴스 ID", required = true)
            @RequestParam Long instanceId,
            @Parameter(description = "테이블명", required = true)
            @RequestParam String tableName
    ) {
        log.info("Bloat 대시보드 조회: databaseId={}, instanceId={}, table={}",
                databaseId, instanceId, tableName);

        VacuumBloatDetailDto.Response response = vacuumBloatDetailService.getBloatDetail(
                databaseId, instanceId, tableName);

        return ResponseEntity.ok(response);
    }

    /**
     * KPI 데이터만 조회
     */
    @Operation(
            summary = "Bloat KPI 조회",
            description = "Bloat 비율, 테이블 크기, 낭비된 공간 정보를 조회합니다."
    )
    @GetMapping("/kpi")
    public ResponseEntity<VacuumBloatDetailDto.Kpi> getKpi(
            @Parameter(description = "데이터베이스 ID", required = true)
            @RequestParam Long databaseId,
            @Parameter(description = "인스턴스 ID", required = true)
            @RequestParam Long instanceId,
            @Parameter(description = "테이블명", required = true)
            @RequestParam String tableName
    ) {
        log.debug("Bloat KPI 조회: databaseId={}, instanceId={}, table={}",
                databaseId, instanceId, tableName);

        VacuumBloatDetailDto.Kpi kpi = vacuumBloatDetailService.getKpi(
                databaseId, instanceId, tableName);

        return ResponseEntity.ok(kpi);
    }

    /**
     * 테이블 목록 조회
     */
    @Operation(
            summary = "테이블 목록 조회",
            description = "특정 인스턴스와 데이터베이스에 속한 모든 테이블 목록을 조회합니다."
    )
    @GetMapping("/tables")
    public ResponseEntity<List<String>> getTableList(
            @Parameter(description = "데이터베이스 ID", required = true)
            @RequestParam Long databaseId,
            @Parameter(description = "인스턴스 ID", required = true)
            @RequestParam Long instanceId
    ) {
        log.debug("테이블 목록 조회: databaseId={}, instanceId={}", databaseId, instanceId);

        List<String> tables = vacuumBloatDetailService.getTableList(databaseId, instanceId);

        return ResponseEntity.ok(tables);
    }
}
