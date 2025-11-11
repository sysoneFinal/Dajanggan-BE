package com.dajanggan.domain.vacuum.controller;

import com.dajanggan.domain.vacuum.dto.VacuumDetailDto;
import com.dajanggan.domain.vacuum.service.VacuumDetailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/vacuum/detail")
@RequiredArgsConstructor
public class VacuumDetailController {

    private final VacuumDetailService vacuumDetailService;

    @GetMapping
    public ResponseEntity<VacuumDetailDto.Response> getVacuumDetail(
            @RequestParam Long databaseId,
            @RequestParam String tableName) {

        log.info("GET /api/vacuum/detail - databaseId: {}, tableName: {}",
                databaseId, tableName);

        VacuumDetailDto.Response detail = vacuumDetailService.getVacuumDetail(
                databaseId, tableName);

        return ResponseEntity.ok(detail);
    }
}