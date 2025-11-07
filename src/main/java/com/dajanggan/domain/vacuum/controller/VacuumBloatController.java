package com.dajanggan.domain.vacuum.controller;

import com.dajanggan.domain.vacuum.dto.VacuumBloatDto;
import com.dajanggan.domain.vacuum.service.VacuumBloatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Vacuum Bloat Controller
 * - Bloat 페이지 데이터 제공
 */
@Slf4j
@RestController
@RequestMapping("/api/vacuum/bloat")
@RequiredArgsConstructor
public class VacuumBloatController {

    private final VacuumBloatService vacuumBloatService;

    /**
     * Vacuum Bloat 대시보드 데이터 조회
     * GET /api/vacuum/bloat/dashboard?databaseId=xxx
     *
     * @param databaseId 데이터베이스 ID
     * @return 대시보드 전체 데이터 (KPI, 차트 데이터 포함)
     */
    @GetMapping("/dashboard")
    public ResponseEntity<VacuumBloatDto.Response> getDashboard(
            @RequestParam(required = false) String databaseId) {

        log.info("GET /api/vacuum/bloat/dashboard - databaseId: {}", databaseId);
        VacuumBloatDto.Response dashboard = vacuumBloatService.getDashboardData(databaseId);
        return ResponseEntity.ok(dashboard);
    }

    /**
     * Xmin Horizon Monitor 데이터 조회 (7일간)
     * GET /api/vacuum/bloat/xmin-horizon?databaseId=xxx&startTime=xxx&endTime=xxx
     *
     * @param databaseId 데이터베이스 ID
     * @param startTime 시작 시간 (기본: 7일 전)
     * @param endTime 종료 시간 (기본: 현재)
     * @return Xmin Horizon Age 데이터
     */
    @GetMapping("/xmin-horizon")
    public ResponseEntity<VacuumBloatDto.XminHorizonMonitor> getXminHorizon(
            @RequestParam(required = false) String databaseId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {

        // 기본값: 최근 7일
        if (startTime == null) {
            startTime = LocalDateTime.now().minusDays(7);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }

        log.info("GET /api/vacuum/bloat/xmin-horizon - databaseId: {}, period: {} ~ {}",
                databaseId, startTime, endTime);

        VacuumBloatDto.XminHorizonMonitor data = vacuumBloatService.getXminHorizonData(
                databaseId, startTime, endTime);
        return ResponseEntity.ok(data);
    }

    /**
     * Bloat Trend 데이터 조회 (최근 30일)
     * GET /api/vacuum/bloat/trend?databaseId=xxx
     *
     * @param databaseId 데이터베이스 ID
     * @return 30일간 Bloat 증가 추세
     */
    @GetMapping("/trend")
    public ResponseEntity<VacuumBloatDto.BloatTrend> getTrend(
            @RequestParam(required = false) String databaseId) {

        log.info("GET /api/vacuum/bloat/trend - databaseId: {}", databaseId);
        VacuumBloatDto.BloatTrend data = vacuumBloatService.getBloatTrendData(databaseId, 30);
        return ResponseEntity.ok(data);
    }

    /**
     * Bloat Distribution 데이터 조회 (24시간)
     * GET /api/vacuum/bloat/distribution?databaseId=xxx
     *
     * @param databaseId 데이터베이스 ID
     * @return Bloat 비율별 테이블 분포
     */
    @GetMapping("/distribution")
    public ResponseEntity<VacuumBloatDto.BloatDistribution> getDistribution(
            @RequestParam(required = false) String databaseId) {

        log.info("GET /api/vacuum/bloat/distribution - databaseId: {}", databaseId);
        VacuumBloatDto.BloatDistribution data = vacuumBloatService.getBloatDistributionData(databaseId);
        return ResponseEntity.ok(data);
    }

    /**
     * KPI 지표 조회
     * GET /api/vacuum/bloat/kpi?databaseId=xxx
     *
     * @param databaseId 데이터베이스 ID
     * @return 주요 KPI (총 Bloat, Critical 테이블 수, Bloat 증가량)
     */
    @GetMapping("/kpi")
    public ResponseEntity<VacuumBloatDto.Kpi> getKpi(
            @RequestParam(required = false) String databaseId) {

        log.info("GET /api/vacuum/bloat/kpi - databaseId: {}", databaseId);
        VacuumBloatDto.Kpi data = vacuumBloatService.getKpiData(databaseId);
        return ResponseEntity.ok(data);
    }
}