package com.dajanggan.domain.query.service;

import com.dajanggan.domain.query.dto.ExplainAnalyzeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EXPLAIN ANALYZE 실행 Service
 * - PostgreSQL EXPLAIN ANALYZE 실행
 * - 안전 모드 처리 (DML 쿼리)
 * - PreparedStatement 파라미터 치환 처리
 *
 * @author 백엔드 담당자
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExplainAnalyzeService {

    private final DataSource dataSource;

    /**
     * EXPLAIN ANALYZE 실행
     *
     * @param databaseId 데이터베이스 ID (현재 미사용, 향후 다중 DB 지원 시 활용)
     * @param query 분석할 쿼리
     * @return EXPLAIN ANALYZE 결과
     */
    public ExplainAnalyzeResult execute(Long databaseId, String query) {
        log.info("🔍 쿼리 분석 시작");

        // 🆕 1. PreparedStatement 파라미터 치환
        String processedQuery = replaceParameters(query);

        if (!query.equals(processedQuery)) {
            log.info("  🔄 파라미터 치환 완료");
            log.debug("    - 원본: {}", query.substring(0, Math.min(100, query.length())));
            log.debug("    - 치환: {}", processedQuery.substring(0, Math.min(100, processedQuery.length())));
        }

        // 2. 쿼리 타입 체크
        String trimmedQuery = processedQuery.trim();
        String upperQuery = trimmedQuery.toUpperCase();

        boolean isModifying = upperQuery.startsWith("UPDATE") ||
                upperQuery.startsWith("INSERT") ||
                upperQuery.startsWith("DELETE") ||
                upperQuery.startsWith("CREATE") ||
                upperQuery.startsWith("DROP") ||
                upperQuery.startsWith("ALTER") ||
                upperQuery.startsWith("TRUNCATE");

        String executionMode = isModifying ? "안전 모드" : "실제 실행";

        log.info("  - 실행 모드: {}", executionMode);
        log.info("  - 쿼리 타입: {}", getQueryType(upperQuery));

        // 3. EXPLAIN 쿼리 생성
        String explainQuery;
        if (isModifying) {
            // 안전 모드: EXPLAIN만 실행 (실제 데이터 변경 없음)
            explainQuery = "EXPLAIN (FORMAT TEXT) " + trimmedQuery;
            log.info("  ⚠️ 데이터 변경 쿼리 감지 - 안전 모드로 실행");
        } else {
            // 실제 실행: EXPLAIN ANALYZE (쿼리 실제 실행)
            explainQuery = "EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) " + trimmedQuery;
            log.info("  ✅ SELECT 쿼리 - EXPLAIN ANALYZE 실행");
        }

        // 4. EXPLAIN 실행
        StringBuilder resultBuilder = new StringBuilder();
        Double executionTimeMs = null;
        Double planningTimeMs = null;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            log.info("  🔗 데이터베이스 연결 성공");

            try (ResultSet rs = stmt.executeQuery(explainQuery)) {
                while (rs.next()) {
                    String line = rs.getString(1);
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
                    .executionMode(executionMode)
                    .explainPlan(explainPlan)
                    .executionTimeMs(executionTimeMs)
                    .planningTimeMs(planningTimeMs)
                    .build();

        } catch (Exception e) {
            log.error("  ❌ EXPLAIN ANALYZE 실행 실패: {}", e.getMessage());
            throw new RuntimeException("쿼리 분석 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 🆕 PreparedStatement 파라미터 치환
     * $1, $2, $3 등을 실제 값으로 치환
     *
     * 치환 규칙:
     * - $1 → 'max_connections' (pg_settings의 name 파라미터)
     * - $2 → 1 (LIMIT 값)
     * - $3 → true (ON 조건)
     * - 기타 숫자 파라미터 → NULL 또는 적절한 기본값
     */
    private String replaceParameters(String query) {
        if (query == null || !query.contains("$")) {
            return query;
        }

        String result = query;

        // 공통 파라미터 치환 패턴
        // $1 → 'max_connections' (WHERE name = $1)
        result = result.replaceAll("= \\$1(?!\\d)", "= 'max_connections'");
        result = result.replaceAll("WHERE name = \\$1", "WHERE name = 'max_connections'");

        // $2 → 1 (LIMIT $2)
        result = result.replaceAll("LIMIT \\$2(?!\\d)", "LIMIT 1");

        // $3 → true (ON $3)
        result = result.replaceAll("ON \\$3(?!\\d)", "ON true");

        // 남은 $숫자 파라미터를 NULL로 치환 (안전한 폴백)
        // 단, 이미 치환된 것은 건드리지 않음
        Pattern pattern = Pattern.compile("\\$(\\d+)");
        Matcher matcher = pattern.matcher(result);

        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String paramNum = matcher.group(1);

            // 컨텍스트에 따라 적절한 값 할당
            String replacement;
            if (result.substring(Math.max(0, matcher.start() - 20), matcher.start()).contains("LIMIT")) {
                replacement = "10"; // LIMIT 절에서는 10
            } else if (result.substring(Math.max(0, matcher.start() - 20), matcher.start()).contains("WHERE")) {
                replacement = "NULL"; // WHERE 절에서는 NULL
            } else {
                replacement = "NULL"; // 기본값 NULL
            }

            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);

        return sb.toString();
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
     * 쿼리 타입 추출
     */
    private String getQueryType(String upperQuery) {
        if (upperQuery.startsWith("SELECT")) return "SELECT";
        if (upperQuery.startsWith("INSERT")) return "INSERT";
        if (upperQuery.startsWith("UPDATE")) return "UPDATE";
        if (upperQuery.startsWith("DELETE")) return "DELETE";
        if (upperQuery.startsWith("CREATE")) return "CREATE";
        if (upperQuery.startsWith("DROP")) return "DROP";
        if (upperQuery.startsWith("ALTER")) return "ALTER";
        return "OTHER";
    }
}