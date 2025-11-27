// 작성자 : 김동현
package com.dajanggan.domain.engine.bgwriter.controller;

import com.dajanggan.domain.engine.bgwriter.dto.*;
import com.dajanggan.domain.engine.bgwriter.service.BgWriterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/engine/bgwriter")
@RequiredArgsConstructor
public class BgWriterController {

    private final BgWriterService bgWriterService;

    /**
     * BGWriter 대시보드 데이터 조회
     */
    @GetMapping
    public ResponseEntity<BgWriterDashboardResponse> getBgWriterDashboard(
            @RequestParam(required = false) Long instanceId) {
        log.debug("BGWriter 대시보드 조회 요청 - instanceId: {}", instanceId);
        
        BgWriterDashboardResponse response = bgWriterService.getBgWriterDashboard(instanceId);
        
        return ResponseEntity.ok(response);
    }

    /**
     * BGWriter 리스트 데이터 조회
     */
    @GetMapping("/list")
    public ResponseEntity<BgWriterListResponse> getBgWriterList(
            @RequestParam(required = false) Long instanceId,
            @RequestParam(defaultValue = "7d") String timeRange,
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
