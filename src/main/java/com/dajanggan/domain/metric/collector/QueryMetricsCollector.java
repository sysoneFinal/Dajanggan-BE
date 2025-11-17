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
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class QueryMetricsCollector {

    private final QueryRawRepository queryRawRepository;
    private final QueryRawRepositoryImpl queryRawRepositoryImpl;
    private final DataSourceFactory dataSourceFactory;

    /**
     * 쿼리 원시 지표 수집기 (Database 단위)
     */
    public void collect(Instance instance, Database database, OffsetDateTime collectedAt) {
        // JdbcTemplate 생성 (인스턴스 + 데이터베이스명으로 동적 연결)
        JdbcTemplate jdbc = dataSourceFactory.createJdbcTemplate(instance, database.getDatabaseName());

        // pg_stat_statements에서 쿼리 메트릭 조회
        List<QueryRawMetricDto> allQueries = queryRawRepositoryImpl.getQueryMetrics(jdbc);

        // 해당 데이터베이스의 쿼리만 필터링
        List<QueryRawMetricDto> queries = allQueries.stream()
                .filter(q -> database.getDatabaseName().equals(q.getDatabasename()))
                .collect(Collectors.toList());

        if (queries.isEmpty()) {
            log.debug("[{}] No query metrics found for database: {}",
                    collectedAt, database.getDatabaseName());
            return;
        }

        // 쿼리별 가공
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

            // 🆕 CPU 사용률 계산 (execution_time_ms 기반, 0-100% 범위)
            // 1000ms(1초) 이상이면 100% CPU 사용으로 간주
            BigDecimal executionTimeMs = dto.getExecutionTimeMs();
            if (executionTimeMs != null && executionTimeMs.compareTo(BigDecimal.ZERO) > 0) {
                double execTime = executionTimeMs.doubleValue();
                double cpuPercent = Math.min(100.0, execTime / 10.0);
                dto.setCpuUsagePercent(BigDecimal.valueOf(cpuPercent));
            } else {
                dto.setCpuUsagePercent(BigDecimal.ZERO);
            }

            // 🆕 메모리 사용량 계산 (execution_time_ms 기반 추정, MB 단위)
            // 느린 쿼리일수록 메모리를 많이 사용한다고 가정
            if (executionTimeMs != null && executionTimeMs.compareTo(BigDecimal.ZERO) > 0) {
                double execTime = executionTimeMs.doubleValue();
                double memoryMb;
                if (execTime > 100) {
                    // 100ms 이상: 메모리 많이 사용
                    memoryMb = execTime / 10.0;
                } else {
                    // 100ms 미만: 메모리 적게 사용
                    memoryMb = execTime / 50.0;
                }
                dto.setMemoryUsageMb(BigDecimal.valueOf(memoryMb));
            } else {
                dto.setMemoryUsageMb(BigDecimal.ZERO);
            }

            // 🆕 I/O 블록 수 계산 (execution_count 기반 추정)
            // 실행 횟수가 많을수록 I/O도 많다고 가정
            Integer executionCount = dto.getExecutionCount();
            if (executionCount != null && executionCount > 0) {
                // 실행 횟수를 I/O 블록으로 변환 (대략적인 추정)
                long ioBlocks = executionCount * 10L; // 1회 실행당 평균 10 블록
                dto.setIoBlocks(ioBlocks);
            } else {
                dto.setIoBlocks(0L);
            }

            dto.setCollectedAt(collectedAt);
            dto.setCreatedAt(collectedAt);
        }

        // 저장 - 모니터링 DB에 INSERT
        queryRawRepository.insertQueryMetrics(queries);
        log.info("[{}] Collected {} query metrics for database: {} (instance: {}:{}) - CPU/Memory/IO calculated",
                collectedAt,
                queries.size(),
                database.getDatabaseName(),
                instance.getHost(),
                instance.getPort());
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