package com.dajanggan.domain.vacuum.controller;

import com.dajanggan.domain.vacuum.dto.VacuumHistoryDto;
import com.dajanggan.domain.vacuum.service.VacuumHistoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@Tag(name = "Vacuum-History", description = "vacuum history 페이지 관련 API")
@RestController
@RequestMapping("/api/vacuum")
@RequiredArgsConstructor
public class VacuumHistoryController {

    private final VacuumHistoryService vacuumHistoryService;

    @Tag(name = "Vacuum-History-history", description = "vacuum history 테이블을 조회합니다")
    @GetMapping("/history")
    public ResponseEntity<List<VacuumHistoryDto.Response>> getHistory(
            @RequestParam(required = false) Long databaseId,  // databaseId 추가
            @RequestParam(required = false) Integer hours,
            @RequestParam(required = false) String status) {

        log.info("GET /api/vacuum/history - databaseId: {}, hours: {}, status: {}",
                databaseId, hours, status);

        VacuumHistoryDto.Request request = new VacuumHistoryDto.Request(databaseId, hours, status);
        List<VacuumHistoryDto.Response> history = vacuumHistoryService.getVacuumHistory(request);

        return ResponseEntity.ok(history);
    }
}