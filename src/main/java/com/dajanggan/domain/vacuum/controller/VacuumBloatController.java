package com.dajanggan.domain.vacuum.controller;

import com.dajanggan.domain.vacuum.dto.VacuumBloatDto;
import com.dajanggan.domain.vacuum.service.VacuumBloatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/vacuum/bloat")
@RequiredArgsConstructor
public class VacuumBloatController {

    private final VacuumBloatService vacuumBloatService;

    /**
     * Vacuum Bloat 대시보드 데이터 조회
     * @return 대시보드 전체 데이터 (KPI, 차트 데이터 포함)
     */
    @GetMapping("/dashboard")
    public ResponseEntity<VacuumBloatDto.DashboardResponse> getDashboardData(
            @RequestParam(required = false) String databaseId
    ) {
        VacuumBloatDto.DashboardResponse dashboard = vacuumBloatService.getDashboardData(databaseId);
        return ResponseEntity.ok(dashboard);
    }

    /**
     * Xmin Horizon Monitor 데이터 조회 (7일간)
     * @param databaseId 데이터베이스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return Xmin Horizon Age 데이터
     */
    @GetMapping("/xmin-horizon")
    public ResponseEntity<?> getXminHorizonData(
            @RequestParam(required = false) String databaseId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime
    ) {
        // 기본값: 최근 7일
        if (startTime == null) {
            startTime = LocalDateTime.now().minusDays(7);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }

        return ResponseEntity.ok(vacuumBloatService.getXminHorizonData(databaseId, startTime, endTime));
    }

    /**
     * Bloat Trend 데이터 조회 (최근 30일)
     * @param databaseId 데이터베이스 ID
     * @return 30일간 Bloat 증가 추세
     */
    @GetMapping("/bloat-trend")
    public ResponseEntity<?> getBloatTrendData(
            @RequestParam(required = false) String databaseId
    ) {
        return ResponseEntity.ok(vacuumBloatService.getBloatTrendData(databaseId, 30));
    }

    /**
     * Bloat Distribution 데이터 조회 (24시간)
     * @param databaseId 데이터베이스 ID
     * @return Bloat 비율별 테이블 분포
     */
    @GetMapping("/bloat-distribution")
    public ResponseEntity<?> getBloatDistributionData(
            @RequestParam(required = false) String databaseId
    ) {
        return ResponseEntity.ok(vacuumBloatService.getBloatDistributionData(databaseId));
    }

    /**
     * KPI 지표 조회
     * @param databaseId 데이터베이스 ID
     * @return 주요 KPI (총 Bloat, Critical 테이블 수, Bloat 증가량)
     */
    @GetMapping("/kpi")
    public ResponseEntity<?> getKpiData(
            @RequestParam(required = false) String databaseId
    ) {
        return ResponseEntity.ok(vacuumBloatService.getKpiData(databaseId));
    }
}