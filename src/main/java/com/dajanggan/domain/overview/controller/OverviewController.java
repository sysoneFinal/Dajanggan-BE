package com.dajanggan.domain.overview.controller;

import com.dajanggan.domain.overview.dto.DashboardLayoutResponse;
import com.dajanggan.domain.overview.dto.DashboardSaveRequest;
import com.dajanggan.domain.overview.service.OverviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;


@Tag(name = "Dashboard", description = "커스텀 대시보드 관련 API")
@RestController
@RequestMapping("/api/overview")
public class OverviewController {

    private final OverviewService overviewService;

    public OverviewController(OverviewService overviewService){
        this.overviewService = overviewService;
    }




    /** 사용자 레이아웃 조회 */
    @Operation(summary = "사용자 레이아웃 조회", description = "사용자 레이아웃을 조회합니다.")
    @GetMapping
    public ResponseEntity<DashboardLayoutResponse> getUserLayout(@RequestParam Long instanceId){
        DashboardLayoutResponse response = overviewService.getUserLayout(instanceId);
        return ResponseEntity.ok(response);
    }


    /** 대시보드 사용자 레이아웃 저정 */
    @Operation(summary = "사용자 레이아웃 저장", description = "커스터마이징된 대시보드 레이아웃을 저장합니다.")
    @PostMapping("/save")
    public ResponseEntity<Void> saveDashboardLayout (@RequestBody DashboardSaveRequest dashboardSaveRequest){
        overviewService.saveDashboardLayout(dashboardSaveRequest);
        return ResponseEntity.ok().build();
    }


}
