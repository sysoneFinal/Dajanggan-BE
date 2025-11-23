package com.dajanggan.domain.metric.collector;

import com.dajanggan.domain.event.detector.QueryEventDetector;
import com.dajanggan.domain.event.dto.EventLog;
import com.dajanggan.domain.event.service.EventService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 쿼리 메트릭 수집기
 * - 대상 PostgreSQL 인스턴스에서 pg_stat_statements 기반 쿼리 메트릭 수집
 * - 필터링 및 최적화를 통한 효율적인 데이터 저장
 * - 쿼리 이벤트 감지 및 저장
 *
 * @author 이해든
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class QueryMetricsCollector {

    private final QueryRawRepository queryRawRepository;
    private final QueryRawRepositoryImpl queryRawRepositoryImpl;
    private final DataSourceFactory dataSourceFactory;
    private final QueryEventDetector queryEventDetector;
    private final EventService eventService;

    private static final int BATCH_SIZE = 500;
    private static final double MIN_EXECUTION_TIME_MS = 100.0;
    private static final int MAX_QUERY_TEXT_LENGTH = 2000;

    /**
     * 쿼리 원시 지표 수집 (Database 단위)
     *
     * @param instance 대상 PostgreSQL 인스턴스
     * @param database 대상 데이터베이스
     * @param collectedAt 수집 시각
     */
    public void collect(Instance instance, Database database, OffsetDateTime collectedAt) {
        long startTime = System.currentTimeMillis();

        JdbcTemplate jdbc = dataSourceFactory.createJdbcTemplate(instance, database.getDatabaseName());

        List<QueryRawMetricDto> allQueries = queryRawRepositoryImpl.getQueryMetrics(jdbc);

        List<QueryRawMetricDto> queries = allQueries.stream()
                .filter(q -> database.getDatabaseName().equals(q.getDatabasename()))
                .collect(Collectors.toList());

        if (queries.isEmpty()) {
            log.debug("[{}] No query metrics found for database: {}",
                    collectedAt, database.getDatabaseName());
            return;
        }

        int totalCount = queries.size();
        log.info("[{}] 수집된 전체 쿼리: {}개 (Database: {})",
                collectedAt, totalCount, database.getDatabaseName());

        List<QueryRawMetricDto> processedQueries = new ArrayList<>();
        int filteredCount = 0;

        for (QueryRawMetricDto dto : queries) {
            BigDecimal executionTimeMs = dto.getExecutionTimeMs();
            if (executionTimeMs == null || executionTimeMs.doubleValue() < MIN_EXECUTION_TIME_MS) {
                filteredCount++;
                continue;
            }

            String queryText = dto.getQueryText();
            if (isSystemQuery(queryText)) {
                filteredCount++;
                continue;
            }

            if (queryText != null && !queryText.isBlank()) {
                String upperQuery = queryText.toUpperCase().trim();

                if (upperQuery.startsWith("EXPLAIN")) {
                    log.debug("EXPLAIN 제거 전: {}", queryText.substring(0, Math.min(100, queryText.length())));

                    int parenStart = queryText.indexOf('(');
                    int parenEnd = -1;

                    if (parenStart > 0 && parenStart < 20) {
                        int depth = 0;
                        for (int i = parenStart; i < queryText.length(); i++) {
                            if (queryText.charAt(i) == '(') depth++;
                            else if (queryText.charAt(i) == ')') {
                                depth--;
                                if (depth == 0) {
                                    parenEnd = i;
                                    break;
                                }
                            }
                        }

                        if (parenEnd > parenStart) {
                            queryText = queryText.substring(parenEnd + 1).trim();
                        }
                    } else {
                        queryText = queryText.substring(7).trim();
                    }

                    dto.setQueryText(queryText);
                    log.debug("EXPLAIN 제거 후: {}", queryText.substring(0, Math.min(100, queryText.length())));
                }
            }

            dto.setDatabaseId(database.getDatabaseId());
            dto.setInstanceId(instance.getInstanceId());

            if (queryText != null) {
                dto.setQueryHash(generateQueryHash(queryText));
            }

            if (queryText != null && !queryText.isBlank()) {
                dto.setQueryType(queryText.trim().split("\\s+")[0].toUpperCase(Locale.ROOT));
            }

            if (queryText != null && queryText.length() > MAX_QUERY_TEXT_LENGTH) {
                dto.setQueryText(queryText.substring(0, MAX_QUERY_TEXT_LENGTH) + "...[truncated]");
            }

            if (queryText != null && queryText.length() > 100) {
                dto.setShortQuery(queryText.substring(0, 97) + "...");
            } else {
                dto.setShortQuery(queryText);
            }

            if (executionTimeMs != null && executionTimeMs.compareTo(BigDecimal.ZERO) > 0) {
                double execTime = executionTimeMs.doubleValue();
                double cpuPercent = Math.min(100.0, execTime / 10.0);
                dto.setCpuUsagePercent(BigDecimal.valueOf(cpuPercent));
            } else {
                dto.setCpuUsagePercent(BigDecimal.ZERO);
            }

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

            Integer executionCount = dto.getExecutionCount();
            if (executionCount != null && executionCount > 0) {
                long ioBlocks = executionCount * 10L;
                dto.setIoBlocks(ioBlocks);
            } else {
                dto.setIoBlocks(0L);
            }

            dto.setCollectedAt(collectedAt);
            dto.setCreatedAt(collectedAt);

            processedQueries.add(dto);
        }

        log.info("필터링 결과: {}개 → {}개 저장 ({}개 제외, {:.1f}% 감소)",
                totalCount, processedQueries.size(), filteredCount,
                (filteredCount * 100.0 / totalCount));

        if (processedQueries.isEmpty()) {
            log.info("저장할 중요 쿼리 없음 (모두 100ms 미만 또는 시스템 쿼리)");
            return;
        }

        insertInBatches(processedQueries);

        long endTime = System.currentTimeMillis();
        log.info("[{}] 쿼리 메트릭 저장 완료: {}개 (소요 시간: {}ms, Database: {}:{}, 데이터 감소율: {:.1f}%)",
                collectedAt,
                processedQueries.size(),
                (endTime - startTime),
                instance.getHost(),
                instance.getPort(),
                (filteredCount * 100.0 / totalCount));

        try {
            List<Map<String, Object>> queryStats = convertToQueryStats(processedQueries);
            List<EventLog> events = queryEventDetector.detectEvents(
                    queryStats,
                    database.getDatabaseId(),
                    instance.getInstanceId(),
                    database.getDatabaseName(),
                    instance.getInstanceName()
            );

            if (!events.isEmpty()) {
                eventService.saveEvents(events);
                log.info("[{}] 쿼리 이벤트 {}개 감지 및 저장 완료 (Database: {})",
                        collectedAt, events.size(), database.getDatabaseName());
            }
        } catch (Exception e) {
            log.error("쿼리 이벤트 감지 중 오류 발생", e);
        }
    }

    /**
     * 시스템 쿼리 판별
     */
    private boolean isSystemQuery(String queryText) {
        if (queryText == null || queryText.isBlank()) {
            return true;
        }

        String upper = queryText.toUpperCase();

        if (upper.contains("PG_CATALOG") || upper.contains("INFORMATION_SCHEMA")) {
            return true;
        }

        if (upper.startsWith("VACUUM") || upper.startsWith("ANALYZE")) {
            return true;
        }

        if (upper.contains("PG_STAT_") || upper.contains("PG_CLASS")) {
            return true;
        }

        return false;
    }

    /**
     * 배치로 나눠서 삽입
     */
    private void insertInBatches(List<QueryRawMetricDto> queries) {
        if (queries.isEmpty()) {
            return;
        }

        int totalSize = queries.size();
        int batchCount = (totalSize + BATCH_SIZE - 1) / BATCH_SIZE;

        log.debug("배치 삽입 시작: 총 {}개 → {}개 배치 (배치 크기: {})",
                totalSize, batchCount, BATCH_SIZE);

        for (int i = 0; i < totalSize; i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, totalSize);
            List<QueryRawMetricDto> batch = queries.subList(i, end);

            try {
                long batchStartTime = System.currentTimeMillis();

                queryRawRepository.insertQueryMetrics(batch);

                long batchEndTime = System.currentTimeMillis();
                long duration = batchEndTime - batchStartTime;

                if (duration > 5000) {
                    log.warn("배치 {}/{} 느림: {}개 삽입에 {}ms 소요!",
                            (i / BATCH_SIZE + 1), batchCount, batch.size(), duration);
                } else {
                    log.debug("  배치 {}/{} 완료: {}개 삽입 ({}ms)",
                            (i / BATCH_SIZE + 1), batchCount, batch.size(), duration);
                }

            } catch (Exception e) {
                log.error("  배치 {}/{} 실패: {}",
                        (i / BATCH_SIZE + 1), batchCount, e.getMessage());
            }
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
            return hexString.substring(0, 16);
        } catch (Exception e) {
            log.warn("Failed to generate query hash: {}", e.getMessage());
            return null;
        }
    }

    /**
     * QueryRawMetricDto 리스트를 이벤트 감지용 형식으로 변환
     */
    private List<Map<String, Object>> convertToQueryStats(List<QueryRawMetricDto> queries) {
        List<Map<String, Object>> queryStats = new ArrayList<>();

        for (QueryRawMetricDto dto : queries) {
            Map<String, Object> stat = new HashMap<>();

            stat.put("queryMetricId", dto.getQueryHash() != null ? dto.getQueryHash() : dto.getQueryId());
            stat.put("shortQuery", dto.getShortQuery());

            if (dto.getExecutionTimeMs() != null) {
                stat.put("avgExecutionTimeMs", dto.getExecutionTimeMs().doubleValue());
            }

            if (dto.getExecutionTimeMs() != null && dto.getExecutionCount() != null) {
                double totalTime = dto.getExecutionTimeMs().doubleValue() * dto.getExecutionCount();
                stat.put("totalExecutionTimeMs", totalTime);
            }

            if (dto.getExecutionCount() != null) {
                stat.put("executionCount", dto.getExecutionCount());
            }

            queryStats.add(stat);
        }

        return queryStats;
    }
}