// 작성자: 김민서
package com.dajanggan.domain.vacuum.controller;

import com.dajanggan.domain.vacuum.dto.VacuumBloatDto;
import com.dajanggan.domain.vacuum.service.VacuumBloatService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Vacuum-bloat", description = "vacuum bloat 페이지 관련 API")
@RestController
@RequestMapping("/api/vacuum/bloat")
@RequiredArgsConstructor
public class VacuumBloatController {

    private final VacuumBloatService vacuumBloatService;

    @Tag(name = "Vacuum-bloat-dashboard", description = "vacuum bloat Xmin Horizon, 전체 Bloat 추이, Bloat 비율별 분포 그래프를 조회합니다")
    @GetMapping("/dashboard")
    public ResponseEntity<VacuumBloatDto.Response> getDashboard(
            @RequestParam Long databaseId,
            @RequestParam Long instanceId) {

        log.info("GET /api/vacuum/bloat/dashboard - databaseId: {}, instanceId: {}",
                databaseId, instanceId);

        VacuumBloatDto.Response dashboard = vacuumBloatService.getDashboardData(
                databaseId, instanceId);

        return ResponseEntity.ok(dashboard);
    }

    @Tag(name = "Vacuum-bloat-kpi", description = "vacuum Table Bloat 예상치, 위험 테이블, Bloat 증가량을 조회합니다")
    @GetMapping("/kpi")
    public ResponseEntity<VacuumBloatDto.Kpi> getKpi(
            @RequestParam Long databaseId,
            @RequestParam Long instanceId) {

        log.info("GET /api/vacuum/bloat/kpi - databaseId: {}, instanceId: {}",
                databaseId, instanceId);

        VacuumBloatDto.Kpi data = vacuumBloatService.getKpiData(databaseId, instanceId);

        return ResponseEntity.ok(data);
    }
}