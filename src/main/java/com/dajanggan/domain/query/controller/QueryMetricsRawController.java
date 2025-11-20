package com.dajanggan.domain.query.controller;

import com.dajanggan.domain.query.dto.QueryMetricsRawDto;
import com.dajanggan.domain.query.service.QueryMetricsRawService;
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
 * 쿼리 메트릭스 원시 데이터 Controller
 * - QueryOverview.tsx, QueryTuner.tsx, ExecutionStatus.tsx 데이터 제공
 *
 * ✅ 수정사항:
 * - getQueryMetricsByDatabaseId에 days 파라미터 추가 (기본값: 1일)
 *
 * @author 이해든
 */
@Slf4j
@RestController
@RequestMapping("/api/query-metrics")
@RequiredArgsConstructor
@Tag(name = "Query Metrics", description = "쿼리 메트릭스 API")
public class QueryMetricsRawController {

    private final QueryMetricsRawService queryMetricsRawService;

    /**
     * 헬스 체크 API
     * GET /api/query-metrics/health
     */
    @GetMapping("/health")
    @Operation(summary = "헬스 체크", description = "API 서버 상태 확인")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        log.info("GET /api/query-metrics/health");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("message", "Query Metrics API is running");
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }

    /**
     * 전체 쿼리 메트릭스 조회
     * GET /api/query-metrics
     */
    @GetMapping
    @Operation(summary = "전체 쿼리 메트릭스 조회", description = "모든 쿼리 메트릭스 데이터를 조회합니다")
    public ResponseEntity<Map<String, Object>> getAllQueryMetrics() {
        log.info("GET /api/query-metrics");

        List<QueryMetricsRawDto> data = queryMetricsRawService.getAllQueryMetrics();
        int totalCount = queryMetricsRawService.getTotalCount();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        response.put("totalCount", totalCount);
        response.put("message", "조회 성공");

        log.info("전체 쿼리 메트릭스 조회 완료: {} 건", data.size());
        return ResponseEntity.ok(response);
    }

    /**
     * ID로 쿼리 메트릭스 상세 조회
     * GET /api/query-metrics/{queryMetricId}
     */
    @GetMapping("/{queryMetricId}")
    @Operation(summary = "쿼리 메트릭스 상세 조회", description = "특정 쿼리 메트릭스를 ID로 조회합니다")
    public ResponseEntity<Map<String, Object>> getQueryMetricById(
            @Parameter(description = "쿼리 메트릭 ID")
            @PathVariable Long queryMetricId) {

        log.info("GET /api/query-metrics/{} - queryMetricId: {}", queryMetricId, queryMetricId);

        QueryMetricsRawDto data = queryMetricsRawService.getQueryMetricById(queryMetricId);

        Map<String, Object> response = new HashMap<>();
        if (data == null) {
            response.put("success", false);
            response.put("message", "해당 ID의 쿼리 메트릭스를 찾을 수 없습니다");
            return ResponseEntity.ok(response);
        }

        response.put("success", true);
        response.put("data", data);
        response.put("message", "조회 성공");

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/query-metrics/database/{databaseId}?days=1
     */
    @GetMapping("/database/{databaseId}")
    @Operation(summary = "데이터베이스별 쿼리 메트릭스 조회",
            description = "특정 데이터베이스의 쿼리 메트릭스를 조회합니다 (기본: 최근 1일)")
    public ResponseEntity<Map<String, Object>> getQueryMetricsByDatabaseId(
            @Parameter(description = "데이터베이스 ID")
            @PathVariable Long databaseId,
            @Parameter(description = "조회 기간 (일 단위, 기본값: 1일, 0 = 전체)")
            @RequestParam(defaultValue = "1") Integer days) {

        log.info("GET /api/query-metrics/database/{} - databaseId: {}, days: {}", databaseId, databaseId, days);

        List<QueryMetricsRawDto> data;

        // days가 0이면 전체 데이터, 그 외에는 해당 일수만큼의 데이터
        if (days == 0) {
            data = queryMetricsRawService.getQueryMetricsByDatabaseId(databaseId);
            log.info("📊 전체 데이터 조회");
        } else {
            data = queryMetricsRawService.getQueryMetricsByDatabaseIdAndDays(databaseId, days);
            log.info("📊 최근 {}일 데이터 조회", days);
        }

        int count = data.size();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        response.put("count", count);
        response.put("days", days);
        response.put("message", "조회 성공");

        log.info("✅ 데이터베이스별 조회 완료: databaseId={}, days={}, count={}", databaseId, days, count);
        return ResponseEntity.ok(response);
    }

    /**
     * 최근 N분 데이터 조회 (실시간 모니터링용)
     * GET /api/query-metrics/recent?databaseId={databaseId}&minutes={minutes}
     */
    @GetMapping("/recent")
    @Operation(summary = "최근 N분 데이터 조회",
            description = "실시간 모니터링을 위한 최근 N분간의 쿼리 메트릭스를 조회합니다")
    public ResponseEntity<Map<String, Object>> getRecentMetrics(
            @Parameter(description = "데이터베이스 ID")
            @RequestParam Long databaseId,
            @Parameter(description = "조회할 시간(분 단위, 기본값: 5분)")
            @RequestParam(defaultValue = "5") Integer minutes) {

        log.info("GET /api/query-metrics/recent - databaseId: {}, minutes: {}", databaseId, minutes);

        List<QueryMetricsRawDto> data = queryMetricsRawService.getRecentMetrics(databaseId, minutes);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        response.put("count", data.size());
        response.put("databaseId", databaseId);
        response.put("minutes", minutes);
        response.put("message", "조회 성공");

        log.info("최근 {}분 데이터 조회 완료: databaseId={}, count={}", minutes, databaseId, data.size());
        return ResponseEntity.ok(response);
    }

    /**
     * 쿼리 타입별 조회
     * GET /api/query-metrics/type/{queryType}
     */
    @GetMapping("/type/{queryType}")
    @Operation(summary = "쿼리 타입별 조회",
            description = "특정 타입(SELECT, INSERT, UPDATE, DELETE)의 쿼리를 조회합니다")
    public ResponseEntity<Map<String, Object>> getQueryMetricsByType(
            @Parameter(description = "쿼리 타입 (예: SELECT, INSERT, UPDATE, DELETE)")
            @PathVariable String queryType) {

        log.info("GET /api/query-metrics/type/{} - queryType: {}", queryType, queryType);

        List<QueryMetricsRawDto> data = queryMetricsRawService.getQueryMetricsByType(queryType);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        response.put("count", data.size());
        response.put("message", "조회 성공");

        return ResponseEntity.ok(response);
    }

    /**
     * 슬로우 쿼리 조회
     * GET /api/query-metrics/slow?thresholdMs=1000
     */
    @GetMapping("/slow")
    @Operation(summary = "슬로우 쿼리 조회",
            description = "지정된 임계값을 초과하는 느린 쿼리를 조회합니다")
    public ResponseEntity<Map<String, Object>> getSlowQueries(
            @Parameter(description = "임계값 (밀리초, 기본값: 1000)")
            @RequestParam(defaultValue = "1000") Double thresholdMs) {

        log.info("GET /api/query-metrics/slow - thresholdMs: {}", thresholdMs);

        List<QueryMetricsRawDto> data = queryMetricsRawService.getSlowQueries(thresholdMs);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        response.put("count", data.size());
        response.put("thresholdMs", thresholdMs);
        response.put("message", "조회 성공");

        log.info("슬로우 쿼리 조회 완료: thresholdMs={}, count={}", thresholdMs, data.size());
        return ResponseEntity.ok(response);
    }

    /**
     * CPU 사용량 상위 N개 조회
     * GET /api/query-metrics/top/cpu?limit=10
     */
    @GetMapping("/top/cpu")
    @Operation(summary = "CPU 사용량 상위 쿼리 조회",
            description = "CPU 사용량이 높은 상위 N개의 쿼리를 조회합니다")
    public ResponseEntity<Map<String, Object>> getTopByCpuUsage(
            @Parameter(description = "조회할 개수 (기본값: 10)")
            @RequestParam(defaultValue = "10") Integer limit) {

        log.info("GET /api/query-metrics/top/cpu - limit: {}", limit);

        List<QueryMetricsRawDto> data = queryMetricsRawService.getTopByCpuUsage(limit);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        response.put("count", data.size());
        response.put("limit", limit);
        response.put("message", "조회 성공");

        return ResponseEntity.ok(response);
    }

    /**
     * 메모리 사용량 상위 N개 조회
     * GET /api/query-metrics/top/memory?limit=10
     */
    @GetMapping("/top/memory")
    @Operation(summary = "메모리 사용량 상위 쿼리 조회",
            description = "메모리 사용량이 높은 상위 N개의 쿼리를 조회합니다")
    public ResponseEntity<Map<String, Object>> getTopByMemoryUsage(
            @Parameter(description = "조회할 개수 (기본값: 10)")
            @RequestParam(defaultValue = "10") Integer limit) {

        log.info("GET /api/query-metrics/top/memory - limit: {}", limit);

        List<QueryMetricsRawDto> data = queryMetricsRawService.getTopByMemoryUsage(limit);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        response.put("count", data.size());
        response.put("limit", limit);
        response.put("message", "조회 성공");

        return ResponseEntity.ok(response);
    }

    /**
     * 전체 쿼리 메트릭스 개수 조회
     * GET /api/query-metrics/count
     */
    @GetMapping("/count")
    @Operation(summary = "전체 쿼리 메트릭스 개수 조회",
            description = "저장된 전체 쿼리 메트릭스 개수를 반환합니다")
    public ResponseEntity<Map<String, Object>> getTotalCount() {
        log.info("GET /api/query-metrics/count");

        int count = queryMetricsRawService.getTotalCount();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("totalCount", count);
        response.put("message", "조회 성공");

        return ResponseEntity.ok(response);
    }

    /**
     *  ExecutionStatus용 쿼리별 집계 통계 조회
     * GET /api/query-metrics/execution-stats?databaseId={databaseId}&days={days}
     */
    @GetMapping("/execution-stats")
    @Operation(summary = "실행 통계 집계",
            description = "ExecutionStatus 페이지용 쿼리별 집계 데이터 (쿼리문별 실행횟수, 평균시간, 총시간)")
    public ResponseEntity<Map<String, Object>> getExecutionStats(
            @Parameter(description = "데이터베이스 ID", required = true)
            @RequestParam Long databaseId,
            @Parameter(description = "조회 기간 (시간 단위, 기본값: 1시간)")
            @RequestParam(defaultValue = "1") Integer hours) {

        log.info("GET /api/query-metrics/execution-stats - databaseId: {}, hours: {}", databaseId, hours);

        List<Map<String, Object>> data = queryMetricsRawService.getExecutionStats(databaseId, hours);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        response.put("count", data.size());
        response.put("databaseId", databaseId);
        response.put("hours", hours);
        response.put("message", "조회 성공");

        log.info("✅ 쿼리별 집계 조회 완료: databaseId={}, hours={}, count={}", databaseId, hours, data.size());
        return ResponseEntity.ok(response);
    }

    /**
     * 시간대별 쿼리 수 분포
     * GET /api/query-metrics/hourly-distribution?databaseId={databaseId}&hours={hours}
     */
    @GetMapping("/hourly-distribution")
    @Operation(summary = "시간대별 쿼리 수 분포",
            description = "시간대별 쿼리 수를 집계합니다 (기본: 최근 5시간)")
    public ResponseEntity<Map<String, Object>> getHourlyDistribution(
            @Parameter(description = "데이터베이스 ID", required = true)
            @RequestParam Long databaseId,
            @Parameter(description = "조회 기간 (시간 단위, 기본값: 5시간)")
            @RequestParam(defaultValue = "5") Integer hours) {

        log.info("GET /api/query-metrics/hourly-distribution - databaseId: {}, hours: {}", databaseId, hours);

        List<Map<String, Object>> data = queryMetricsRawService.getHourlyDistribution(databaseId, hours);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        response.put("count", data.size());
        response.put("hours", hours);
        response.put("message", "조회 성공");

        log.info("✅ 시간대별 분포 조회 완료: databaseId={}, hours={}, count={}", databaseId, hours, data.size());
        return ResponseEntity.ok(response);
    }
}