package com.dajanggan.domain.engine.bgwriter.controller;

import com.dajanggan.domain.engine.bgwriter.dto.BgWriterDto;
import com.dajanggan.domain.engine.bgwriter.service.BgWriterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/dashboard/bgwriter")
@RequiredArgsConstructor
public class BgWriterController {

    private final BgWriterService bgWriterService;

    /**
     * BGWriter 대시보드 데이터 조회
     * @param instanceId PostgreSQL 인스턴스 ID (optional, 기본값은 설정된 기본 인스턴스)
     * @return BGWriter 대시보드 데이터
     */
    @GetMapping
    public ResponseEntity<BgWriterDto.DashboardResponse> getBgWriterDashboard(
            @RequestParam(required = false) Long instanceId) {
        log.debug("BGWriter 대시보드 조회 요청 - instanceId: {}", instanceId);
        
        BgWriterDto.DashboardResponse response = bgWriterService.getBgWriterDashboard(instanceId);
        
        return ResponseEntity.ok(response);
    }
}
