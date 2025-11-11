package com.dajanggan.domain.query.controller;

import com.dajanggan.domain.query.dto.ExplainAnalyzeRequest;
import com.dajanggan.domain.query.dto.ExplainAnalyzeResult;
import com.dajanggan.domain.query.service.ExplainAnalyzeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * EXPLAIN ANALYZE 실행 Controller
 * - 사용자가 입력한 쿼리에 대해 EXPLAIN ANALYZE 실행
 * - SELECT: EXPLAIN ANALYZE (실제 실행)
 * - UPDATE/INSERT/DELETE: EXPLAIN만 (안전 모드)
 *
 * @author 이해든
 */
@Slf4j
@RestController
@RequestMapping("/api/query-metrics")
@RequiredArgsConstructor
@Tag(name = "EXPLAIN ANALYZE", description = "쿼리 실행 계획 분석 API")
public class ExplainAnalyzeController {

    private final ExplainAnalyzeService explainAnalyzeService;

    /**
     * EXPLAIN ANALYZE 실행
     * POST /api/query-metrics/explain-analyze
     *
     * @param request { databaseId, query }
     * @return EXPLAIN ANALYZE 결과
     */
    @PostMapping("/explain-analyze")
    @Operation(summary = "EXPLAIN ANALYZE 실행",
            description = "입력한 쿼리에 대해 EXPLAIN ANALYZE를 실행합니다. SELECT는 실제 실행, DML은 안전 모드로 실행됩니다.")
    public ResponseEntity<Map<String, Object>> executeExplainAnalyze(
            @RequestBody ExplainAnalyzeRequest request) {

        log.info("==========================================");
        log.info("📊 EXPLAIN ANALYZE 요청");
        log.info("  - Database ID: {}", request.getDatabaseId());
        log.info("  - Query Length: {} chars", request.getQuery().length());
        log.info("==========================================");

        Map<String, Object> response = new HashMap<>();

        try {
            // 입력 검증
            if (request.getDatabaseId() == null) {
                response.put("success", false);
                response.put("message", "databaseId는 필수입니다");
                return ResponseEntity.badRequest().body(response);
            }

            if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "query는 필수입니다");
                return ResponseEntity.badRequest().body(response);
            }

            // EXPLAIN ANALYZE 실행
            ExplainAnalyzeResult result = explainAnalyzeService.execute(
                    request.getDatabaseId(),
                    request.getQuery()
            );

            response.put("success", true);
            response.put("data", result);

            log.info("✅ EXPLAIN ANALYZE 실행 성공");
            log.info("  - Execution Mode: {}", result.getExecutionMode());
            log.info("==========================================");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ EXPLAIN ANALYZE 실행 실패", e);
            log.error("==========================================");

            response.put("success", false);
            response.put("message", "쿼리 분석 실패: " + e.getMessage());

            return ResponseEntity.ok(response);
        }
    }
}