package com.dajanggan.domain.overview.controller;

import com.dajanggan.domain.overview.dto.DashboardSaveRequest;
import com.dajanggan.domain.overview.service.OverviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;


@Tag(name = "Dashboard", description = "대시보드 관련 API")
@RestController
@RequestMapping("/api/overview")
public class OverviewController {

    private final OverviewService overviewService;

    public OverviewController(OverviewService overviewService){
        this.overviewService = overviewService;
    }

    @Operation(summary = "대시보드 저장", description = "커스터마이징된 대시보드 레이아웃을 저장합니다.")
    @PostMapping("/save")
    public ResponseEntity<Void> saveDashboardLayout (@RequestBody DashboardSaveRequest dashboardSaveRequest){
        overviewService.saveDashboardLayout(dashboardSaveRequest);
        return ResponseEntity.ok().build();
    }
}
