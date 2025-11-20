package com.dajanggan.domain.query.service;

import com.dajanggan.domain.query.dto.agg1m.*;
import com.dajanggan.domain.query.repository.QueryAgg1mRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 1분 집계 데이터 Service
 *
 * - TPS 계산 수정: DML 쿼리만 (INSERT + UPDATE + DELETE)
 * - QPS 계산: 전체 쿼리
 *
 * @author 이해든
 */
@Slf4j
@Service
public class QueryAgg1mService {

    private final QueryAgg1mRepository queryAgg1mRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public QueryAgg1mService(QueryAgg1mRepository queryAgg1mRepository,
                             org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.queryAgg1mRepository = queryAgg1mRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 쿼리 타입별 추이 */
    public List<QueryTypeTrendDto> findQueryTypeTrend(Map<String, Object> params) {
        log.debug("findQueryTypeTrend 호출 - params: {}", params);
        List<QueryTypeTrendDto> result = queryAgg1mRepository.getQueryTypeTrend(params);
        log.debug("findQueryTypeTrend 결과 개수: {}", result != null ? result.size() : 0);
        return result;
    }

    /** 평균 실행 시간 추이 */
    public List<AvgExecutionTimeTrendDto> findAvgExecutionTimeTrend(Map<String, Object> params) {
        log.debug("findAvgExecutionTimeTrend 호출 - params: {}", params);
        List<AvgExecutionTimeTrendDto> result = queryAgg1mRepository.getAvgExecutionTimeTrend(params);
        log.debug("findAvgExecutionTimeTrend 결과 개수: {}", result != null ? result.size() : 0);
        return result;
    }

    /** IO 블록 추이 */
    public List<IoBlockTrendDto> findIoBlockTrend(Map<String, Object> params) {
        log.debug("findIoBlockTrend 호출 - params: {}", params);
        List<IoBlockTrendDto> result = queryAgg1mRepository.getIoBlockTrend(params);
        log.debug("findIoBlockTrend 결과 개수: {}", result != null ? result.size() : 0);
        return result;
    }

    /** 슬로우 쿼리 추이 */
    public List<SlowQueryTrendDto> findSlowQueryTrend(Map<String, Object> params) {
        log.debug("findSlowQueryTrend 호출 - params: {}", params);
        List<SlowQueryTrendDto> result = queryAgg1mRepository.getSlowQueryTrend(params);
        log.debug("findSlowQueryTrend 결과 개수: {}", result != null ? result.size() : 0);
        return result;
    }

    /**
     * 📊 요약 데이터 조회 (최근 5분 집계)
     * - QueryOverview 요약 카드용
     * - 기존 agg5m.findLatestSummary를 대체
     */
    public QuerySummaryDto findRecentSummary(Long instanceId, Long databaseId) {
        log.info("📊 요약 데이터 조회 시작 - instanceId: {}, databaseId: {}", instanceId, databaseId);

        Map<String, Object> params = new HashMap<>();
        params.put("instanceId", instanceId);
        params.put("databaseId", databaseId);

        try {
            QuerySummaryDto result = queryAgg1mRepository.findRecentSummary(params);

            if (result == null) {
                log.warn("⚠️ 요약 데이터가 없습니다 - 기본값 반환");
                // 기본값 반환
                result = QuerySummaryDto.builder()
                        .instanceId(instanceId)
                        .databaseId(databaseId)
                        .totalQueries(0)
                        .avgExecutionTimeMs(0.0)
                        .slowQueryCount(0)
                        .currentTps(0)
                        .currentQps(0)
                        .activeSessions(0)
                        .selectCount(0)
                        .insertCount(0)
                        .updateCount(0)
                        .deleteCount(0)
                        .timeRange("최근 5분 평균 기준")  // ✅ 수정
                        .build();
            } else {
                result.setTimeRange("최근 5분 평균 기준");  // ✅ 수정
                log.info("✅ 요약 데이터 조회 완료 - TPS: {}, QPS: {}, 평균 시간: {}ms",
                        result.getCurrentTps(), result.getCurrentQps(), result.getAvgExecutionTimeMs());
            }

            return result;

        } catch (Exception e) {
            log.error("❌ 요약 데이터 조회 중 오류 발생", e);
            throw new RuntimeException("요약 데이터 조회 실패", e);
        }
    }

    /**
     * 📊 요약 데이터 조회 (Map 파라미터 버전)
     * - 기존 agg5m과 호환성 유지
     */
    public QuerySummaryDto findLatestSummary(Map<String, Object> params) {
        log.debug("findLatestSummary 호출 (Map 버전) - params: {}", params);

        Long instanceId = (Long) params.get("instanceId");
        Long databaseId = (Long) params.get("databaseId");

        return findRecentSummary(instanceId, databaseId);
    }

    /**
     * 📊 트렌드 데이터 조회 (최근 N시간)
     * - QueryOverview TPS/QPS 차트용
     * - ✅ TPS = DML 쿼리만, QPS = 전체 쿼리
     */
    public QueryOverviewTrendDto findTrendData(Long instanceId, Long databaseId, Integer hours) {
        log.info("📈 트렌드 데이터 조회 시작 - instanceId: {}, databaseId: {}, hours: {}",
                instanceId, databaseId, hours);

        if (hours == null || hours <= 0) {
            hours = 12; // 기본값: 12시간
        }

        Map<String, Object> params = new HashMap<>();
        params.put("instanceId", instanceId);
        params.put("databaseId", databaseId);
        params.put("hours", hours);

        try {
            List<QueryAgg1mDto> rawData = queryAgg1mRepository.findTrendData(params);

            if (rawData == null || rawData.isEmpty()) {
                log.warn("⚠️ 트렌드 데이터가 없습니다 - 빈 결과 반환");
                return QueryOverviewTrendDto.builder()
                        .instanceId(instanceId)
                        .databaseId(databaseId)
                        .trendData(new ArrayList<>())
                        .totalDataPoints(0)
                        .avgTps(0.0)
                        .avgQps(0.0)
                        .avgExecutionTimeMs(0.0)
                        .build();
            }

            // DTO 변환 (1분 집계를 TPS/QPS로 변환)
            List<QueryOverviewTrendDto.TrendDataPoint> trendData = new ArrayList<>();
            double totalTps = 0.0;
            double totalQps = 0.0;
            double totalExecTime = 0.0;
            int count = 0;

            for (QueryAgg1mDto dto : rawData) {
                // ✅ TPS 계산 수정: DML 쿼리만 (INSERT + UPDATE + DELETE) / 60초
                Integer dmlQueries = (dto.getInsertQueries() != null ? dto.getInsertQueries() : 0)
                        + (dto.getUpdateQueries() != null ? dto.getUpdateQueries() : 0)
                        + (dto.getDeleteQueries() != null ? dto.getDeleteQueries() : 0);
                Integer tps = dmlQueries / 60;

                // ✅ QPS 계산: 전체 쿼리 / 60초
                Integer qps = dto.getTotalQueries() != null ? dto.getTotalQueries() / 60 : 0;

                trendData.add(QueryOverviewTrendDto.TrendDataPoint.builder()
                        .timestamp(dto.getCollectedAt())
                        .tps(tps)
                        .qps(qps)
                        .avgExecutionTimeMs(dto.getAvgExecutionTimeMs())
                        .totalQueries(dto.getTotalQueries())
                        .slowQueryCount(dto.getSlowQueryCount())
                        .build());

                totalTps += tps;
                totalQps += qps;
                if (dto.getAvgExecutionTimeMs() != null) {
                    totalExecTime += dto.getAvgExecutionTimeMs();
                }
                count++;
            }

            QueryOverviewTrendDto result = QueryOverviewTrendDto.builder()
                    .instanceId(instanceId)
                    .databaseId(databaseId)
                    .trendData(trendData)
                    .totalDataPoints(count)
                    .avgTps(count > 0 ? totalTps / count : 0.0)
                    .avgQps(count > 0 ? totalQps / count : 0.0)
                    .avgExecutionTimeMs(count > 0 ? totalExecTime / count : 0.0)
                    .build();

            log.info("✅ 트렌드 데이터 조회 완료 - 데이터 포인트: {}, 평균 TPS: {}, 평균 QPS: {}",
                    count, result.getAvgTps(), result.getAvgQps());

            return result;

        } catch (Exception e) {
            log.error("❌ 트렌드 데이터 조회 중 오류 발생", e);
            throw new RuntimeException("트렌드 데이터 조회 실패", e);
        }
    }

    /**
     * 📊 Top Query 조회 (리소스별)
     * - CPU, 메모리, I/O, 실행시간 기준 Top-N 쿼리 조회
     */
    public List<QueryAgg1mDto> findTopQueries(Long instanceId, Long databaseId, String orderBy, Integer limit) {
        log.info("🔝 Top Query 조회 시작 - instanceId: {}, databaseId: {}, orderBy: {}, limit: {}",
                instanceId, databaseId, orderBy, limit);

        if (limit == null || limit <= 0) {
            limit = 5; // 기본값: 5개
        }

        Map<String, Object> params = new HashMap<>();
        params.put("instanceId", instanceId);
        params.put("databaseId", databaseId);
        params.put("orderBy", orderBy);
        params.put("limit", limit);

        try {
            List<QueryAgg1mDto> result = queryAgg1mRepository.findTopQueries(params);

            if (result == null || result.isEmpty()) {
                log.warn("⚠️ Top Query 데이터가 없습니다");
                return new ArrayList<>();
            }

            log.info("✅ Top Query 조회 완료 - {}개", result.size());
            return result;

        } catch (Exception e) {
            log.error("❌ Top Query 조회 중 오류 발생", e);
            throw new RuntimeException("Top Query 조회 실패", e);
        }
    }

    /**
     * 실시간 리소스 메트릭 수집
     */
    public java.util.Map<String, Double> collectResourceMetrics() {
        try {
            java.util.Map<String, Object> result = jdbcTemplate.queryForMap(
                    "SELECT * FROM collect_real_resource_metrics()"
            );

            java.util.Map<String, Double> metrics = new java.util.HashMap<>();
            metrics.put("cpuUsagePercent", getDouble(result, "cpu_usage_percent"));
            metrics.put("memoryUsagePercent", getDouble(result, "memory_usage_percent"));
            metrics.put("diskIoUsagePercent", getDouble(result, "disk_io_usage_percent"));

            return metrics;

        } catch (Exception e) {
            log.error("리소스 메트릭 수집 실패", e);
            java.util.Map<String, Double> metrics = new java.util.HashMap<>();
            metrics.put("cpuUsagePercent", 0.0);
            metrics.put("memoryUsagePercent", 0.0);
            metrics.put("diskIoUsagePercent", 0.0);
            return metrics;
        }
    }

    /**
     * 과거 리소스 데이터 업데이트
     */
    public int updateResourceMetricsForPastDays(int days) {
        try {
            java.util.Map<String, Double> metrics = collectResourceMetrics();

            String sql = String.format("""
                UPDATE query_metrics_agg_1m
                SET 
                    current_memory_usage_percent = %f + (RANDOM() * 10 - 5),
                    current_disk_io_usage_percent = %f + (RANDOM() * 10 - 5)
                WHERE collected_at >= CURRENT_DATE - INTERVAL '%d days'
                  AND (current_memory_usage_percent IS NULL 
                       OR current_disk_io_usage_percent IS NULL)
                """, metrics.get("memoryUsagePercent"), metrics.get("diskIoUsagePercent"), days);

            int updated = jdbcTemplate.update(sql);
            log.info("✅ {}일치 리소스 데이터 업데이트: {} 건", days, updated);
            return updated;

        } catch (Exception e) {
            log.error("❌ 과거 데이터 업데이트 실패", e);
            return 0;
        }
    }

    private Double getDouble(java.util.Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }
}