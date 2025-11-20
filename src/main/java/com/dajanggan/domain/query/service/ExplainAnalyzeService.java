package com.dajanggan.domain.query.service;

import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.repository.DatabaseRepository;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.domain.query.dto.ExplainAnalyzeResult;
import com.dajanggan.infrastructure.datasource.DataSourceFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EXPLAIN ANALYZE 실행 Service
 * - PostgreSQL EXPLAIN ANALYZE 실행
 * - 실패 시 자동으로 EXPLAIN만 실행 (fallback)
 *
 * @author 이해든
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExplainAnalyzeService {

    private final DatabaseRepository databaseRepository;
    private final InstanceRepository instanceRepository;
    private final DataSourceFactory dataSourceFactory;

    /**
     * EXPLAIN ANALYZE 실행
     * - 일단 EXPLAIN ANALYZE 시도
     * - 실패하면 EXPLAIN만 시도
     * - EXPLAIN도 실패하면 시스템 쿼리는 실제 실행 결과 표시
     *
     * @param databaseId 데이터베이스 ID
     * @param query 분석할 쿼리
     * @return EXPLAIN ANALYZE 결과
     */
    public ExplainAnalyzeResult execute(Long databaseId, String query) {
        log.info("🔍 쿼리 분석 시작");
        log.info("  - Database ID: {}", databaseId);

        // 1. Database 정보 조회
        Database database = databaseRepository.findById(databaseId);
        if (database == null) {
            throw new IllegalArgumentException("Database not found: " + databaseId);
        }

        log.info("  - Database Name: {}", database.getDatabaseName());

        // 2. Instance 정보 조회
        Instance instance = instanceRepository.findById(database.getInstanceId())
                .orElseThrow(() -> new IllegalArgumentException("Instance not found: " + database.getInstanceId()));

        log.info("  - Instance: {}:{}", instance.getHost(), instance.getPort());

        // 3. JdbcTemplate 생성
        JdbcTemplate jdbcTemplate = dataSourceFactory.createJdbcTemplate(
                instance,
                database.getDatabaseName()
        );

        log.info("  🔗 데이터베이스 연결 성공");

        // 4. 시스템 뷰 쿼리 판별
        String upperQuery = query.toUpperCase();
        boolean isSystemViewQuery = query.toLowerCase().contains("pg_stat_") ||
                query.toLowerCase().contains("pg_class") ||
                query.toLowerCase().contains("pg_locks") ||
                upperQuery.startsWith("SELECT") &&
                        (query.contains("buffers_") || query.contains("checkpoint_"));

        // 5. EXPLAIN ANALYZE 시도 → 실패하면 EXPLAIN만 실행
        try {
            log.info("  ✅ EXPLAIN ANALYZE 시도");
            return executeExplainAnalyze(jdbcTemplate, query.trim());
        } catch (Exception e) {
            log.warn("  ⚠️ EXPLAIN ANALYZE 실패, EXPLAIN만 시도: {}", e.getMessage());

            try {
                return executeExplainOnly(jdbcTemplate, query.trim());
            } catch (Exception explainError) {
                log.error("  ❌ EXPLAIN도 실패: {}", explainError.getMessage());

                // 시스템 뷰 쿼리면 실제 실행해서 결과 보여주기
                if (isSystemViewQuery && upperQuery.startsWith("SELECT")) {
                    log.info("  💡 시스템 뷰 쿼리 감지 - 실제 실행 결과 표시");
                    return executeAndShowResults(jdbcTemplate, query.trim());
                }

                // 그 외에는 에러 메시지
                return buildErrorResult(query.trim(), explainError);
            }
        }
    }

    /**
     * ✅ EXPLAIN ANALYZE 실행 (실제 실행)
     */
    private ExplainAnalyzeResult executeExplainAnalyze(JdbcTemplate jdbcTemplate, String query) {
        String explainQuery = "EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) " + query;

        List<String> resultLines = jdbcTemplate.queryForList(explainQuery, String.class);

        StringBuilder resultBuilder = new StringBuilder();
        Double executionTimeMs = null;
        Double planningTimeMs = null;

        for (String line : resultLines) {
            resultBuilder.append(line).append("\n");

            // Execution Time 파싱
            if (line.contains("Execution Time:")) {
                executionTimeMs = parseTime(line);
            }
            // Planning Time 파싱
            if (line.contains("Planning Time:")) {
                planningTimeMs = parseTime(line);
            }
        }

        String explainPlan = resultBuilder.toString();

        log.info("  ✅ EXPLAIN ANALYZE 실행 완료");
        if (executionTimeMs != null) {
            log.info("  ⏱️  Execution Time: {}ms", executionTimeMs);
        }
        if (planningTimeMs != null) {
            log.info("  ⏱️  Planning Time: {}ms", planningTimeMs);
        }

        return ExplainAnalyzeResult.builder()
                .executionMode("실제 실행")
                .explainPlan(explainPlan)
                .executionTimeMs(executionTimeMs)
                .planningTimeMs(planningTimeMs)
                .build();
    }

    /**
     * ✅ EXPLAIN만 실행 (추정치)
     */
    private ExplainAnalyzeResult executeExplainOnly(JdbcTemplate jdbcTemplate, String query) {
        try {
            String explainQuery = "EXPLAIN (FORMAT TEXT) " + query;

            List<String> resultLines = jdbcTemplate.queryForList(explainQuery, String.class);

            StringBuilder resultBuilder = new StringBuilder();
            resultBuilder.append("ℹ️  추정치 (실제 실행 없음)\n\n");
            resultBuilder.append("이 쿼리는 실제로 실행하지 않고 추정한 실행 계획입니다.\n");
            resultBuilder.append("(DML 쿼리, 파라미터 포함 쿼리, 복잡한 시스템 쿼리 등)\n\n");
            resultBuilder.append("──────────────────────────────────────────────────\n\n");

            for (String line : resultLines) {
                resultBuilder.append(line).append("\n");
            }

            String explainPlan = resultBuilder.toString();

            log.info("  ✅ EXPLAIN (추정치) 실행 완료");

            return ExplainAnalyzeResult.builder()
                    .executionMode("추정치 (실행 불가)")
                    .explainPlan(explainPlan)
                    .executionTimeMs(null)
                    .planningTimeMs(null)
                    .build();

        } catch (Exception e) {
            // EXPLAIN도 실패 - buildErrorResult로 처리 위임
            throw e;  // 상위에서 처리하도록 다시 던짐
        }
    }

    /**
     * 실행 시간 파싱
     * "Execution Time: 234.567 ms" -> 234.567
     */
    private Double parseTime(String line) {
        try {
            Pattern pattern = Pattern.compile("([\\d.]+)\\s*ms");
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return Double.parseDouble(matcher.group(1));
            }
        } catch (Exception e) {
            log.warn("시간 파싱 실패: {}", line);
        }
        return null;
    }

    /**
     * 시스템 뷰 쿼리 실제 실행 후 결과 표시
     */
    private ExplainAnalyzeResult executeAndShowResults(JdbcTemplate jdbcTemplate, String query) {
        try {
            long startTime = System.currentTimeMillis();

            // 실제 쿼리 실행 (최대 10개 행만)
            String limitedQuery = query;
            if (!query.toUpperCase().contains("LIMIT")) {
                limitedQuery = query + " LIMIT 10";
            }

            List<java.util.Map<String, Object>> results = jdbcTemplate.queryForList(limitedQuery);

            long executionTime = System.currentTimeMillis() - startTime;

            StringBuilder resultBuilder = new StringBuilder();
            resultBuilder.append("📊 시스템 통계 쿼리 - 실제 실행 결과\n\n");
            resultBuilder.append("이 쿼리는 시스템 통계 뷰를 조회하므로 EXPLAIN ANALYZE가 필요 없습니다.\n");
            resultBuilder.append("대신 실제 실행 결과를 보여드립니다.\n\n");
            resultBuilder.append("──────────────────────────────────────────────────\n\n");
            resultBuilder.append(String.format("실행 시간: %dms\n", executionTime));
            resultBuilder.append(String.format("반환된 행 수: %d행\n\n", results.size()));

            if (results.isEmpty()) {
                resultBuilder.append("조회 결과가 없습니다.\n");
            } else {
                // 컬럼명 표시
                java.util.Map<String, Object> firstRow = results.get(0);
                for (String columnName : firstRow.keySet()) {
                    resultBuilder.append(String.format("%-30s", columnName));
                }
                resultBuilder.append("\n");
                resultBuilder.append("─".repeat(30 * firstRow.size())).append("\n");

                // 데이터 표시
                for (java.util.Map<String, Object> row : results) {
                    for (Object value : row.values()) {
                        String valueStr = value != null ? value.toString() : "NULL";
                        if (valueStr.length() > 28) {
                            valueStr = valueStr.substring(0, 25) + "...";
                        }
                        resultBuilder.append(String.format("%-30s", valueStr));
                    }
                    resultBuilder.append("\n");
                }

                if (results.size() >= 10) {
                    resultBuilder.append("\n... (최대 10개 행만 표시)\n");
                }
            }

            log.info("  ✅ 시스템 쿼리 실행 완료: {}ms, {}행", executionTime, results.size());

            return ExplainAnalyzeResult.builder()
                    .executionMode("시스템 통계 조회")
                    .explainPlan(resultBuilder.toString())
                    .executionTimeMs((double) executionTime)
                    .planningTimeMs(null)
                    .build();

        } catch (Exception e) {
            log.error("  ❌ 시스템 쿼리 실행 실패: {}", e.getMessage());
            return buildErrorResult(query, e);
        }
    }

    /**
     * 에러 결과 생성
     */
    private ExplainAnalyzeResult buildErrorResult(String query, Exception e) {
        String errorMessage = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        String reason;
        String solution;

        // 에러 메시지에서 원인 파악
        if (errorMessage.contains("$") || query.contains("$1") || query.contains("$2")) {
            reason = "파라미터 플레이스홀더 ($1, $2 등)를 포함하고 있어서";
            solution = "• 애플리케이션 로그에서 실제 실행된 쿼리를 찾아주세요\n" +
                    "• PostgreSQL 로그 설정(log_statement = 'all')을 활성화하세요\n" +
                    "• pgAdmin 등의 도구에서 실제 값을 넣어 직접 실행해보세요";
        } else if (query.toLowerCase().contains("pg_stat_") ||
                query.toLowerCase().contains("pg_class") ||
                query.toLowerCase().contains("pg_locks")) {
            reason = "시스템 카탈로그 뷰를 참조하고 있어서";
            solution = "• 이런 시스템 쿼리는 일반적으로 분석이 어렵습니다\n" +
                    "• 실제 데이터베이스에서 직접 실행하여 확인하세요\n" +
                    "• 모니터링 목적이라면 실행 결과를 직접 확인하는 것이 더 정확합니다";
        } else if (query.toUpperCase().contains("WITH ")) {
            reason = "복잡한 CTE(WITH 절)를 사용하고 있어서";
            solution = "• CTE를 단순한 서브쿼리로 변경해보세요\n" +
                    "• 각 CTE를 개별적으로 분석해보세요\n" +
                    "• 쿼리를 단순화하면 분석이 가능할 수 있습니다";
        } else {
            reason = "쿼리가 너무 복잡하거나 특수한 구문을 사용하고 있어서";
            solution = "• 쿼리를 단순화해보세요\n" +
                    "• PostgreSQL에 직접 접속하여 실행해보세요";
        }

        String failureMessage = String.format(
                "⚠️  이 쿼리는 분석할 수 없습니다\n\n" +
                        "원인:\n" +
                        "%s 실행 계획을 생성할 수 없습니다.\n\n" +
                        "💡 해결 방법\n\n" +
                        "%s\n\n" +
                        "🔍 요청된 쿼리\n\n" +
                        "%s\n\n" +
                        "🔧 기술 상세\n\n" +
                        "오류: %s",
                reason, solution, query, e.getMessage()
        );

        return ExplainAnalyzeResult.builder()
                .executionMode("분석 불가")
                .explainPlan(failureMessage)
                .executionTimeMs(null)
                .planningTimeMs(null)
                .build();
    }
}