package com.dajanggan.domain.query.controller;

import com.dajanggan.domain.query.dto.agg5m.SlowQueryListDto;
import com.dajanggan.domain.query.dto.agg5m.TopSlowQueryDto;
import com.dajanggan.domain.query.service.QueryAgg5mService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 5분 집계 데이터 Controller
 * - 슬로우 쿼리 조회
 *
 * @author 이해든
 */
@Slf4j
@RestController
@RequestMapping("/api/query-agg-5m")
@RequiredArgsConstructor
@Tag(name = "Query Aggregation 5m", description = "5분 집계 데이터 API")
public class QueryAgg5mController {

    private final QueryAgg5mService queryAgg5mService;

    /**
     * 헬스 체크
     */
    @GetMapping("/health")
    @Operation(summary = "헬스 체크")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("message", "Query Aggregation 5m API is running");
        return ResponseEntity.ok(response);
    }

    /**
     * Top 슬로우 쿼리 조회 (Top 5)
     * 가장 느린 쿼리 Top 5를 조회
     */
    @GetMapping("/top-slow")
    @Operation(summary = "Top 슬로우 쿼리 조회", description = "가장 느린 쿼리 Top 5를 조회합니다")
    public ResponseEntity<Map<String, Object>> getTopSlowQueries(
            @Parameter(description = "인스턴스 ID", required = true)
            @RequestParam Long instanceId,
            @Parameter(description = "데이터베이스 ID", required = true)
            @RequestParam Long databaseId) {

        log.info("GET /api/query-agg-5m/top-slow - instanceId: {}, databaseId: {}",
                instanceId, databaseId);

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("instanceId", instanceId);
            params.put("databaseId", databaseId);

            TopSlowQueryDto data = queryAgg5mService.findTopSlowQueries(params);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);
            response.put("message", "조회 성공");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Top 슬로우 쿼리 조회 실패", e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "조회 실패: " + e.getMessage());

            return ResponseEntity.ok(response);
        }
    }

    /**
     * 슬로우 쿼리 리스트 조회
     * 슬로우 쿼리 목록을 조회
     */
    @GetMapping("/slow-list")
    @Operation(summary = "슬로우 쿼리 리스트 조회", description = "슬로우 쿼리 목록을 조회합니다")
    public ResponseEntity<Map<String, Object>> getSlowQueryList(
            @Parameter(description = "인스턴스 ID", required = true)
            @RequestParam Long instanceId,
            @Parameter(description = "데이터베이스 ID", required = true)
            @RequestParam Long databaseId,
            @Parameter(description = "조회 개수 (기본 20개)")
            @RequestParam(defaultValue = "20") Integer limit) {

        log.info("GET /api/query-agg-5m/slow-list - instanceId: {}, databaseId: {}, limit: {}",
                instanceId, databaseId, limit);

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("instanceId", instanceId);
            params.put("databaseId", databaseId);
            params.put("limit", limit);

            List<SlowQueryListDto> data = queryAgg5mService.findSlowQueryList(params);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);
            response.put("count", data.size());
            response.put("message", "조회 성공");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("슬로우 쿼리 리스트 조회 실패", e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "조회 실패: " + e.getMessage());

            return ResponseEntity.ok(response);
        }
    }
}