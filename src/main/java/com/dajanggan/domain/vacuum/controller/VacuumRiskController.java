package com.dajanggan.domain.vacuum.controller;

import com.dajanggan.domain.vacuum.dto.VacuumRiskDto;
import com.dajanggan.domain.vacuum.service.VacuumRiskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * VacuumRisk 컨트롤러
 *
 * 주요 기능:
 * - Vacuum 위험도 대시보드 조회
 * - Blocker 분석
 * - Wraparound 위험도 조회
 * - Transaction 산포도 분석
 *
 *
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-10  김민서    1. 최초작성
 *
 */
@Slf4j
@Tag(name = "Vacuum Risk", description = "Vacuum 위험도 분석 페이지 API")
@RestController
@RequestMapping("/api/vacuum/risk")
@RequiredArgsConstructor
public class VacuumRiskController {

    private final VacuumRiskService vacuumRiskService;

    /**
     * Vacuum 위험도 대시보드 조회
     */
    @Operation(
            summary = "Vacuum 위험도 대시보드 조회",
            description = "Vacuum 위험도 관련 전체 대시보드 데이터를 조회합니다."
    )
    @GetMapping("/dashboard")
    public ResponseEntity<VacuumRiskDto.Response> getDashboard(
            @Parameter(description = "데이터베이스 ID")
            @RequestParam(required = false) Long databaseId,
            @Parameter(description = "조회 시간 (시간 단위)", example = "24")
            @RequestParam(defaultValue = "24") int hours
    ) {
        log.info("Vacuum 위험도 대시보드 조회: databaseId={}, hours={}", databaseId, hours);

        VacuumRiskDto.Response data = vacuumRiskService.getRiskData(databaseId, hours);

        return ResponseEntity.ok(data);
    }

    /**
     * 시간대별 Vacuum Blocker 수 조회
     */
    @Operation(
            summary = "시간대별 Blocker 수 조회",
            description = "시간대별로 발생한 Vacuum Blocker의 수를 조회합니다."
    )
    @GetMapping("/blockers-per-hour")
    public ResponseEntity<List<VacuumRiskDto.BlockersPerHourRaw>> getBlockersPerHour(
            @Parameter(description = "데이터베이스 ID")
            @RequestParam(required = false) Long databaseId,
            @Parameter(description = "시작 시간 (ISO 8601 형식)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @Parameter(description = "종료 시간 (ISO 8601 형식)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime
    ) {
        // 기본값 설정: 최근 24시간
        if (startTime == null || endTime == null) {
            endTime = (endTime == null) ? OffsetDateTime.now() : endTime;
            startTime = (startTime == null) ? endTime.minusHours(24) : startTime;
        }

        log.info("시간대별 Blocker 조회: databaseId={}, period: {} ~ {}",
                databaseId, startTime, endTime);

        List<VacuumRiskDto.BlockersPerHourRaw> result =
                vacuumRiskService.getBlockersPerHour(databaseId, startTime, endTime);

        return ResponseEntity.ok(result);
    }

    /**
     * 상위 Bloat 테이블 조회
     */
    @Operation(
            summary = "상위 Bloat 테이블 조회",
            description = "시간 구간 내에서 Bloat가 가장 높은 테이블들을 조회합니다."
    )
    @GetMapping("/top-bloat")
    public ResponseEntity<List<VacuumRiskDto.TopBloatRaw>> getTopBloat(
            @Parameter(description = "데이터베이스 ID")
            @RequestParam(required = false) Long databaseId,
            @Parameter(description = "조회 개수 (기본: 10)")
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "시작 시간 (ISO 8601 형식)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @Parameter(description = "종료 시간 (ISO 8601 형식)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime
    ) {
        // 기본값 설정
        if (endTime == null) endTime = OffsetDateTime.now();
        if (startTime == null) startTime = endTime.minusHours(24);

        log.info("상위 Bloat 테이블 조회: databaseId={}, limit={}, period: {} ~ {}",
                databaseId, limit, startTime, endTime);

        List<VacuumRiskDto.TopBloatRaw> result =
                vacuumRiskService.getTopBloatTables(databaseId, limit, startTime, endTime);

        return ResponseEntity.ok(result);
    }

    /**
     * Vacuum Blocker 상세 정보 조회
     */
    @Operation(
            summary = "Vacuum Blocker 상세 조회",
            description = "Vacuum을 차단하는 세션/트랜잭션의 상세 정보를 조회합니다."
    )
    @GetMapping("/blockers")
    public ResponseEntity<List<VacuumRiskDto.VacuumBlockerDetailRaw>> getBlockers(
            @Parameter(description = "데이터베이스 ID")
            @RequestParam(required = false) Long databaseId,
            @Parameter(description = "시작 시간 (ISO 8601 형식)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @Parameter(description = "종료 시간 (ISO 8601 형식)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime
    ) {
        // 기본값 설정
        if (endTime == null) endTime = OffsetDateTime.now();
        if (startTime == null) startTime = endTime.minusHours(24);

        log.info("Blocker 상세 조회: databaseId={}, period: {} ~ {}",
                databaseId, startTime, endTime);

        List<VacuumRiskDto.VacuumBlockerDetailRaw> result =
                vacuumRiskService.getVacuumBlockers(databaseId, startTime, endTime);

        return ResponseEntity.ok(result);
    }

    /**
     * Wraparound 위험도 조회
     */
    @Operation(
            summary = "Wraparound 위험도 조회",
            description = "Transaction ID Wraparound 위험도를 조회합니다."
    )
    @GetMapping("/wraparound")
    public ResponseEntity<List<VacuumRiskDto.WraparoundProgressRaw>> getWraparound(
            @Parameter(description = "데이터베이스 ID")
            @RequestParam(required = false) Long databaseId,
            @Parameter(description = "시작 시간 (ISO 8601 형식)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @Parameter(description = "종료 시간 (ISO 8601 형식)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime
    ) {
        // 기본값 설정
        if (endTime == null) endTime = OffsetDateTime.now();
        if (startTime == null) startTime = endTime.minusHours(24);

        log.info("Wraparound 위험도 조회: databaseId={}, period: {} ~ {}",
                databaseId, startTime, endTime);

        List<VacuumRiskDto.WraparoundProgressRaw> result =
                vacuumRiskService.getWraparoundProgress(databaseId, startTime, endTime);

        return ResponseEntity.ok(result);
    }

    /**
     * Transaction Age 산포도 조회
     */
    @Operation(
            summary = "Transaction Age 산포도 조회",
            description = "Transaction Age와 Block Duration의 상관관계를 산포도로 조회합니다."
    )
    @GetMapping("/tx-scatter")
    public ResponseEntity<VacuumRiskDto.ScatterDto> getTxScatter(
            @Parameter(description = "데이터베이스 ID")
            @RequestParam(required = false) Long databaseId,
            @Parameter(description = "시작 시간 (ISO 8601 형식)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @Parameter(description = "종료 시간 (ISO 8601 형식)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime
    ) {
        // 기본값 설정
        if (endTime == null) endTime = OffsetDateTime.now();
        if (startTime == null) startTime = endTime.minusHours(24);

        log.info("Transaction 산포도 조회: databaseId={}, period: {} ~ {}",
                databaseId, startTime, endTime);

        VacuumRiskDto.ScatterDto result =
                vacuumRiskService.getTransactionScatter(databaseId, startTime, endTime);

        return ResponseEntity.ok(result);
    }
}
