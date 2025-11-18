package com.dajanggan.domain.query.controller;

import com.dajanggan.domain.query.dto.agg1m.QueryAgg1mDto;
import com.dajanggan.domain.query.dto.agg1m.QuerySummaryDto;
import com.dajanggan.domain.query.dto.agg1m.QueryOverviewTrendDto;
import com.dajanggan.domain.query.service.QueryAgg1mService;
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
 * 1분 집계 데이터 Controller
 * - QueryOverview용 집계 API 제공
 * - 요약 데이터 및 트렌드 데이터 조회
 *
 * @author 이해든
 */
@Slf4j
@RestController
@RequestMapping("/api/query-agg-1m")
@RequiredArgsConstructor
@Tag(name = "Query Aggregation 1m", description = "1분 집계 데이터 API")
public class QueryAgg1mController {

    private final QueryAgg1mService queryAgg1mService;

    /**
     * 헬스 체크 API
     * GET /api/query-agg-1m/health
     */
    @GetMapping("/health")
    @Operation(summary = "헬스 체크", description = "API 서버 상태 확인")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        log.info("GET /api/query-agg-1m/health");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("message", "Query Aggregation 1m API is running");
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }

    /**
     * 🆕 요약 데이터 조회 (최근 5분 집계)
     * GET /api/query-agg-1m/summary?instanceId={instanceId}&databaseId={databaseId}
     *
     * QueryOverview의 요약 카드(TPS, QPS, 세션수, 응답시간) 데이터 제공
     */
    @GetMapping("/summary")
    @Operation(summary = "요약 데이터 조회",
            description = "최근 5분간의 집계 데이터를 기반으로 TPS, QPS, 활성 세션, 평균 응답 시간 등을 제공합니다")
    public ResponseEntity<Map<String, Object>> getSummary(
            @Parameter(description = "인스턴스 ID", required = true)
            @RequestParam Long instanceId,
            @Parameter(description = "데이터베이스 ID", required = true)
            @RequestParam Long databaseId) {

        log.info("GET /api/query-agg-1m/summary - instanceId: {}, databaseId: {}", instanceId, databaseId);

        try {
            QuerySummaryDto data = queryAgg1mService.findRecentSummary(instanceId, databaseId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);
            response.put("message", "조회 성공");

            log.info("✅ 요약 데이터 조회 완료 - TPS: {}, QPS: {}",
                    data.getCurrentTps(), data.getCurrentQps());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ 요약 데이터 조회 중 오류 발생", e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "조회 실패: " + e.getMessage());

            return ResponseEntity.ok(response);
        }
    }

    /**
     * 🆕 트렌드 데이터 조회 (최근 N시간)
     * GET /api/query-agg-1m/trend?instanceId={instanceId}&databaseId={databaseId}&hours={hours}
     *
     * QueryOverview의 TPS/QPS 차트 데이터 제공
     */
    @GetMapping("/trend")
    @Operation(summary = "트렌드 데이터 조회",
            description = "최근 N시간의 1분 집계 데이터를 시간순으로 제공합니다 (TPS/QPS 차트용)")
    public ResponseEntity<Map<String, Object>> getTrend(
            @Parameter(description = "인스턴스 ID", required = true)
            @RequestParam Long instanceId,
            @Parameter(description = "데이터베이스 ID", required = true)
            @RequestParam Long databaseId,
            @Parameter(description = "조회할 시간 (시간 단위, 기본값: 12시간)")
            @RequestParam(defaultValue = "12") Integer hours) {

        log.info("GET /api/query-agg-1m/trend - instanceId: {}, databaseId: {}, hours: {}",
                instanceId, databaseId, hours);

        try {
            QueryOverviewTrendDto data = queryAgg1mService.findTrendData(instanceId, databaseId, hours);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);
            response.put("message", "조회 성공");

            log.info("✅ 트렌드 데이터 조회 완료 - 데이터 포인트: {}", data.getTotalDataPoints());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ 트렌드 데이터 조회 중 오류 발생", e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "조회 실패: " + e.getMessage());

            return ResponseEntity.ok(response);
        }
    }

    /**
     * 🆕 Top Query 조회 (리소스별)
     * GET /api/query-agg-1m/top-queries?instanceId={instanceId}&databaseId={databaseId}&orderBy={orderBy}&limit={limit}
     */
    @GetMapping("/top-queries")
    @Operation(summary = "Top Query 조회",
            description = "CPU, 메모리, I/O, 실행시간 기준 Top-N 쿼리를 조회합니다")
    public ResponseEntity<Map<String, Object>> getTopQueries(
            @Parameter(description = "인스턴스 ID", required = true)
            @RequestParam Long instanceId,
            @Parameter(description = "데이터베이스 ID", required = true)
            @RequestParam Long databaseId,
            @Parameter(description = "정렬 기준 (cpu, memory, io, execution_time)", required = true)
            @RequestParam String orderBy,
            @Parameter(description = "조회 개수 (기본 5개)")
            @RequestParam(defaultValue = "5") Integer limit) {

        log.info("GET /api/query-agg-1m/top-queries - instanceId: {}, databaseId: {}, orderBy: {}, limit: {}",
                instanceId, databaseId, orderBy, limit);

        try {
            List<QueryAgg1mDto> data = queryAgg1mService.findTopQueries(instanceId, databaseId, orderBy, limit);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);
            response.put("count", data.size());
            response.put("message", "조회 성공");

            log.info("✅ Top Query 조회 완료 - {}개", data.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Top Query 조회 중 오류 발생", e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "조회 실패: " + e.getMessage());

            return ResponseEntity.ok(response);
        }
    }

    /**
     * 현재 리소스 메트릭 조회
     * GET /api/query-agg-1m/resources/current
     */
    @GetMapping("/resources/current")
    @Operation(summary = "현재 리소스 메트릭 조회",
            description = "현재 시스템의 CPU, Memory, Disk I/O 사용률을 조회합니다")
    public ResponseEntity<Map<String, Object>> getCurrentResources() {
        log.info("GET /api/query-agg-1m/resources/current");

        try {
            Map<String, Double> metrics = queryAgg1mService.collectResourceMetrics();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", metrics);
            response.put("message", "조회 성공");

            log.info("✅ 리소스 메트릭 조회 완료 - CPU: {}%, Memory: {}%, Disk: {}%",
                    metrics.get("cpuUsagePercent"),
                    metrics.get("memoryUsagePercent"),
                    metrics.get("diskIoUsagePercent"));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ 리소스 메트릭 조회 실패", e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "조회 실패: " + e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 과거 리소스 데이터 수동 업데이트
     * POST /api/query-agg-1m/resources/update-past?days=7
     */
    @PostMapping("/resources/update-past")
    @Operation(summary = "과거 리소스 데이터 업데이트",
            description = "최근 N일 동안의 NULL 리소스 데이터를 채웁니다")
    public ResponseEntity<Map<String, Object>> updatePastResources(
            @Parameter(description = "업데이트할 일수 (기본 7일)")
            @RequestParam(defaultValue = "7") int days) {

        log.info("POST /api/query-agg-1m/resources/update-past - days: {}", days);

        try {
            int updated = queryAgg1mService.updateResourceMetricsForPastDays(days);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "리소스 데이터 업데이트 완료");
            response.put("updatedCount", updated);
            response.put("days", days);

            log.info("✅ 과거 리소스 데이터 업데이트 완료: {} 건", updated);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ 과거 리소스 데이터 업데이트 실패", e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "업데이트 실패: " + e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }
}