package com.dajanggan.domain.vacuum.controller;

import com.dajanggan.domain.vacuum.dto.VacuumDetailDto;
import com.dajanggan.domain.vacuum.service.VacuumDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * VacuumDetail 컨트롤러
 *
 * 주요 기능:
 * - Vacuum 세션 상세 정보 조회
 *
 *
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-11  김민서    1. 최초작성
 *
 */
@Slf4j
@Tag(name = "Vacuum Detail", description = "Vacuum 세션 상세 페이지 API")
@RestController
@RequestMapping("/api/vacuum/detail")
@RequiredArgsConstructor
public class VacuumDetailController {

    private final VacuumDetailService vacuumDetailService;

    /**
     * Vacuum 세션 상세 정보 조회
     */
    @Operation(
            summary = "Vacuum 세션 상세 조회",
            description = "특정 테이블의 Vacuum 세션 상세 정보를 조회합니다. 실행 시간 기준으로 필터링 가능합니다."
    )
    @GetMapping
    public ResponseEntity<VacuumDetailDto.Response> getVacuumDetail(
            @Parameter(description = "데이터베이스 ID", required = true)
            @RequestParam Long databaseId,
            @Parameter(description = "테이블명", required = true)
            @RequestParam String tableName,
            @Parameter(description = "실행 시간 (ISO 8601 형식)")
            @RequestParam(required = false) String executedAt
    ) {
        log.info("Vacuum 세션 상세 조회: databaseId={}, table={}, executedAt={}",
                databaseId, tableName, executedAt);

        VacuumDetailDto.Response detail = vacuumDetailService.getVacuumDetail(
                databaseId, tableName, executedAt);

        return ResponseEntity.ok(detail);
    }
}
