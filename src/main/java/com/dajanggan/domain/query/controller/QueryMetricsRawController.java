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
 * ========================================
 * 쿼리 메트릭스 원시 데이터 컨트롤러
 * ========================================
 *
 * [주요 기능]
 * 1. 원시 쿼리 메트릭 조회
 *    - PostgreSQL에서 직접 수집한 실시간 데이터
 *    - pg_stat_statements 기반 메트릭
 *
 * 2. 다양한 조회 조건 지원
 *    - 데이터베이스별 조회
 *    - 기간별 조회 (최근 N일, N분)
 *    - 쿼리 타입별 조회
 *    - 슬로우 쿼리 조회
 *    - 리소스 사용량 기준 Top N 조회
 *
 * 3. 집계 데이터 제공
 *    - ExecutionStatus용 쿼리별 집계 통계
 *    - 시간대별 쿼리 수 분포
 *
 * [데이터 특성]
 * - 실시간성: 1분 주기 수집
 * - 상세도: 개별 쿼리 실행 정보
 * - 보관 기간: 일반적으로 7~30일
 *
 * [활용 페이지]
 * - QueryOverview.tsx: 실시간 모니터링
 * - QueryTuner.tsx: 쿼리 튜닝
 * - ExecutionStatus.tsx: 실행 통계
 *
 * 작성자: 이해든
 */
@Slf4j
@RestController
@RequestMapping("/api/query-metrics")
@RequiredArgsConstructor
@Tag(name = "Query Metrics", description = "쿼리 메트릭스 API")
public class QueryMetricsRawController {

    // ============================================
    // 의존성 주입
    // ============================================

    /** 쿼리 메트릭스 원시 데이터 서비스 */
    private final QueryMetricsRawService queryMetricsRawService;

    // ============================================
    // API 엔드포인트 - 헬스체크
    // ============================================

    /**
     * 헬스 체크 엔드포인트
     *
     * [용도]
     * - API 서버 정상 작동 확인
     * - 모니터링 시스템 연동
     *
     * @return 서버 상태 정보
     *      *  ----------  ------  --------------------------------------------------
     *      *  작업일자      작성자    Description
     *      *  ----------  ------  --------------------------------------------------
     *      *  2025-11-10  이해든    1. 최초작성자
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

    // ============================================
    // API 엔드포인트 - 전체 조회
    // ============================================

    /**
     * 전체 쿼리 메트릭스 조회
     *
     * [주의사항]
     * - 대량 데이터 조회로 성능 영향 가능
     * - 프로덕션 환경에서는 제한적 사용 권장
     * - 페이징 처리 고려 필요
     *
     * [용도]
     * - 관리자 전체 데이터 확인
     * - 데이터 마이그레이션
     * - 통계 분석
     *
     * @return 전체 쿼리 메트릭스 목록
     *  ----------  ------  --------------------------------------------------
     *  작업일자      작성자    Description
     *  ----------  ------  --------------------------------------------------
     *  2025-11-10  이해든    1. 최초작성자
     */
    @GetMapping
    @Operation(summary = "전체 쿼리 메트릭스 조회", description = "모든 쿼리 메트릭스 데이터를 조회합니다")
    public ResponseEntity<Map<String, Object>> getAllQueryMetrics() {
        log.info("GET /api/query-metrics");

        // ----------------------------------------
        // 1. 전체 데이터 조회
        // ----------------------------------------

        List<QueryMetricsRawDto> data = queryMetricsRawService.getAllQueryMetrics();
        int totalCount = queryMetricsRawService.getTotalCount();

        // ----------------------------------------
        // 2. 응답 생성
        // ----------------------------------------

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        response.put("totalCount", totalCount);
        response.put("message", "조회 성공");

        log.info("전체 쿼리 메트릭스 조회 완료: {} 건", data.size());
        return ResponseEntity.ok(response);
    }

    // ============================================
    // API 엔드포인트 - ID 기반 조회
    // ============================================

