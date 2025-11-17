package com.dajanggan.domain.overview.controller;

import com.dajanggan.domain.overview.dto.DatabaseMetricsAgg;
import com.dajanggan.domain.overview.service.DatabaseSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "database-summary", description = "데이터베이스 요약 페이지 API")
@RequestMapping("/api/database/summary")
@RestController
public class DatabaseSummaryController {
    private final DatabaseSummaryService databaseSummaryService;

    public DatabaseSummaryController(DatabaseSummaryService databaseSummaryService){
        this.databaseSummaryService = databaseSummaryService;
    }

    @Operation(summary = "요약페이지 지표 조회", description = "DB 요약페이지 모든 지표를 조회합니다")
    @GetMapping
    public ResponseEntity<List<DatabaseMetricsAgg>> findSummaryMetrics(@RequestParam Long databaseId){
        List<DatabaseMetricsAgg> response = databaseSummaryService.getDatabaseSummaryMetrics(databaseId);
        return ResponseEntity.ok(response);
    }

}
