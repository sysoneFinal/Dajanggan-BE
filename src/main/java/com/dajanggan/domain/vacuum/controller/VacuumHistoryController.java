package com.dajanggan.domain.vacuum.controller;

import com.dajanggan.domain.vacuum.dto.VacuumHistoryDto;
import com.dajanggan.domain.vacuum.service.VacuumHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/vacuum")
@RequiredArgsConstructor
public class VacuumHistoryController {

    private final VacuumHistoryService vacuumHistoryService;

    @GetMapping("/history")
    public ResponseEntity<List<VacuumHistoryDto.Response>> getHistory(
            @RequestParam(required = false) Integer hours,
            @RequestParam(required = false) String status) {

        log.info("GET /api/vacuum/history - hours: {}, status: {}", hours, status);

        VacuumHistoryDto.Request request = new VacuumHistoryDto.Request(hours, status);
        List<VacuumHistoryDto.Response> history = vacuumHistoryService.getVacuumHistory(request);

        return ResponseEntity.ok(history);
    }
}