    /**
     * ID로 쿼리 메트릭스 상세 조회
     *
     * [용도]
     * - 특정 쿼리 실행 정보 상세 확인
     * - 쿼리 튜닝 분석
     * - 이상 쿼리 추적
     *
     * @param queryMetricId 쿼리 메트릭 ID
     * @return 쿼리 메트릭스 상세 정보
     *  ----------  ------  --------------------------------------------------
     *  작업일자      작성자    Description
     *  ----------  ------  --------------------------------------------------
     *  2025-11-10  이해든    1. 최초작성자
     */
    @GetMapping("/{queryMetricId}")
    @Operation(summary = "쿼리 메트릭스 상세 조회", description = "특정 쿼리 메트릭스를 ID로 조회합니다")
    public ResponseEntity<Map<String, Object>> getQueryMetricById(
            @Parameter(description = "쿼리 메트릭 ID")
            @PathVariable Long queryMetricId) {

        log.info("GET /api/query-metrics/{} - queryMetricId: {}", queryMetricId, queryMetricId);

        // ----------------------------------------
        // 1. 데이터 조회
        // ----------------------------------------

        QueryMetricsRawDto data = queryMetricsRawService.getQueryMetricById(queryMetricId);

        // ----------------------------------------
        // 2. 응답 생성
        // ----------------------------------------

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

    // ============================================
    // API 엔드포인트 - 데이터베이스별 조회
    // ============================================

    /**
     * 데이터베이스별 쿼리 메트릭스 조회
     *
     * [조회 옵션]
     * - days = 0: 전체 데이터
     * - days > 0: 최근 N일 데이터
     *
     * [기본값]
     * - days = 1 (최근 1일)
     *
     * [성능 최적화]
     * - 인덱스: (database_id, collected_at)
     * - 최대 10,000건 제한
     *
     * @param databaseId 데이터베이스 ID
     * @param days 조회 기간 (일 단위, 0=전체)
     * @return 데이터베이스별 쿼리 메트릭스 목록
     *  ----------  ------  --------------------------------------------------
     *  작업일자      작성자    Description
     *  ----------  ------  --------------------------------------------------
     *  2025-11-18  이해든    1. 최초작성자
     */
    @GetMapping("/database/{databaseId}")
    @Operation(
            summary = "데이터베이스별 쿼리 메트릭스 조회",
            description = "특정 데이터베이스의 쿼리 메트릭스를 조회합니다 (기본: 최근 1일)"
    )
    public ResponseEntity<Map<String, Object>> getQueryMetricsByDatabaseId(
            @Parameter(description = "데이터베이스 ID")
            @PathVariable Long databaseId,
            @Parameter(description = "조회 기간 (일 단위, 기본값: 1일, 0 = 전체)")
            @RequestParam(defaultValue = "1") Integer days) {

        log.info("GET /api/query-metrics/database/{} - databaseId: {}, days: {}", databaseId, databaseId, days);

        // ----------------------------------------
        // 1. 데이터 조회
        // ----------------------------------------

        List<QueryMetricsRawDto> data;

        if (days == 0) {
            // 전체 데이터 조회
            data = queryMetricsRawService.getQueryMetricsByDatabaseId(databaseId);
            log.info("전체 데이터 조회");
        } else {
            // 기간별 데이터 조회
            data = queryMetricsRawService.getQueryMetricsByDatabaseIdAndDays(databaseId, days);
            log.info("최근 {}일 데이터 조회", days);
        }

        int count = data.size();

        // ----------------------------------------
        // 2. 응답 생성
        // ----------------------------------------

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        response.put("count", count);
        response.put("days", days);
        response.put("message", "조회 성공");

        log.info("데이터베이스별 조회 완료: databaseId={}, days={}, count={}", databaseId, days, count);
        return ResponseEntity.ok(response);
    }

    // ============================================
    // API 엔드포인트 - 실시간 모니터링
    // ============================================

    /**
     * 최근 N분 데이터 조회 (실시간 모니터링용)
     *
     * [용도]
     * - 실시간 쿼리 모니터링
     * - 현재 시스템 상태 파악
     * - 장애 발생 시 즉시 분석
     *
     * [기본값]
     * - minutes = 5 (최근 5분)
     *
     * [갱신 주기]
     * - 1분마다 새로운 데이터 수집
     * - 대시보드 자동 갱신 권장
     *
     * @param databaseId 데이터베이스 ID
     * @param minutes 조회 시간 (분 단위)
     * @return 최근 N분간 쿼리 메트릭스
     *  ----------  ------  --------------------------------------------------
     *  작업일자      작성자    Description
     *  ----------  ------  --------------------------------------------------
     *  2025-11-10  이해든    1. 최초작성자
     */
    @GetMapping("/recent")
    @Operation(
            summary = "최근 N분 데이터 조회",
            description = "실시간 모니터링을 위한 최근 N분간의 쿼리 메트릭스를 조회합니다"
    )
    public ResponseEntity<Map<String, Object>> getRecentMetrics(
            @Parameter(description = "데이터베이스 ID")
            @RequestParam Long databaseId,
            @Parameter(description = "조회할 시간(분 단위, 기본값: 5분)")
            @RequestParam(defaultValue = "5") Integer minutes) {

        log.info("GET /api/query-metrics/recent - databaseId: {}, minutes: {}", databaseId, minutes);

        // ----------------------------------------
        // 1. 최근 데이터 조회
        // ----------------------------------------

        List<QueryMetricsRawDto> data = queryMetricsRawService.getRecentMetrics(databaseId, minutes);

        // ----------------------------------------
        // 2. 응답 생성
        // ----------------------------------------

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

    // ============================================
    // API 엔드포인트 - 쿼리 타입별 조회
    // ============================================

    /**
     * 쿼리 타입별 조회
     *
     * [지원 타입]
     * - SELECT: 조회 쿼리
     * - INSERT: 삽입 쿼리
     * - UPDATE: 수정 쿼리
     * - DELETE: 삭제 쿼리
     * - OTHER: 기타 (DDL, DCL 등)
     *
     * [용도]
     * - 쿼리 타입별 성능 분석
     * - DML/DQL 비율 분석
     * - 부하 패턴 파악
     *
     * @param queryType 쿼리 타입
     * @return 해당 타입의 쿼리 메트릭스 목록
     *  ----------  ------  --------------------------------------------------
     *  작업일자      작성자    Description
     *  ----------  ------  --------------------------------------------------
     *  2025-11-10  이해든    1. 최초작성자
     */
    @GetMapping("/type/{queryType}")
    @Operation(
            summary = "쿼리 타입별 조회",
            description = "특정 타입(SELECT, INSERT, UPDATE, DELETE)의 쿼리를 조회합니다"
    )
    public ResponseEntity<Map<String, Object>> getQueryMetricsByType(
            @Parameter(description = "쿼리 타입 (예: SELECT, INSERT, UPDATE, DELETE)")
            @PathVariable String queryType) {

        log.info("GET /api/query-metrics/type/{} - queryType: {}", queryType, queryType);

        // ----------------------------------------
        // 1. 타입별 데이터 조회
        // ----------------------------------------

        List<QueryMetricsRawDto> data = queryMetricsRawService.getQueryMetricsByType(queryType);

        // ----------------------------------------
        // 2. 응답 생성
        // ----------------------------------------

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        response.put("count", data.size());
        response.put("message", "조회 성공");

        return ResponseEntity.ok(response);
    }

    // ============================================
    // API 엔드포인트 - 슬로우 쿼리
    // ============================================

    /**
     * 슬로우 쿼리 조회
     *
     * [슬로우 쿼리 판단 기준]
     * - 실행시간 >= thresholdMs
     * - 기본값: 1000ms (1초)
     *
     * [용도]
     * - 성능 문제 쿼리 식별
     * - 튜닝 대상 쿼리 선정
     * - 슬로우 쿼리 추이 분석
     *
     * [정렬]
     * - 실행시간 내림차순
     *
     * @param thresholdMs 임계값 (밀리초, 기본 1000)
     * @return 슬로우 쿼리 목록
     *
     */
    @GetMapping("/slow")
    @Operation(
            summary = "슬로우 쿼리 조회",
            description = "지정된 임계값을 초과하는 느린 쿼리를 조회합니다"
    )
    public ResponseEntity<Map<String, Object>> getSlowQueries(
            @Parameter(description = "임계값 (밀리초, 기본값: 1000)")
            @RequestParam(defaultValue = "1000") Double thresholdMs) {

        log.info("GET /api/query-metrics/slow - thresholdMs: {}", thresholdMs);

        // ----------------------------------------
        // 1. 슬로우 쿼리 조회
        // ----------------------------------------

        List<QueryMetricsRawDto> data = queryMetricsRawService.getSlowQueries(thresholdMs);

        // ----------------------------------------
        // 2. 응답 생성
        // ----------------------------------------

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        response.put("count", data.size());
        response.put("thresholdMs", thresholdMs);
        response.put("message", "조회 성공");

        log.info("슬로우 쿼리 조회 완료: thresholdMs={}, count={}", thresholdMs, data.size());
        return ResponseEntity.ok(response);
    }

    // ============================================
    // API 엔드포인트 - 리소스 사용량 Top N
    // ============================================

    /**
     * CPU 사용량 상위 N개 조회
     *
     * [용도]
     * - CPU 집약적 쿼리 식별
     * - 시스템 부하 분석
     * - 쿼리 최적화 우선순위 결정
     *
     * [정렬]
     * - CPU 사용률 내림차순
     *
     * @param limit 조회 개수 (기본 10개)
     * @return CPU 사용량 상위 쿼리 목록
     */
    @GetMapping("/top/cpu")
    @Operation(
            summary = "CPU 사용량 상위 쿼리 조회",
            description = "CPU 사용량이 높은 상위 N개의 쿼리를 조회합니다"
    )
    public ResponseEntity<Map<String, Object>> getTopByCpuUsage(
            @Parameter(description = "조회할 개수 (기본값: 10)")
            @RequestParam(defaultValue = "10") Integer limit) {

        log.info("GET /api/query-metrics/top/cpu - limit: {}", limit);

        // ----------------------------------------
        // 1. CPU 상위 쿼리 조회
        // ----------------------------------------

        List<QueryMetricsRawDto> data = queryMetricsRawService.getTopByCpuUsage(limit);

        // ----------------------------------------
        // 2. 응답 생성
        // ----------------------------------------

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
     *
     * [용도]
     * - 메모리 집약적 쿼리 식별
     * - OOM(Out of Memory) 위험 파악
     * - 대용량 데이터 처리 쿼리 분석
     *
     * [정렬]
     * - 메모리 사용량(MB) 내림차순
     *
     * @param limit 조회 개수 (기본 10개)
     * @return 메모리 사용량 상위 쿼리 목록
     */
    @GetMapping("/top/memory")
    @Operation(
            summary = "메모리 사용량 상위 쿼리 조회",
            description = "메모리 사용량이 높은 상위 N개의 쿼리를 조회합니다"
    )
    public ResponseEntity<Map<String, Object>> getTopByMemoryUsage(
            @Parameter(description = "조회할 개수 (기본값: 10)")
            @RequestParam(defaultValue = "10") Integer limit) {

        log.info("GET /api/query-metrics/top/memory - limit: {}", limit);

        // ----------------------------------------
        // 1. 메모리 상위 쿼리 조회
        // ----------------------------------------

        List<QueryMetricsRawDto> data = queryMetricsRawService.getTopByMemoryUsage(limit);

        // ----------------------------------------
        // 2. 응답 생성
        // ----------------------------------------

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        response.put("count", data.size());
        response.put("limit", limit);
        response.put("message", "조회 성공");

        return ResponseEntity.ok(response);
    }

    // ============================================
    // API 엔드포인트 - 통계 데이터
    // ============================================

    /**
     * 전체 쿼리 메트릭스 개수 조회
     *
     * [용도]
     * - 전체 데이터 규모 파악
     * - 스토리지 관리
     * - 데이터 정리 계획
     *
     * @return 전체 레코드 개수
     */
    @GetMapping("/count")
    @Operation(
            summary = "전체 쿼리 메트릭스 개수 조회",
            description = "저장된 전체 쿼리 메트릭스 개수를 반환합니다"
    )
    public ResponseEntity<Map<String, Object>> getTotalCount() {
        log.info("GET /api/query-metrics/count");

        // ----------------------------------------
        // 1. 개수 조회
        // ----------------------------------------

        int count = queryMetricsRawService.getTotalCount();

        // ----------------------------------------
        // 2. 응답 생성
        // ----------------------------------------

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("totalCount", count);
        response.put("message", "조회 성공");

        return ResponseEntity.ok(response);
    }

    /**
     * ExecutionStatus용 쿼리별 집계 통계 조회
     *
     * [집계 방식]
     * - query_hash(MD5) 기준 그룹화
     * - 최근 N시간 데이터 집계
     *
     * [집계 항목]
     * - executionCount: 실행 횟수
     * - avgTimeMs: 평균 실행시간
     * - totalTimeMs: 총 실행시간
     * - callCount: 호출 횟수
     * - lastExecutedAt: 마지막 실행 시각
     *
     * [용도]
     * - ExecutionStatus 페이지 메인 테이블
     * - 쿼리별 성능 추이 분석
     * - 문제 쿼리 패턴 파악
     *
     * @param databaseId 데이터베이스 ID
     * @param hours 조회 기간 (시간 단위)
     * @return 쿼리별 집계 통계
     */
    @GetMapping("/execution-stats")
    @Operation(
            summary = "실행 통계 집계",
            description = "ExecutionStatus 페이지용 쿼리별 집계 데이터 (쿼리문별 실행횟수, 평균시간, 총시간)"
    )
    public ResponseEntity<Map<String, Object>> getExecutionStats(
            @Parameter(description = "데이터베이스 ID", required = true)
            @RequestParam Long databaseId,
            @Parameter(description = "조회 기간 (시간 단위, 기본값: 1시간)")
            @RequestParam(defaultValue = "1") Integer hours) {

        log.info("GET /api/query-metrics/execution-stats - databaseId: {}, hours: {}", databaseId, hours);

        // ----------------------------------------
        // 1. 집계 데이터 조회
        // ----------------------------------------

        List<Map<String, Object>> data = queryMetricsRawService.getExecutionStats(databaseId, hours);

        // ----------------------------------------
        // 2. 응답 생성
        // ----------------------------------------

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        response.put("count", data.size());
        response.put("databaseId", databaseId);
        response.put("hours", hours);
        response.put("message", "조회 성공");

        log.info("쿼리별 집계 조회 완료: databaseId={}, hours={}, count={}", databaseId, hours, data.size());
        return ResponseEntity.ok(response);
    }

    /**
     * 시간대별 쿼리 수 분포 조회
     *
     * [집계 방식]
     * - 1시간 단위 그룹화
     * - 최근 N시간 데이터
     *
     * [용도]
     * - 시간대별 부하 패턴 분석
     * - 피크 타임 파악
     * - 용량 계획
     *
     * [응답 형식]
     * [
     *   { "timeSlot": "09:00", "queryCount": 1234 },
     *   { "timeSlot": "10:00", "queryCount": 2345 },
     *   ...
     * ]
     *
     * @param databaseId 데이터베이스 ID
     * @param hours 조회 기간 (시간 단위)
     * @return 시간대별 쿼리 수
     */
    @GetMapping("/hourly-distribution")
    @Operation(
            summary = "시간대별 쿼리 수 분포",
            description = "시간대별 쿼리 수를 집계합니다 (기본: 최근 5시간)"
    )
    public ResponseEntity<Map<String, Object>> getHourlyDistribution(
            @Parameter(description = "데이터베이스 ID", required = true)
            @RequestParam Long databaseId,
            @Parameter(description = "조회 기간 (시간 단위, 기본값: 5시간)")
            @RequestParam(defaultValue = "5") Integer hours) {

        log.info("GET /api/query-metrics/hourly-distribution - databaseId: {}, hours: {}", databaseId, hours);

        // ----------------------------------------
        // 1. 시간대별 분포 조회
        // ----------------------------------------

        List<Map<String, Object>> data = queryMetricsRawService.getHourlyDistribution(databaseId, hours);

        // ----------------------------------------
        // 2. 응답 생성
        // ----------------------------------------

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        response.put("count", data.size());
        response.put("hours", hours);
        response.put("message", "조회 성공");

        log.info("시간대별 분포 조회 완료: databaseId={}, hours={}, count={}", databaseId, hours, data.size());
        return ResponseEntity.ok(response);
    }
}