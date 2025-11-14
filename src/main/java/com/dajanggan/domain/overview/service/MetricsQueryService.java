package com.dajanggan.domain.overview.service;

import com.dajanggan.domain.overview.dto.MetricDefinition;
import com.dajanggan.domain.overview.repository.OverviewRepository;
import com.dajanggan.global.exception.DajangganException;
import com.dajanggan.global.exception.ExceptionMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MetricsQueryService {

    private final OverviewRepository overviewRepository;

    public MetricsQueryService(OverviewRepository overviewRepository) {
        this.overviewRepository = overviewRepository;
    }

    /**
     * 메트릭 데이터 조회
     * @param dbName 데이터베이스명
     * @param instanceId 인스턴스 ID
     * @param metricNames 메트릭명 리스트
     * @param timeRange 시간 범위
     * @return 조회된 메트릭 데이터
     */
    public List<Map<String, Object>> queryMetrics(
            String dbName,
            Long instanceId,
            List<String> metricNames,
            String timeRange
    ) {
        try {
            // 1. metric_definition 테이블에서 메트릭 메타정보 조회
            List<MetricDefinition> metricDefinitions = overviewRepository.getMetricDefinitions(metricNames);

            if (metricDefinitions == null || metricDefinitions.isEmpty()) {
                log.warn("메트릭 정의를 찾을 수 없음: {}", metricNames);
                // 메트릭 정의가 없으면 빈 리스트 반환 (에러 던지지 않음)
                return Collections.emptyList();
            }

            // 조회된 메트릭과 요청한 메트릭 비교
            List<String> foundMetrics = metricDefinitions.stream()
                    .map(MetricDefinition::getName)
                    .collect(Collectors.toList());
            
            List<String> missingMetrics = metricNames.stream()
                    .filter(name -> !foundMetrics.contains(name))
                    .collect(Collectors.toList());
            
            if (!missingMetrics.isEmpty()) {
                log.warn("일부 메트릭 정의를 찾을 수 없음: {}", missingMetrics);
            }

            // 2. 메트릭을 테이블별로 그룹핑 (같은 테이블의 메트릭은 한 번에 조회)
            Map<String, List<MetricDefinition>> metricsByTable = metricDefinitions.stream()
                    .collect(Collectors.groupingBy(MetricDefinition::getTableName));

            // 3. 각 테이블별로 쿼리 실행
            List<Map<String, Object>> allResults = new ArrayList<>();

            for (Map.Entry<String, List<MetricDefinition>> entry : metricsByTable.entrySet()) {
                String tableName = entry.getKey();
                List<MetricDefinition> tableMetrics = entry.getValue();

                List<Map<String, Object>> tableResults = queryTable(
                        dbName,
                        tableName,
                        tableMetrics,
                        instanceId,
                        timeRange
                );

                allResults.addAll(tableResults);
            }

            // 4. 타임스탬프 기준으로 병합 및 정렬
            return mergeAndSortResults(allResults);

        } catch (Exception e) {
            log.error("메트릭 조회 실패: dbName={}, instanceId={}, metrics={}, error={}",
                    dbName, instanceId, metricNames, e.getMessage(), e);
            throw new DajangganException(ExceptionMessage.DB_QUERY_EXECUTION_FAILED);
        }
    }

    /**
     * 특정 테이블에서 메트릭 데이터 조회
     */
    private List<Map<String, Object>> queryTable(
            String dbName,
            String tableName,
            List<MetricDefinition> metrics,
            Long instanceId,
            String timeRange
    ) {
        // 조회할 컬럼명 리스트 생성
        List<String> columns = metrics.stream()
                .map(MetricDefinition::getColumnName)
                .collect(Collectors.toList());

        Map<String, Object> params = new HashMap<>();
        params.put("tableName", tableName);
        params.put("columns", columns);
        params.put("instanceId", instanceId);
        params.put("interval", "15 MINUTE");  // 고정 15분

        log.debug("메트릭 쿼리 실행: tableName={}, columns={}, instanceId={}, interval=15 MINUTE", 
                tableName, columns, instanceId);

        return overviewRepository.queryMetrics(params);
    }

    /**
     * 결과 병합 및 정렬
     * 타임스탬프가 같은 데이터들을 하나의 row로 병합
     */
    private List<Map<String, Object>> mergeAndSortResults(List<Map<String, Object>> results) {
        // 타임스탬프를 키로 그룹핑
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();

        for (Map<String, Object> row : results) {
            Object timestampObj = row.get("timestamp");
            if (timestampObj == null) {
                continue;
            }

            String timestamp = timestampObj.toString();

            merged.computeIfAbsent(timestamp, k -> {
                Map<String, Object> newRow = new HashMap<>();
                newRow.put("timestamp", timestamp);
                return newRow;
            }).putAll(row);
        }

        // 타임스탬프 순으로 정렬하여 반환
        return merged.values().stream()
                .sorted(Comparator.comparing(m -> m.get("timestamp").toString()))
                .collect(Collectors.toList());
    }
}
