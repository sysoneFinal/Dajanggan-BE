package com.dajanggan.domain.metric.collector;

import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.query.dto.raw.QueryRawMetricDto;
import com.dajanggan.domain.query.repository.QueryRawRepository;
import com.dajanggan.domain.query.repository.QueryRawRepositoryImpl;
import com.dajanggan.infrastructure.datasource.DataSourceFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class QueryMetricsCollector {

    private final QueryRawRepository queryRawRepository;
    private final QueryRawRepositoryImpl queryRawRepositoryImpl;
    private final DataSourceFactory dataSourceFactory;

    // 인스턴스별 pg_stat_statements 활성화 여부 캐시
    private final Map<Long, Boolean> pgStatStatementsCache = new ConcurrentHashMap<>();

    /**
     * 쿼리 원시 지표 수집기 (Database 단위) - 복호화된 비밀번호 사용
     */
    public void collect(Instance instance, Database database, String decryptedPassword, OffsetDateTime collectedAt) {
        long startTime = System.currentTimeMillis();

        try {
            // JdbcTemplate 생성
            JdbcTemplate jdbc = dataSourceFactory.createJdbcTemplate(instance, database.getDatabaseName(), decryptedPassword);

            //  캐시에서 확인 (인스턴스별로 한 번만 체크)
            Long instanceId = instance.getInstanceId();
            Boolean isEnabled = pgStatStatementsCache.computeIfAbsent(instanceId,
                    id -> isPgStatStatementsEnabled(jdbc));

            if (!isEnabled) {
                log.debug("⚠️ [QUERY] {}:{}/{} - pg_stat_statements 미설치로 건너뜀",
                        instance.getHost(),
                        instance.getPort(),
                        database.getDatabaseName());
                return;
            }

            // pg_stat_statements에서 쿼리 메트릭 조회
            long queryStartTime = System.currentTimeMillis();
            List<QueryRawMetricDto> allQueries = queryRawRepositoryImpl.getQueryMetrics(jdbc);
            long queryTime = System.currentTimeMillis() - queryStartTime;

            // 해당 데이터베이스의 쿼리만 필터링
            List<QueryRawMetricDto> queries = allQueries.stream()
                    .filter(q -> database.getDatabaseName().equals(q.getDatabasename()))
                    .collect(Collectors.toList());

            if (queries.isEmpty()) {
                log.debug("[{}] No query metrics found for database: {} (조회시간: {}ms)",
                        collectedAt, database.getDatabaseName(), queryTime);
                return;
            }

            // 쿼리별 가공
            long processingStartTime = System.currentTimeMillis();
            for (QueryRawMetricDto dto : queries) {
                // Database ID와 Instance ID 설정
                dto.setDatabaseId(database.getDatabaseId());
                dto.setInstanceId(instance.getInstanceId());

                // 쿼리 해시 생성
                if (dto.getQueryText() != null) {
                    dto.setQueryHash(generateQueryHash(dto.getQueryText()));
                }

                // 쿼리 타입 추출
                String query = dto.getQueryText();
                if (query != null && !query.isBlank()) {
                    dto.setQueryType(query.trim().split("\\s+")[0].toUpperCase(Locale.ROOT));
                }

                // 짧은 쿼리 생성 (100자 제한)
                if (query != null && query.length() > 100) {
                    dto.setShortQuery(query.substring(0, 97) + "...");
                } else {
                    dto.setShortQuery(query);
                }

                // CPU 사용률 계산
                BigDecimal executionTimeMs = dto.getExecutionTimeMs();
                if (executionTimeMs != null && executionTimeMs.compareTo(BigDecimal.ZERO) > 0) {
                    double execTime = executionTimeMs.doubleValue();
                    double cpuPercent = Math.min(100.0, execTime / 10.0);
                    dto.setCpuUsagePercent(BigDecimal.valueOf(cpuPercent));
                } else {
                    dto.setCpuUsagePercent(BigDecimal.ZERO);
                }

                // 메모리 사용량 계산
                if (executionTimeMs != null && executionTimeMs.compareTo(BigDecimal.ZERO) > 0) {
                    double execTime = executionTimeMs.doubleValue();
                    double memoryMb;
                    if (execTime > 100) {
                        memoryMb = execTime / 10.0;
                    } else {
                        memoryMb = execTime / 50.0;
                    }
                    dto.setMemoryUsageMb(BigDecimal.valueOf(memoryMb));
                } else {
                    dto.setMemoryUsageMb(BigDecimal.ZERO);
                }

                // I/O 블록 수 계산
                Integer executionCount = dto.getExecutionCount();
                if (executionCount != null && executionCount > 0) {
                    long ioBlocks = executionCount * 10L;
                    dto.setIoBlocks(ioBlocks);
                } else {
                    dto.setIoBlocks(0L);
                }

                dto.setCollectedAt(collectedAt);
                dto.setCreatedAt(collectedAt);
            }
            long processingTime = System.currentTimeMillis() - processingStartTime;

            // 저장 - 모니터링 DB에 INSERT
            long saveStartTime = System.currentTimeMillis();
            queryRawRepository.insertQueryMetrics(queries);
            long saveTime = System.currentTimeMillis() - saveStartTime;
            long totalTime = System.currentTimeMillis() - startTime;

            log.info("📊 [QUERY] {}:{}/{} - {} 쿼리 메트릭 수집 완료 (총: {}ms | 조회: {}ms, 가공: {}ms, 저장: {}ms)",
                    instance.getHost(),
                    instance.getPort(),
                    database.getDatabaseName(),
                    queries.size(),
                    totalTime,
                    queryTime,
                    processingTime,
                    saveTime);
        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            log.error("❌ [QUERY] {}:{}/{} - 수집 실패 ({}ms): {}",
                    instance.getHost(),
                    instance.getPort(),
                    database.getDatabaseName(),
                    totalTime,
                    e.getMessage());
            throw new RuntimeException("쿼리 메트릭 수집 실패", e);
        }
    }
    /**
     * pg_stat_statements 익스텐션 활성화 여부 확인
     */
    private boolean isPgStatStatementsEnabled(JdbcTemplate jdbc) {
        try {
            String sql = """
            SELECT EXISTS (
                SELECT 1 
                FROM pg_extension 
                WHERE extname = 'pg_stat_statements'
            )
            """;

            Boolean exists = jdbc.queryForObject(sql, Boolean.class);
            return Boolean.TRUE.equals(exists);

        } catch (Exception e) {
            log.debug("pg_stat_statements 확인 중 에러: {}", e.getMessage());
            return false;
        }
    }
    /**
     * 쿼리 해시 생성 (SHA-256)
     */
    private String generateQueryHash(String query) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(query.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.substring(0, 16); // 16자만 사용
        } catch (Exception e) {
            log.warn("Failed to generate query hash: {}", e.getMessage());
            return null;
        }
    }
}