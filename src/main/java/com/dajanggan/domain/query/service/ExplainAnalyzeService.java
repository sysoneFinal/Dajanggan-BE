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
 * - 안전 모드 처리 (DML 쿼리, 파라미터 포함 쿼리)
 *
 * ✅ 올바른 방식:
 * 1. 데이터 변경 쿼리 → EXPLAIN만 (안전 모드)
 * 2. 파라미터 포함 → EXPLAIN만 (안전 모드)
 * 3. 일반 SELECT → EXPLAIN ANALYZE (실제 실행)
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

        // 3. 쿼리 전처리
        String trimmedQuery = query.trim();
        String upperQuery = trimmedQuery.toUpperCase();

        // 4. 쿼리 타입 체크
        boolean isModifyingQuery = upperQuery.startsWith("UPDATE") ||
                upperQuery.startsWith("INSERT") ||
                upperQuery.startsWith("DELETE") ||
                upperQuery.startsWith("CREATE") ||
                upperQuery.startsWith("DROP") ||
                upperQuery.startsWith("ALTER") ||
                upperQuery.startsWith("TRUNCATE");

        // 5. 파라미터 플레이스홀더 체크
        boolean hasParameters = containsParameterPlaceholders(trimmedQuery);

        log.info("  - 쿼리 타입: {}", getQueryType(upperQuery));
        log.info("  - 데이터 변경 여부: {}", isModifyingQuery);
        log.info("  - 파라미터 포함 여부: {}", hasParameters);

        // 6. JdbcTemplate 생성
        JdbcTemplate jdbcTemplate = dataSourceFactory.createJdbcTemplate(
                instance,
                database.getDatabaseName()
        );

        log.info("  🔗 데이터베이스 연결 성공");

        // 7. 실행 전략 결정
        if (isModifyingQuery) {
            // 데이터 변경 쿼리 → EXPLAIN만 (안전 모드)
            log.info("  ⚠️ 데이터 변경 쿼리 감지 - 안전 모드로 실행");
            return executeExplainOnly(jdbcTemplate, trimmedQuery, "안전 모드");
        }

        if (hasParameters) {
            // 파라미터 포함 → EXPLAIN만 (안전 모드)
            log.info("  ⚠️ 파라미터 플레이스홀더 감지 - 안전 모드로 실행");
            return executeExplainOnly(jdbcTemplate, trimmedQuery, "안전 모드 (파라미터 포함)");
        }

        // 일반 SELECT 쿼리 → EXPLAIN ANALYZE (실제 실행)
        log.info("  ✅ 일반 SELECT 쿼리 - EXPLAIN ANALYZE 실행");
        return executeExplainAnalyze(jdbcTemplate, trimmedQuery);
    }

    /**
     * ✅ 파라미터 플레이스홀더 포함 여부 확인
     * $1, $2, $3 등의 패턴을 감지
     */
    private boolean containsParameterPlaceholders(String query) {
        // $숫자 패턴 검색
        Pattern pattern = Pattern.compile("\\$\\d+");
        Matcher matcher = pattern.matcher(query);
        return matcher.find();  // $1, $2 등이 있으면 true
    }

    /**
     * ✅ EXPLAIN만 실행 (안전 모드)
     * - 실제 쿼리 실행 없음
     * - 예상 실행 계획만 표시
     */
    private ExplainAnalyzeResult executeExplainOnly(JdbcTemplate jdbcTemplate, String query, String mode) {
        try {
            String explainQuery = "EXPLAIN (FORMAT TEXT) " + query;

            List<String> resultLines = jdbcTemplate.queryForList(explainQuery, String.class);

            StringBuilder resultBuilder = new StringBuilder();
            for (String line : resultLines) {
                resultBuilder.append(line).append("\n");
            }

            String explainPlan = resultBuilder.toString();

            log.info("  ✅ EXPLAIN ({}) 실행 완료", mode);

            // 안전 모드에서는 실행 시간 없음
            return ExplainAnalyzeResult.builder()
                    .executionMode(mode)
                    .explainPlan(explainPlan)
                    .executionTimeMs(null)  // 실제 실행하지 않으므로 null
                    .planningTimeMs(null)   // 실제 실행하지 않으므로 null
                    .build();

        } catch (Exception e) {
            log.error("  ❌ EXPLAIN 실행 실패: {}", e.getMessage());
            throw new RuntimeException("쿼리 분석 실패: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ EXPLAIN ANALYZE 실행 (실제 실행)
     * - 쿼리 실제 실행
     * - 실행 시간 측정
     */
    private ExplainAnalyzeResult executeExplainAnalyze(JdbcTemplate jdbcTemplate, String query) {
        try {
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

        } catch (Exception e) {
            log.error("  ❌ EXPLAIN ANALYZE 실행 실패: {}", e.getMessage());
            throw new RuntimeException("쿼리 분석 실패: " + e.getMessage(), e);
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
        if (upperQuery.startsWith("TRUNCATE")) return "TRUNCATE";
        return "OTHER";
    }
}