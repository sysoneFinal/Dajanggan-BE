package com.dajanggan.domain.query.service;

import javax.sql.DataSource;
import java.sql.Connection;
import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.repository.DatabaseRepository;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.domain.query.dto.ExplainAnalyzeResult;
import com.dajanggan.infrastructure.datasource.DataSourceFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

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
     * 1. EXPLAIN ANALYZE 시도
     * 2. 실패하면 EXPLAIN만 시도
     * 3. EXPLAIN도 실패하면 시스템 쿼리는 실제 실행 결과 표시
     *
     * @param databaseId 데이터베이스 ID
     * @param query 분석할 쿼리
     * @return EXPLAIN ANALYZE 결과
     */
    public ExplainAnalyzeResult execute(Long databaseId, String query) {
        log.info("쿼리 분석 시작");
        log.info("  - Database ID: {}", databaseId);

        String cleanedQuery = query.trim();
        String upperQuery = cleanedQuery.toUpperCase();

        if (upperQuery.startsWith("EXPLAIN")) {
            log.warn("  쿼리에 이미 EXPLAIN이 포함되어 있습니다. 제거합니다.");
            log.warn("  원본 쿼리 미리보기: {}", cleanedQuery.substring(0, Math.min(100, cleanedQuery.length())));

            int parenStart = cleanedQuery.indexOf('(');
            int parenEnd = -1;

            if (parenStart > 0 && parenStart < 20) {
                int depth = 0;
                for (int i = parenStart; i < cleanedQuery.length(); i++) {
                    if (cleanedQuery.charAt(i) == '(') depth++;
                    else if (cleanedQuery.charAt(i) == ')') {
                        depth--;
                        if (depth == 0) {
                            parenEnd = i;
                            break;
                        }
                    }
                }

                if (parenEnd > parenStart) {
                    cleanedQuery = cleanedQuery.substring(parenEnd + 1).trim();
                }
            } else {
                cleanedQuery = cleanedQuery.substring(7).trim();
            }

            log.info("  정제된 쿼리: {}", cleanedQuery.substring(0, Math.min(80, cleanedQuery.length())));
        }

        query = cleanedQuery;

        Database database = databaseRepository.findById(databaseId);
        if (database == null) {
            throw new IllegalArgumentException("Database not found: " + databaseId);
        }

        log.info("  - Database Name: {}", database.getDatabaseName());

        Instance instance = instanceRepository.findById(database.getInstanceId())
                .orElseThrow(() -> new IllegalArgumentException("Instance not found: " + database.getInstanceId()));

        log.info("  - Instance: {}:{}", instance.getHost(), instance.getPort());

        JdbcTemplate jdbcTemplate = dataSourceFactory.createJdbcTemplate(
                instance,
                database.getDatabaseName()
        );

        log.info("  데이터베이스 연결 성공");

        upperQuery = query.toUpperCase();
        boolean isSystemViewQuery = query.toLowerCase().contains("pg_stat_") ||
                query.toLowerCase().contains("pg_class") ||
                query.toLowerCase().contains("pg_locks") ||
                upperQuery.startsWith("SELECT") &&
                        (query.contains("buffers_") || query.contains("checkpoint_"));

        String lowerQuery = query.toLowerCase();
        boolean isLargeTable = lowerQuery.contains("query_metrics_raw") ||
                lowerQuery.contains("query_metrics_agg") ||
                lowerQuery.contains("query_agg_1m") ||
                lowerQuery.contains("query_agg_5m");

        if (isLargeTable) {
            log.info("  대용량 테이블 감지 - EXPLAIN만 실행 (ANALYZE 생략)");
            try {
                return executeExplainOnly(jdbcTemplate, query.trim());
            } catch (Exception explainError) {
                log.error("  EXPLAIN 실패: {}", explainError.getMessage());
                return buildErrorResult(query.trim(), explainError);
            }
        }

        try {
            log.info("  EXPLAIN ANALYZE 시도");
            return executeExplainAnalyze(jdbcTemplate, query.trim());
        } catch (Exception e) {
            log.warn("  EXPLAIN ANALYZE 실패, EXPLAIN만 시도: {}", e.getMessage());

            try {
                return executeExplainOnly(jdbcTemplate, query.trim());
            } catch (Exception explainError) {
                log.error("  EXPLAIN도 실패: {}", explainError.getMessage());

                if (isSystemViewQuery && upperQuery.startsWith("SELECT")) {
                    log.info("  시스템 뷰 쿼리 감지 - 실제 실행 결과 표시");
                    return executeAndShowResults(jdbcTemplate, query.trim());
                }

                return buildErrorResult(query.trim(), explainError);
            }
        }
    }

    /**
     * EXPLAIN ANALYZE 실행 (실제 실행)
     */
    private ExplainAnalyzeResult executeExplainAnalyze(JdbcTemplate jdbcTemplate, String query) {
        String explainQuery = "EXPLAIN (ANALYZE true, BUFFERS true, FORMAT TEXT) " + query;

        List<String> resultLines = jdbcTemplate.queryForList(explainQuery, String.class);

        StringBuilder resultBuilder = new StringBuilder();
        Double executionTimeMs = null;
        Double planningTimeMs = null;

        for (String line : resultLines) {
            resultBuilder.append(line).append("\n");

            if (line.contains("Execution Time:")) {
                executionTimeMs = parseTime(line);
            }
            if (line.contains("Planning Time:")) {
                planningTimeMs = parseTime(line);
            }
        }

        String explainPlan = resultBuilder.toString();

        Double memoryUsageMb = parseMemoryUsage(explainPlan);
        Integer ioBlocks = parseIoBlocks(explainPlan);
        Double cpuUsagePercent = parseCpuUsage(explainPlan, executionTimeMs);

        log.info("  EXPLAIN ANALYZE 실행 완료");
        if (executionTimeMs != null) {
            log.info("    - Execution Time: {} ms", String.format("%.2f", executionTimeMs));
        }
        if (planningTimeMs != null) {
            log.info("    - Planning Time: {} ms", String.format("%.2f", planningTimeMs));
        }
        if (ioBlocks != null && ioBlocks > 0) {
            log.info("    - I/O Blocks: {}", ioBlocks);
        }

        return ExplainAnalyzeResult.builder()
                .executionMode("EXPLAIN ANALYZE")
                .explainPlan(explainPlan)
                .executionTimeMs(executionTimeMs)
                .planningTimeMs(planningTimeMs)
                .memoryUsageMb(memoryUsageMb)
                .ioBlocks(ioBlocks)
                .cpuUsagePercent(cpuUsagePercent)
                .build();
    }

    /**
     * EXPLAIN만 실행 (추정치만, 실제 실행 X)
     */
    private ExplainAnalyzeResult executeExplainOnly(JdbcTemplate jdbcTemplate, String query) {
        String explainQuery = "EXPLAIN (ANALYZE false, BUFFERS true, COSTS true, FORMAT TEXT) " + query;

        List<String> resultLines = jdbcTemplate.queryForList(explainQuery, String.class);

        StringBuilder resultBuilder = new StringBuilder();
        Double planningTimeMs = null;
        Double estimatedCost = null;
        Double estimatedRows = null;
        Double estimatedWidth = null;

        for (String line : resultLines) {
            resultBuilder.append(line).append("\n");

            if (line.contains("Planning Time:")) {
                planningTimeMs = parseTime(line);
            }

            // 첫 번째 라인에서 추정치 추출 (한 번만)
            if (estimatedCost == null && line.contains("cost=")) {
                try {
                    // cost=0.00..35.50 rows=10 width=244
                    Pattern costPattern = Pattern.compile("cost=(\\d+\\.\\d+)\\.\\.(\\d+\\.\\d+)");
                    Matcher costMatcher = costPattern.matcher(line);
                    if (costMatcher.find()) {
                        estimatedCost = Double.parseDouble(costMatcher.group(2));
                    }

                    Pattern rowsPattern = Pattern.compile("rows=(\\d+)");
                    Matcher rowsMatcher = rowsPattern.matcher(line);
                    if (rowsMatcher.find()) {
                        estimatedRows = Double.parseDouble(rowsMatcher.group(1));
                    }

                    Pattern widthPattern = Pattern.compile("width=(\\d+)");
                    Matcher widthMatcher = widthPattern.matcher(line);
                    if (widthMatcher.find()) {
                        estimatedWidth = Double.parseDouble(widthMatcher.group(1));
                    }
                } catch (Exception e) {
                    log.debug("추정치 파싱 실패: {}", e.getMessage());
                }
            }
        }

        String explainPlan = resultBuilder.toString();

        // Buffers에서 I/O 블록 추정 (EXPLAIN에도 Buffers가 포함될 수 있음)
        Integer ioBlocks = parseIoBlocks(explainPlan);

        log.info("  EXPLAIN (ANALYZE 없이) 실행 완료");
        if (planningTimeMs != null) {
            log.info("    - Planning Time: {} ms", String.format("%.2f", planningTimeMs));
        }
        if (estimatedCost != null) {
            log.info("    - Estimated Cost: {}", String.format("%.2f", estimatedCost));
        }
        if (estimatedRows != null) {
            log.info("    - Estimated Rows: {}", estimatedRows.intValue());
        }
        if (ioBlocks != null && ioBlocks > 0) {
            log.info("    - I/O Blocks (estimated): {}", ioBlocks);
        }

        return ExplainAnalyzeResult.builder()
                .executionMode("EXPLAIN (추정치)")
                .explainPlan(explainPlan)
                .executionTimeMs(estimatedCost)     // cost를 실행시간 대신 사용
                .planningTimeMs(planningTimeMs)
                .memoryUsageMb(estimatedWidth != null ? estimatedWidth / 1024.0 : null)
                .ioBlocks(ioBlocks)
                .cpuUsagePercent(estimatedRows != null ? Math.min(100.0, estimatedRows * 0.1) : null)
                .build();
    }

    /**
     * 시간 파싱 (ms 단위)
     */
    private Double parseTime(String line) {
        try {
            Pattern pattern = Pattern.compile("([\\d.]+)\\s*ms");
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return Double.parseDouble(matcher.group(1));
            }
        } catch (Exception e) {
            log.warn("시간 파싱 실패: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 메모리 사용량 파싱
     */
    private Double parseMemoryUsage(String explainPlan) {
        if (explainPlan == null || explainPlan.isEmpty()) {
            return null;
        }

        try {
            Pattern pattern = Pattern.compile("Memory.*?:(.*?)kB", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(explainPlan);

            int totalKb = 0;
            while (matcher.find()) {
                String memoryStr = matcher.group(1).trim();
                try {
                    totalKb += Integer.parseInt(memoryStr);
                } catch (NumberFormatException e) {
                    log.debug("메모리 숫자 파싱 실패: {}", memoryStr);
                }
            }

            if (totalKb > 0) {
                double mb = totalKb / 1024.0;
                log.info("  메모리 사용량: {} MB", String.format("%.2f", mb));
                return mb;
            }

            log.warn("  메모리 정보를 찾을 수 없음");
            return null;

        } catch (Exception e) {
            log.warn("메모리 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * I/O 블록 수 파싱 (개선된 버전)
     */
    private Integer parseIoBlocks(String explainPlan) {
        if (explainPlan == null || explainPlan.isEmpty()) {
            return null;
        }

        try {
            int totalBlocks = 0;

            // Buffers 라인을 모두 찾기
            Pattern bufferLinePattern = Pattern.compile("Buffers:\\s*(.+?)(?=\\n|$)", Pattern.CASE_INSENSITIVE);
            Matcher bufferLineMatcher = bufferLinePattern.matcher(explainPlan);

            while (bufferLineMatcher.find()) {
                String bufferLine = bufferLineMatcher.group(1);
                log.debug("Buffers 라인 발견: {}", bufferLine);

                // 각 버퍼 타입별로 추출
                totalBlocks += extractBufferValue(bufferLine, "shared\\s+hit");
                totalBlocks += extractBufferValue(bufferLine, "shared\\s+read");
                totalBlocks += extractBufferValue(bufferLine, "shared\\s+written");
                totalBlocks += extractBufferValue(bufferLine, "shared\\s+dirtied");
                totalBlocks += extractBufferValue(bufferLine, "local\\s+hit");
                totalBlocks += extractBufferValue(bufferLine, "local\\s+read");
                totalBlocks += extractBufferValue(bufferLine, "local\\s+written");
                totalBlocks += extractBufferValue(bufferLine, "local\\s+dirtied");
                totalBlocks += extractBufferValue(bufferLine, "temp\\s+read");
                totalBlocks += extractBufferValue(bufferLine, "temp\\s+written");

                // 단일 워드 버퍼 (shared 없이 hit, read 등)
                totalBlocks += extractBufferValue(bufferLine, "\\bhit");
                totalBlocks += extractBufferValue(bufferLine, "\\bread");
                totalBlocks += extractBufferValue(bufferLine, "\\bwritten");
                totalBlocks += extractBufferValue(bufferLine, "\\bdirtied");
            }

            if (totalBlocks > 0) {
                log.info("  총 I/O 블록 수: {}", totalBlocks);
                return totalBlocks;
            }

            log.debug("  I/O 블록 정보를 찾을 수 없음");
            return 0;

        } catch (Exception e) {
            log.warn("I/O 블록 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Buffers 라인에서 특정 키워드의 값 추출
     * 예: "shared hit=12345" -> 12345
     */
    private int extractBufferValue(String bufferLine, String keyword) {
        try {
            // keyword 다음에 = 와 숫자가 오는 패턴
            String regex = keyword + "\\s*=\\s*(\\d+)";
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(bufferLine);

            int total = 0;
            while (matcher.find()) {
                int value = Integer.parseInt(matcher.group(1));
                total += value;
                log.debug("    - {}: {}", keyword, value);
            }
            return total;
        } catch (Exception e) {
            log.debug("    - {} 파싱 실패: {}", keyword, e.getMessage());
        }
        return 0;
    }

    /**
     * CPU 사용률 추정 (실행 시간 기반)
     */
    private Double parseCpuUsage(String explainPlan, Double executionTimeMs) {
        if (executionTimeMs == null || executionTimeMs == 0) {
            return null;
        }

        double cpuPercent = Math.min(90.0, (executionTimeMs / 100.0) * 10);
        return cpuPercent;
    }

    /**
     * 시스템 뷰 쿼리 실제 실행 및 결과 표시
     */
    private ExplainAnalyzeResult executeAndShowResults(JdbcTemplate jdbcTemplate, String query) {
        try {
            long startTime = System.currentTimeMillis();

            List<String> results = jdbcTemplate.queryForList(query, String.class);

            long executionTime = System.currentTimeMillis() - startTime;

            StringBuilder resultBuilder = new StringBuilder();
            resultBuilder.append("시스템 뷰 쿼리 실행 결과\n\n");
            resultBuilder.append(String.format("실행 시간: %d ms\n", executionTime));
            resultBuilder.append(String.format("결과 행 수: %d\n\n", results.size()));
            resultBuilder.append("────────────────────────────────────────────────\n\n");

            int displayLimit = 50;
            for (int i = 0; i < Math.min(results.size(), displayLimit); i++) {
                resultBuilder.append(results.get(i)).append("\n");
            }

            if (results.size() > displayLimit) {
                resultBuilder.append(String.format("\n... (%d개 행 더 있음)\n", results.size() - displayLimit));
            }

            return ExplainAnalyzeResult.builder()
                    .executionMode("시스템 통계 조회")
                    .explainPlan(resultBuilder.toString())
                    .executionTimeMs((double) executionTime)
                    .planningTimeMs(null)
                    .memoryUsageMb(null)
                    .ioBlocks(null)
                    .cpuUsagePercent(null)
                    .build();

        } catch (Exception e) {
            log.error("  시스템 쿼리 실행 실패: {}", e.getMessage());
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
                "이 쿼리는 분석할 수 없습니다\n\n" +
                        "원인:\n" +
                        "%s 실행 계획을 생성할 수 없습니다.\n\n" +
                        "해결 방법\n\n" +
                        "%s\n\n" +
                        "요청된 쿼리\n\n" +
                        "%s\n\n" +
                        "기술 상세\n\n" +
                        "오류: %s",
                reason, solution, query, e.getMessage()
        );

        return ExplainAnalyzeResult.builder()
                .executionMode("분석 불가")
                .explainPlan(failureMessage)
                .executionTimeMs(null)
                .planningTimeMs(null)
                .memoryUsageMb(null)
                .ioBlocks(null)
                .cpuUsagePercent(null)
                .build();
    }
}