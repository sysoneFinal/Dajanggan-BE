// 작성자 : 김동현
package com.dajanggan.domain.engine.bgwriter.controller;

import com.dajanggan.domain.engine.bgwriter.dto.*;
import com.dajanggan.domain.engine.bgwriter.service.BgWriterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "BGWriter", description = "Background Writer 메트릭 모니터링 API")
@RestController
@RequestMapping("/api/engine/bgwriter")
@RequiredArgsConstructor
public class BgWriterController {

    private final BgWriterService bgWriterService;

    /**
     * BGWriter 대시보드 데이터 조회
     */
    @Operation(summary = "BGWriter 대시보드 조회", description = "실시간 Background Writer 통계 및 성능 지표 조회")
    @GetMapping
    public ResponseEntity<BgWriterDashboardResponse> getBgWriterDashboard(
            @Parameter(description = "PostgreSQL 인스턴스 ID", example = "1")
            @RequestParam(required = false) Long instanceId) {
        log.debug("BGWriter 대시보드 조회 요청 - instanceId: {}", instanceId);
        
        BgWriterDashboardResponse response = bgWriterService.getBgWriterDashboard(instanceId);
        
        return ResponseEntity.ok(response);
    }

    /**
     * BGWriter 리스트 데이터 조회
     */
    @Operation(summary = "BGWriter 리스트 조회", description = "시간 범위 및 상태별로 필터링된 Background Writer 메트릭 리스트 조회")
    @GetMapping("/list")
    public ResponseEntity<BgWriterListResponse> getBgWriterList(
            @Parameter(description = "PostgreSQL 인스턴스 ID", example = "1")
            @RequestParam(required = false) Long instanceId,
            @Parameter(description = "시간 범위 (1h, 6h, 24h, 7d)", example = "7d")
            @RequestParam(defaultValue = "7d") String timeRange,
            @Parameter(description = "상태 필터 (정상,주의,위험 - 콤마 구분)", example = "주의,위험")
            @RequestParam(required = false) String status) {
        log.debug("BGWriter 리스트 조회 요청 - instanceId: {}, timeRange: {}, status: {}", 
                instanceId, timeRange, status);
        
        // status 파라미터를 List로 변환
        List<String> statusList = null;
        if (status != null && !status.isEmpty()) {
            statusList = List.of(status.split(","));
        }
        
        BgWriterListResponse response = bgWriterService.getBgWriterList(instanceId, timeRange, statusList);
        
        return ResponseEntity.ok(response);
    }
}
