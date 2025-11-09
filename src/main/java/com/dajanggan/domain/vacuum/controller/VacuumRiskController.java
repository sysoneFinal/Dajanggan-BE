package com.dajanggan.domain.vacuum.controller;

import com.dajanggan.domain.vacuum.dto.VacuumRiskDto;
import com.dajanggan.domain.vacuum.service.VacuumRiskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/vacuum")
@RequiredArgsConstructor
public class VacuumRiskController {

    private final VacuumRiskService vacuumRiskService;

    @GetMapping("/risk")
    public ResponseEntity<VacuumRiskDto.Response> getRisk(
            @RequestParam(defaultValue = "24") int hours) {

        log.info("GET /api/vacuum/risk - hours: {}", hours);
        VacuumRiskDto.Response risk = vacuumRiskService.getRiskData(hours);

        return ResponseEntity.ok(risk);
    }
}