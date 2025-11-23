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

@Slf4j
@RequiredArgsConstructor
@Service
public class QueryMetricsCollector {

    private final QueryRawRepository queryRawRepository;
    private final QueryRawRepositoryImpl queryRawRepositoryImpl;
    private final DataSourceFactory dataSourceFactory;
    private final QueryEventDetector queryEventDetector;
    private final EventService eventService;

    // ✅ 최적화 설정 추가
    private static final int BATCH_SIZE = 500;  // 배치 크기
    private static final double MIN_EXECUTION_TIME_MS = 100.0;  // 100ms 이상만 저장
    private static final int MAX_QUERY_TEXT_LENGTH = 2000;  // 쿼리 텍스트 길이 제한

    /**
     * 쿼리 원시 지표 수집기 (Database 단위) - 최적화 버전
     */
    public void collect(Instance instance, Database database, OffsetDateTime collectedAt) {
        long startTime = System.currentTimeMillis();

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

        int totalCount = queries.size();
        log.info("📊 [{}] 수집된 전체 쿼리: {}개 (Database: {})",
                collectedAt, totalCount, database.getDatabaseName());

        // ✅ 쿼리 가공 및 필터링
        List<QueryRawMetricDto> processedQueries = new ArrayList<>();
        int filteredCount = 0;

        for (QueryRawMetricDto dto : queries) {
            // ✅ 필터링 1: 실행 시간이 100ms 이상인 쿼리만 저장
            BigDecimal executionTimeMs = dto.getExecutionTimeMs();
            if (executionTimeMs == null || executionTimeMs.doubleValue() < MIN_EXECUTION_TIME_MS) {
                filteredCount++;
                continue;
            }

            // ✅ 필터링 2: 시스템 쿼리 제외
            String queryText = dto.getQueryText();
            if (isSystemQuery(queryText)) {
                filteredCount++;
                continue;
            }

            // ✅ EXPLAIN이 포함된 쿼리 정제
            if (queryText != null && !queryText.isBlank()) {
                String upperQuery = queryText.toUpperCase().trim();

                if (upperQuery.startsWith("EXPLAIN")) {
                    log.debug("EXPLAIN 제거 전: {}", queryText.substring(0, Math.min(100, queryText.length())));

                    // EXPLAIN (...) 부분 제거
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

            // Database ID와 Instance ID 설정
            dto.setDatabaseId(database.getDatabaseId());
            dto.setInstanceId(instance.getInstanceId());

            // 쿼리 해시 생성
            if (queryText != null) {
                dto.setQueryHash(generateQueryHash(queryText));
            }

            // 쿼리 타입 추출
            if (queryText != null && !queryText.isBlank()) {
                dto.setQueryType(queryText.trim().split("\\s+")[0].toUpperCase(Locale.ROOT));
            }

            // ✅ 쿼리 텍스트 길이 제한
            if (queryText != null && queryText.length() > MAX_QUERY_TEXT_LENGTH) {
                dto.setQueryText(queryText.substring(0, MAX_QUERY_TEXT_LENGTH) + "...[truncated]");
            }

            // 짧은 쿼리 생성 (100자 제한)
            if (queryText != null && queryText.length() > 100) {
                dto.setShortQuery(queryText.substring(0, 97) + "...");
            } else {
                dto.setShortQuery(queryText);
            }

            // CPU 사용률 계산 (execution_time_ms 기반, 0-100% 범위)
            if (executionTimeMs != null && executionTimeMs.compareTo(BigDecimal.ZERO) > 0) {
                double execTime = executionTimeMs.doubleValue();
                double cpuPercent = Math.min(100.0, execTime / 10.0);
                dto.setCpuUsagePercent(BigDecimal.valueOf(cpuPercent));
            } else {
                dto.setCpuUsagePercent(BigDecimal.ZERO);
            }

            // 메모리 사용량 계산 (execution_time_ms 기반 추정, MB 단위)
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

            // I/O 블록 수 계산 (execution_count 기반 추정)
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

        // ✅ 필터링 결과 로깅
        log.info("🔍 필터링 결과: {}개 → {}개 저장 ({}개 제외, {:.1f}% 감소)",
                totalCount, processedQueries.size(), filteredCount,
                (filteredCount * 100.0 / totalCount));

        if (processedQueries.isEmpty()) {
            log.info("✅ 저장할 중요 쿼리 없음 (모두 100ms 미만 또는 시스템 쿼리)");
            return;
        }

        // ✅ 배치로 나눠서 저장
        insertInBatches(processedQueries);

        long endTime = System.currentTimeMillis();
        log.info("✅ [{}] 쿼리 메트릭 저장 완료: {}개 (소요 시간: {}ms, Database: {}:{}, 데이터 감소율: {:.1f}%)",
                collectedAt,
                processedQueries.size(),
                (endTime - startTime),
                instance.getHost(),
                instance.getPort(),
                (filteredCount * 100.0 / totalCount));

        // ✅ 쿼리 이벤트 감지 및 저장
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
                log.info("📊 [{}] 쿼리 이벤트 {}개 감지 및 저장 완료 (Database: {})",
                        collectedAt, events.size(), database.getDatabaseName());
            }
        } catch (Exception e) {
            log.error("쿼리 이벤트 감지 중 오류 발생", e);
            // 이벤트 감지 실패해도 메트릭 수집은 계속 진행
        }
    }

    /**
     * ✅ 시스템 쿼리 판별
     */
    private boolean isSystemQuery(String queryText) {
        if (queryText == null || queryText.isBlank()) {
            return true;
        }

        String upper = queryText.toUpperCase();

        // pg_catalog, information_schema 쿼리 제외
        if (upper.contains("PG_CATALOG") || upper.contains("INFORMATION_SCHEMA")) {
            return true;
        }

        // VACUUM, ANALYZE 제외
        if (upper.startsWith("VACUUM") || upper.startsWith("ANALYZE")) {
            return true;
        }

        // 백그라운드 통계 쿼리 제외
        if (upper.contains("PG_STAT_") || upper.contains("PG_CLASS")) {
            return true;
        }

        return false;
    }

    /**
     * ✅ 배치로 나눠서 삽입
     */
    private void insertInBatches(List<QueryRawMetricDto> queries) {
        if (queries.isEmpty()) {
            return;
        }

        int totalSize = queries.size();
        int batchCount = (totalSize + BATCH_SIZE - 1) / BATCH_SIZE;

        log.debug("📦 배치 삽입 시작: 총 {}개 → {}개 배치 (배치 크기: {})",
                totalSize, batchCount, BATCH_SIZE);

        for (int i = 0; i < totalSize; i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, totalSize);
            List<QueryRawMetricDto> batch = queries.subList(i, end);

            try {
                long batchStartTime = System.currentTimeMillis();

                queryRawRepository.insertQueryMetrics(batch);

                long batchEndTime = System.currentTimeMillis();
                long duration = batchEndTime - batchStartTime;

                // ⚠️ 배치 삽입이 5초 이상 걸리면 경고
                if (duration > 5000) {
                    log.warn("⚠️ 배치 {}/{} 느림: {}개 삽입에 {}ms 소요!",
                            (i / BATCH_SIZE + 1), batchCount, batch.size(), duration);
                } else {
                    log.debug("  ✓ 배치 {}/{} 완료: {}개 삽입 ({}ms)",
                            (i / BATCH_SIZE + 1), batchCount, batch.size(), duration);
                }

            } catch (Exception e) {
                log.error("  ✗ 배치 {}/{} 실패: {}",
                        (i / BATCH_SIZE + 1), batchCount, e.getMessage());
                // 배치 실패 시에도 다음 배치 계속 진행
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
            return hexString.substring(0, 16); // 16자만 사용
        } catch (Exception e) {
            log.warn("Failed to generate query hash: {}", e.getMessage());
            return null;
        }
    }

    /**
     * QueryRawMetricDto 리스트를 QueryEventDetector가 기대하는 형식으로 변환
     */
    private List<Map<String, Object>> convertToQueryStats(List<QueryRawMetricDto> queries) {
        List<Map<String, Object>> queryStats = new ArrayList<>();

        for (QueryRawMetricDto dto : queries) {
            Map<String, Object> stat = new HashMap<>();
            
            // queryMetricId는 queryHash를 사용 (또는 queryId)
            stat.put("queryMetricId", dto.getQueryHash() != null ? dto.getQueryHash() : dto.getQueryId());
            stat.put("shortQuery", dto.getShortQuery());
            
            // 평균 실행 시간 (ms)
            if (dto.getExecutionTimeMs() != null) {
                stat.put("avgExecutionTimeMs", dto.getExecutionTimeMs().doubleValue());
            }
            
            // 총 실행 시간 = 평균 실행 시간 * 실행 횟수
            if (dto.getExecutionTimeMs() != null && dto.getExecutionCount() != null) {
                double totalTime = dto.getExecutionTimeMs().doubleValue() * dto.getExecutionCount();
                stat.put("totalExecutionTimeMs", totalTime);
            }
            
            // 실행 횟수
            if (dto.getExecutionCount() != null) {
                stat.put("executionCount", dto.getExecutionCount());
            }

            queryStats.add(stat);
        }

        return queryStats;
    }
}