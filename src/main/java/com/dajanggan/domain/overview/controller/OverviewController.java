package com.dajanggan.domain.overview.controller;

import com.dajanggan.domain.overview.dto.DashboardDataResponse;
import com.dajanggan.domain.overview.dto.DashboardSaveRequest;
import com.dajanggan.domain.overview.service.OverviewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@Tag(name = "Dashboard", description = "커스텀 대시보드 관련 API")
@RestController
@RequestMapping("/api/overview")
public class OverviewController {

    private final OverviewService overviewService;

    public OverviewController(OverviewService overviewService){
        this.overviewService = overviewService;
    }


    /** 사용자 대시보드 조회 (레이아웃 + 데이터) */
    @Operation(summary = "대시보드 조회", description = "사용자 레이아웃과 실제 메트릭 데이터를 함께 조회합니다.")
    @GetMapping
    public ResponseEntity<DashboardDataResponse> getDashboard(@RequestParam Long instanceId){
        DashboardDataResponse response = overviewService.getDashboardWithData(instanceId);
        
        // null 체크 추가
        if (response == null || response.getWidgets() == null) {
            log.info("대시보드 데이터 없음: instanceId={} -> 빈 응답 반환", instanceId);
            return ResponseEntity.ok(DashboardDataResponse.builder()
                    .widgets(java.util.Collections.emptyList())
                    .build());
        }
        
        log.info("대시보드 데이터 조회 완료: instanceId={}, widgetCount={}", 
                instanceId, response.getWidgets().size());
        
        // 각 위젯의 데이터 개수 로그
        response.getWidgets().forEach(widget -> {
            log.info("  Widget[{}]: dataCount={}, error={}", 
                    widget.getId(), 
                    widget.getData() != null ? widget.getData().size() : 0,
                    widget.getError());
        });
        
        return ResponseEntity.ok(response);
    }


    /** 대시보드 사용자 레이아웃 저장 */
    @Operation(summary = "사용자 레이아웃 저장", description = "커스터마이징된 대시보드 레이아웃을 저장합니다.")
    @PostMapping("/save")
    public ResponseEntity<Void> saveDashboardLayout (@RequestBody DashboardSaveRequest dashboardSaveRequest){
        overviewService.saveDashboardLayout(dashboardSaveRequest);
        return ResponseEntity.ok().build();
    }

}
