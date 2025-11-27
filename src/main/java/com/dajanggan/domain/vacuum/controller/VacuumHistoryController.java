package com.dajanggan.domain.vacuum.controller;

import com.dajanggan.domain.vacuum.dto.VacuumHistoryDto;
import com.dajanggan.domain.vacuum.service.VacuumHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * VacuumHistory 컨트롤러
 *
 * 주요 기능:
 * - Vacuum 히스토리 조회
 * - 시간/상태 필터링
 *
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-10  김민서    1. 최초작성
 */
@Slf4j
@Tag(name = "Vacuum History", description = "Vacuum 히스토리 페이지 API")
@RestController
@RequestMapping("/api/vacuum")
@RequiredArgsConstructor
public class VacuumHistoryController {

    private final VacuumHistoryService vacuumHistoryService;

    /**
     * Vacuum 히스토리 조회
     */
    @Operation(
            summary = "Vacuum 히스토리 조회",
            description = "Vacuum 실행 히스토리를 조회합니다. 데이터베이스, 시간 범위, 상태로 필터링 가능합니다."
    )
    @GetMapping("/history")
    public ResponseEntity<List<VacuumHistoryDto.Response>> getHistory(
            @Parameter(description = "데이터베이스 ID")
            @RequestParam(required = false) Long databaseId,
            @Parameter(description = "조회 시간 (시간 단위)")
            @RequestParam(required = false) Integer hours,
            @Parameter(description = "조회 시간 (일 단위, hours보다 우선)")
            @RequestParam(required = false) Integer days,
            @Parameter(description = "상태 (success, running, failed)")
            @RequestParam(required = false) String status
    ) {
        // days가 제공되면 hours로 변환 (days가 우선)
        Integer finalHours = (days != null) ? days * 24 : hours;

        log.info("Vacuum 히스토리 조회: databaseId={}, hours={}, days={}, finalHours={}, status={}",
                databaseId, hours, days, finalHours, status);

        VacuumHistoryDto.Request request = new VacuumHistoryDto.Request(databaseId, finalHours, status);
        List<VacuumHistoryDto.Response> history = vacuumHistoryService.getVacuumHistory(request);

        log.info("히스토리 조회 결과: {}건", history.size());
        if (!history.isEmpty()) {
            log.debug("첫 번째 항목: {}", history.get(0));
        }

        return ResponseEntity.ok(history);
    }
}
