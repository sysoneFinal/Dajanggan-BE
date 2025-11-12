package com.dajanggan.domain.engine.hotindex.controller;

import com.dajanggan.domain.engine.hotindex.dto.HotIndexDto;
import com.dajanggan.domain.engine.hotindex.service.HotIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/engine/hotindex")
@RequiredArgsConstructor
public class HotIndexController {

    private final HotIndexService hotIndexService;

    /**
     * HotIndex 대시보드 데이터 조회
     * @param databaseId 데이터베이스 ID (optional, 기본값은 설정된 기본 데이터베이스)
     * @return HotIndex 대시보드 데이터
     */
    @GetMapping
    public ResponseEntity<HotIndexDto.DashboardResponse> getHotIndexDashboard(
            @RequestParam(required = false) Long databaseId) {
        log.debug("HotIndex 대시보드 조회 요청 - databaseId: {}", databaseId);

        HotIndexDto.DashboardResponse response = hotIndexService.getHotIndexDashboard(databaseId);

        return ResponseEntity.ok(response);
    }

    /**
     * HotIndex 리스트 데이터 조회
     * @param databaseId 데이터베이스 ID (optional)
     * @param timeRange 시간 범위 (1h, 6h, 24h, 7d)
     * @param status 상태 필터 (정상, 비효율, 미사용, bloat) - 콤마로 구분
     * @return HotIndex 리스트 데이터
     */
    @GetMapping("/list")
    public ResponseEntity<HotIndexDto.ListResponse> getHotIndexList(
            @RequestParam(required = false) Long databaseId,
            @RequestParam(defaultValue = "7d") String timeRange,
            @RequestParam(required = false) String status) {
        log.debug("HotIndex 리스트 조회 요청 - databaseId: {}, timeRange: {}, status: {}",
                databaseId, timeRange, status);

        // status 파라미터를 List로 변환
        List<String> statusList = null;
        if (status != null && !status.isEmpty()) {
            statusList = List.of(status.split(","));
        }

        HotIndexDto.ListResponse response = hotIndexService.getHotIndexList(databaseId, timeRange, statusList);

        return ResponseEntity.ok(response);
    }
}