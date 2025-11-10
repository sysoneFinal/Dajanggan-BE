package com.dajanggan.domain.system.memory.controller;

import com.dajanggan.domain.system.memory.dto.MemoryDto;
import com.dajanggan.domain.system.memory.service.MemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/dashboard/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;

    /**
     * Memory 대시보드 데이터 조회
     * @param instanceId PostgreSQL 인스턴스 ID (optional, 기본값은 설정된 기본 인스턴스)
     * @return Memory 대시보드 데이터
     */
    @GetMapping
    public ResponseEntity<MemoryDto.DashboardResponse> getMemoryDashboard(
            @RequestParam(required = false) Long instanceId) {
        log.debug("Memory 대시보드 조회 요청 - instanceId: {}", instanceId);

        MemoryDto.DashboardResponse response = memoryService.getMemoryDashboard(instanceId);

        return ResponseEntity.ok(response);
    }

    /**
     * Memory 리스트 데이터 조회
     * @param instanceId PostgreSQL 인스턴스 ID (optional)
     * @param type 타입 필터 (table, index) - 콤마로 구분
     * @param status 상태 필터 (정상, 주의, 위험) - 콤마로 구분
     * @return Memory 리스트 데이터
     */
    @GetMapping("/list")
    public ResponseEntity<MemoryDto.ListResponse> getMemoryList(
            @RequestParam(required = false) Long instanceId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        log.debug("Memory 리스트 조회 요청 - instanceId: {}, type: {}, status: {}",
                instanceId, type, status);

        // type 파라미터를 List로 변환
        List<String> typeList = null;
        if (type != null && !type.isEmpty()) {
            typeList = List.of(type.split(","));
        }

        // status 파라미터를 List로 변환
        List<String> statusList = null;
        if (status != null && !status.isEmpty()) {
            statusList = List.of(status.split(","));
        }

        MemoryDto.ListResponse response = memoryService.getMemoryList(instanceId, typeList, statusList);

        return ResponseEntity.ok(response);
    }
}