package com.dajanggan.domain.vacuum.controller;

import com.dajanggan.domain.vacuum.dto.VacuumDetailDto;
import com.dajanggan.domain.vacuum.service.VacuumDetailService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Vacuum-Detail", description = "vacuum 세션 상세 페이지 관련 API")
@RestController
@RequestMapping("/api/vacuum/detail")
@RequiredArgsConstructor
public class VacuumDetailController {

    private final VacuumDetailService vacuumDetailService;

    @GetMapping
    public ResponseEntity<VacuumDetailDto.Response> getVacuumDetail(
            @RequestParam Long databaseId,
            @RequestParam String tableName,
            @RequestParam(required = false) String executedAt) {

        log.info("GET /api/vacuum/detail - databaseId: {}, tableName: {}, executedAt: {}",
                databaseId, tableName, executedAt);

        VacuumDetailDto.Response detail = vacuumDetailService.getVacuumDetail(
                databaseId, tableName, executedAt);

        return ResponseEntity.ok(detail);
    }
}