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

        // ✅ EXPLAIN이 이미 붙어있으면 제거
        String cleanedQuery = query.trim();
        String upperQuery = cleanedQuery.toUpperCase();

        if (upperQuery.startsWith("EXPLAIN")) {
            log.warn("  ⚠️ 쿼리에 이미 EXPLAIN이 포함되어 있습니다. 제거합니다.");
            log.warn("  원본 쿼리 미리보기: {}", cleanedQuery.substring(0, Math.min(100, cleanedQuery.length())));

            // EXPLAIN (...) 부분 제거
            // 1. "EXPLAIN (ANALYZE TRUE, BUFFERS TRUE, FORMAT TEXT) SELECT ..." 형태
            // 2. "EXPLAIN SELECT ..." 형태

            // 괄호가 있는 경우: EXPLAIN (...) 찾기
            int parenStart = cleanedQuery.indexOf('(');
            int parenEnd = -1;

            if (parenStart > 0 && parenStart < 20) {  // EXPLAIN 바로 뒤에 괄호가 있는 경우
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
                    // EXPLAIN (...) 이후부터 추출
                    cleanedQuery = cleanedQuery.substring(parenEnd + 1).trim();
                }
            } else {
                // 괄호가 없는 경우: "EXPLAIN " 이후부터
                cleanedQuery = cleanedQuery.substring(7).trim();  // "EXPLAIN" = 7글자
            }

            log.info("  ✅ 정제된 쿼리: {}", cleanedQuery.substring(0, Math.min(80, cleanedQuery.length())));
        }

        query = cleanedQuery; // 정제된 쿼리로 교체

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
        upperQuery = query.toUpperCase();  // 정제된 쿼리로 다시 판별
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

        // ⭐ 메모리/IO 정보 파싱
        Double memoryUsageMb = parseMemoryUsage(explainPlan);
        Integer ioBlocks = parseIoBlocks(explainPlan);
        Double cpuUsagePercent = parseCpuUsage(explainPlan, executionTimeMs);

        log.info("  ✅ EXPLAIN ANALYZE 실행 완료");
        if (executionTimeMs != null) {
            log.info("  ⏱️  Execution Time: {}ms", executionTimeMs);
        }
        if (planningTimeMs != null) {
            log.info("  ⏱️  Planning Time: {}ms", planningTimeMs);
        }
        if (memoryUsageMb != null) {
            log.info("  💾 Memory Usage: {}MB", String.format("%.2f", memoryUsageMb));
        }
        if (ioBlocks != null) {
            log.info("  💿 I/O Blocks: {}", ioBlocks);
        }

        return ExplainAnalyzeResult.builder()
                .executionMode("실제 실행")
                .explainPlan(explainPlan)
                .executionTimeMs(executionTimeMs)
                .planningTimeMs(planningTimeMs)
                .memoryUsageMb(memoryUsageMb)        // ⭐ 추가
                .ioBlocks(ioBlocks)                  // ⭐ 추가
                .cpuUsagePercent(cpuUsagePercent)    // ⭐ 추가
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
            resultBuilder.append("────────────────────────────────────────────────\n\n");

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
                    .memoryUsageMb(null)      // ⭐ 추정치에서는 null
                    .ioBlocks(null)            // ⭐ 추정치에서는 null
                    .cpuUsagePercent(null)     // ⭐ 추정치에서는 null
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
     * ⭐ 메모리 사용량 파싱 (MB)
     * "Buffers: shared hit=12345 read=678" -> 메모리 사용량 계산
     * PostgreSQL에서 1 block = 8KB
     */
    private Double parseMemoryUsage(String explainPlan) {
        try {
            // "Buffers: shared hit=X read=Y written=Z" 패턴 찾기
            Pattern hitPattern = Pattern.compile("shared hit=(\\d+)");
            Pattern readPattern = Pattern.compile("shared.*?read=(\\d+)");
            Pattern writtenPattern = Pattern.compile("shared.*?written=(\\d+)");

            int totalBlocks = 0;

            // shared hit 파싱
            Matcher hitMatcher = hitPattern.matcher(explainPlan);
            while (hitMatcher.find()) {
                totalBlocks += Integer.parseInt(hitMatcher.group(1));
            }

            // shared read 파싱
            Matcher readMatcher = readPattern.matcher(explainPlan);
            while (readMatcher.find()) {
                totalBlocks += Integer.parseInt(readMatcher.group(1));
            }

            // shared written 파싱
            Matcher writtenMatcher = writtenPattern.matcher(explainPlan);
            while (writtenMatcher.find()) {
                totalBlocks += Integer.parseInt(writtenMatcher.group(1));
            }

            if (totalBlocks > 0) {
                // 1 block = 8KB, MB로 변환
                return (totalBlocks * 8.0) / 1024.0;
            }
        } catch (Exception e) {
            log.warn("메모리 사용량 파싱 실패: {}", e.getMessage());
        }
        return null;
    }

    /**
     * ⭐ I/O 블록 수 파싱 (개선 버전)
     * Buffers: shared hit=12345 read=678 written=90 형식을 모두 파싱
     */
    private Integer parseIoBlocks(String explainPlan) {
        if (explainPlan == null || explainPlan.isEmpty()) {
            return null;
        }

        try {
            int totalBlocks = 0;

            // "Buffers:"로 시작하는 모든 라인 찾기
            Pattern bufferLinePattern = Pattern.compile("Buffers:.*");
            Matcher bufferLineMatcher = bufferLinePattern.matcher(explainPlan);

            while (bufferLineMatcher.find()) {
                String bufferLine = bufferLineMatcher.group();
                log.debug("Buffers 라인 발견: {}", bufferLine);

                // shared hit, read, written 각각 파싱
                totalBlocks += extractBufferValue(bufferLine, "hit");
                totalBlocks += extractBufferValue(bufferLine, "read");
                totalBlocks += extractBufferValue(bufferLine, "written");
                totalBlocks += extractBufferValue(bufferLine, "dirtied");
                totalBlocks += extractBufferValue(bufferLine, "shared written");
                totalBlocks += extractBufferValue(bufferLine, "local hit");
                totalBlocks += extractBufferValue(bufferLine, "local read");
                totalBlocks += extractBufferValue(bufferLine, "local written");
                totalBlocks += extractBufferValue(bufferLine, "temp read");
                totalBlocks += extractBufferValue(bufferLine, "temp written");
            }

            if (totalBlocks > 0) {
                log.info("  💿 총 I/O 블록 수: {}", totalBlocks);
                return totalBlocks;
            }

            log.warn("  ⚠️ I/O 블록 정보를 찾을 수 없음");
            return 0;  // null 대신 0 반환

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
            // \b는 단어 경계 - "hit"만 매칭하고 "dirtied"는 안 매칭
            String regex = "\\b" + keyword + "\\s*=\\s*(\\d+)";
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
     * ⭐ CPU 사용률 추정 (실행 시간 기반)
     */
    private Double parseCpuUsage(String explainPlan, Double executionTimeMs) {
        if (executionTimeMs == null || executionTimeMs == 0) {
            return null;
        }

        // 실행 시간이 길수록 CPU 사용률이 높다고 가정
        // 100ms = 10%, 1000ms = 50%, 5000ms = 90%
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
            resultBuilder.append("📊 시스템 뷰 쿼리 실행 결과\n\n");
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
                    .memoryUsageMb(null)       // ⭐ 시스템 쿼리는 메모리 정보 없음
                    .ioBlocks(null)             // ⭐ 시스템 쿼리는 IO 정보 없음
                    .cpuUsagePercent(null)      // ⭐ 시스템 쿼리는 CPU 정보 없음
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
                        "📝 요청된 쿼리\n\n" +
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
                .memoryUsageMb(null)       // ⭐ 에러 시에는 null
                .ioBlocks(null)             // ⭐ 에러 시에는 null
                .cpuUsagePercent(null)      // ⭐ 에러 시에는 null
                .build();
    }
}