package com.dajanggan.domain.query.service;

import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.repository.DatabaseRepository;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.domain.query.dto.ExplainAnalyzeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EXPLAIN ANALYZE 실행 Service
 * - PostgreSQL EXPLAIN ANALYZE 실행
 * - 안전 모드 처리 (DML 쿼리)
 * - 동적 데이터베이스 연결
 *
 * @author 이해든
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExplainAnalyzeService {

    private final DatabaseRepository databaseRepository;
    private final InstanceRepository instanceRepository;

    @Value("${DB_PASSWORD:StrongPW!234}")
    private String dbPassword;

    /**
     * EXPLAIN ANALYZE 실행
     *
     * @param databaseId 데이터베이스 ID
     * @param query 분석할 쿼리
     * @return EXPLAIN ANALYZE 결과
     */
    public ExplainAnalyzeResult execute(Long databaseId, String query) {
        log.info("🔍 쿼리 분석 시작");

        // 1. 데이터베이스 정보 조회
        Database database = databaseRepository.findById(databaseId);
        if (database == null) {
            log.error("❌ 데이터베이스를 찾을 수 없습니다: databaseId={}", databaseId);
            throw new RuntimeException("데이터베이스를 찾을 수 없습니다: databaseId=" + databaseId);
        }

        // 2. 인스턴스 정보 조회
        Instance instance = instanceRepository.findById(database.getInstanceId())
                .orElseThrow(() -> new RuntimeException("인스턴스를 찾을 수 없습니다: instanceId=" + database.getInstanceId()));

        String databaseName = database.getDatabaseName();
        log.info("  - 대상 데이터베이스: {}", databaseName);
        log.info("  - 인스턴스: {}:{}", instance.getHost(), instance.getPort());

        // 3. 쿼리 타입 체크
        String trimmedQuery = query.trim();
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

        // 4. EXPLAIN 쿼리 생성
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

        // 5. 동적으로 해당 데이터베이스에 직접 연결
        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s",
                instance.getHost(),
                instance.getPort(),
                databaseName);

        log.info("  🔗 데이터베이스 연결 URL: {}", jdbcUrl);

        // 6. EXPLAIN 실행
        StringBuilder resultBuilder = new StringBuilder();
        Double executionTimeMs = null;
        Double planningTimeMs = null;

        try (Connection conn = DriverManager.getConnection(jdbcUrl, instance.getUserName(), dbPassword);
             Statement stmt = conn.createStatement()) {

            log.info("  ✅ 데이터베이스 연결 성공: {}", databaseName);

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
            log.error("  상세 오류:", e);
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
        return "OTHER";
    }
}