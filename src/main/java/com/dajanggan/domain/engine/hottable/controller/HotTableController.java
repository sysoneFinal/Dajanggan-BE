package com.dajanggan.domain.engine.hottable.controller;

import com.dajanggan.domain.engine.hottable.dto.HotTableDto;
import com.dajanggan.domain.engine.hottable.service.HotTableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/engine/hottable")
@RequiredArgsConstructor
public class HotTableController {

    private final HotTableService hotTableService;

    /**
     * HotTable 대시보드 데이터 조회
     * @param instanceId 인스턴스 ID (optional, 기본값은 설정된 기본 인스턴스)
     * @param databaseId 데이터베이스 ID (optional, 기본값은 설정된 기본 데이터베이스)
     * @return HotTable 대시보드 데이터
     */
    @GetMapping
    public ResponseEntity<HotTableDto.DashboardResponse> getHotTableDashboard(
            @RequestParam(required = false) Long instanceId,
            @RequestParam(required = false) Long databaseId) {
        log.debug("HotTable 대시보드 조회 요청 - instanceId: {}, databaseId: {}", instanceId, databaseId);

        HotTableDto.DashboardResponse response = hotTableService.getHotTableDashboard(instanceId, databaseId);

        return ResponseEntity.ok(response);
    }

    /**
     * HotTable 리스트 데이터 조회
     * @param instanceId 인스턴스 ID (optional)
     * @param databaseId 데이터베이스 ID (optional)
     * @param timeRange 시간 범위 (1h, 6h, 24h, 7d)
     * @param status 상태 필터 (정상, 주의, 위험) - 콤마로 구분
     * @return HotTable 리스트 데이터
     */
    @GetMapping("/list")
    public ResponseEntity<HotTableDto.ListResponse> getHotTableList(
            @RequestParam(required = false) Long instanceId,
            @RequestParam(required = false) Long databaseId,
            @RequestParam(defaultValue = "7d") String timeRange,
            @RequestParam(required = false) String status) {
        log.debug("HotTable 리스트 조회 요청 - instanceId: {}, databaseId: {}, timeRange: {}, status: {}",
                instanceId, databaseId, timeRange, status);

        // status 파라미터를 List로 변환
        List<String> statusList = null;
        if (status != null && !status.isEmpty()) {
            statusList = List.of(status.split(","));
        }

        HotTableDto.ListResponse response = hotTableService.getHotTableList(instanceId, databaseId, timeRange, statusList);

        return ResponseEntity.ok(response);
    }
}