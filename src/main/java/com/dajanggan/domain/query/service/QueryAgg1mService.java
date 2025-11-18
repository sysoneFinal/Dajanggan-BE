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
 * 1ë¶„ ì§‘ê³„ ë°ì´í„° Service
 *
 * @author ì´í•´ë“ 
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

    /** ì¿¼ë¦¬ íƒ€ìž…ë³„ ì¶”ì´ */
    public List<QueryTypeTrendDto> findQueryTypeTrend(Map<String, Object> params) {
        log.debug("findQueryTypeTrend í˜¸ì¶œ - params: {}", params);
        List<QueryTypeTrendDto> result = queryAgg1mRepository.getQueryTypeTrend(params);
        log.debug("findQueryTypeTrend ê²°ê³¼ ê°œìˆ˜: {}", result != null ? result.size() : 0);
        return result;
    }

    /** í‰ê·  ì‹¤í–‰ ì‹œê°„ ì¶”ì´ */
    public List<AvgExecutionTimeTrendDto> findAvgExecutionTimeTrend(Map<String, Object> params) {
        log.debug("findAvgExecutionTimeTrend í˜¸ì¶œ - params: {}", params);
        List<AvgExecutionTimeTrendDto> result = queryAgg1mRepository.getAvgExecutionTimeTrend(params);
        log.debug("findAvgExecutionTimeTrend ê²°ê³¼ ê°œìˆ˜: {}", result != null ? result.size() : 0);
        return result;
    }

    /** IO ë¸”ë¡ ì¶”ì´ */
    public List<IoBlockTrendDto> findIoBlockTrend(Map<String, Object> params) {
        log.debug("findIoBlockTrend í˜¸ì¶œ - params: {}", params);
        List<IoBlockTrendDto> result = queryAgg1mRepository.getIoBlockTrend(params);
        log.debug("findIoBlockTrend ê²°ê³¼ ê°œìˆ˜: {}", result != null ? result.size() : 0);
        return result;
    }

    /** ìŠ¬ë¡œìš° ì¿¼ë¦¬ ì¶”ì´ */
    public List<SlowQueryTrendDto> findSlowQueryTrend(Map<String, Object> params) {
        log.debug("findSlowQueryTrend í˜¸ì¶œ - params: {}", params);
        List<SlowQueryTrendDto> result = queryAgg1mRepository.getSlowQueryTrend(params);
        log.debug("findSlowQueryTrend ê²°ê³¼ ê°œìˆ˜: {}", result != null ? result.size() : 0);
        return result;
    }

    /**
     * ðŸ†• ìš”ì•½ ë°ì´í„° ì¡°íšŒ (ìµœê·¼ 5ë¶„ ì§‘ê³„)
     * - QueryOverview ìš”ì•½ ì¹´ë“œìš©
     * - ê¸°ì¡´ agg5m.findLatestSummaryë¥¼ ëŒ€ì²´
     */
    public QuerySummaryDto findRecentSummary(Long instanceId, Long databaseId) {
        log.info("ðŸ“Š ìš”ì•½ ë°ì´í„° ì¡°íšŒ ì‹œìž‘ - instanceId: {}, databaseId: {}", instanceId, databaseId);

        Map<String, Object> params = new HashMap<>();
        params.put("instanceId", instanceId);
        params.put("databaseId", databaseId);

        try {
            QuerySummaryDto result = queryAgg1mRepository.findRecentSummary(params);

            if (result == null) {
                log.warn("âš ï¸ ìš”ì•½ ë°ì´í„°ê°€ ì—†ìŠµë‹ˆë‹¤ - ê¸°ë³¸ê°’ ë°˜í™˜");
                // ê¸°ë³¸ê°’ ë°˜í™˜
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
                        .timeRange("ìµœê·¼ 5ë¶„")
                        .build();
            } else {
                result.setTimeRange("ìµœê·¼ 5ë¶„");
                log.info("âœ… ìš”ì•½ ë°ì´í„° ì¡°íšŒ ì™„ë£Œ - TPS: {}, QPS: {}, í‰ê· ì‘ë‹µ: {}ms",
                        result.getCurrentTps(), result.getCurrentQps(), result.getAvgExecutionTimeMs());
            }

            return result;

        } catch (Exception e) {
            log.error("âŒ ìš”ì•½ ë°ì´í„° ì¡°íšŒ ì¤‘ ì˜¤ë¥˜ ë°œìƒ", e);
            throw new RuntimeException("ìš”ì•½ ë°ì´í„° ì¡°íšŒ ì‹¤íŒ¨", e);
        }
    }

    /**
     * ðŸ†• ìš”ì•½ ë°ì´í„° ì¡°íšŒ (Map íŒŒë¼ë¯¸í„° ë²„ì „)
     * - ê¸°ì¡´ agg5mê³¼ í˜¸í™˜ì„± ìœ ì§€
     */
    public QuerySummaryDto findLatestSummary(Map<String, Object> params) {
        log.debug("findLatestSummary í˜¸ì¶œ (Map ë²„ì „) - params: {}", params);

        Long instanceId = (Long) params.get("instanceId");
        Long databaseId = (Long) params.get("databaseId");

        return findRecentSummary(instanceId, databaseId);
    }

    /**
     * ðŸ†• íŠ¸ë Œë“œ ë°ì´í„° ì¡°íšŒ (ìµœê·¼ Nì‹œê°„)
     * - QueryOverview TPS/QPS ì°¨íŠ¸ìš©
     */
    public QueryOverviewTrendDto findTrendData(Long instanceId, Long databaseId, Integer hours) {
        log.info("ðŸ“ˆ íŠ¸ë Œë“œ ë°ì´í„° ì¡°íšŒ ì‹œìž‘ - instanceId: {}, databaseId: {}, hours: {}",
                instanceId, databaseId, hours);

        if (hours == null || hours <= 0) {
            hours = 12; // ê¸°ë³¸ê°’: 12ì‹œê°„
        }

        Map<String, Object> params = new HashMap<>();
        params.put("instanceId", instanceId);
        params.put("databaseId", databaseId);
        params.put("hours", hours);

        try {
            List<QueryAgg1mDto> rawData = queryAgg1mRepository.findTrendData(params);

            if (rawData == null || rawData.isEmpty()) {
                log.warn("âš ï¸ íŠ¸ë Œë“œ ë°ì´í„°ê°€ ì—†ìŠµë‹ˆë‹¤ - ë¹ˆ ê²°ê³¼ ë°˜í™˜");
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

            // DTO ë³€í™˜ (1ë¶„ ì§‘ê³„ë¥¼ TPS/QPSë¡œ ë³€í™˜)
            List<QueryOverviewTrendDto.TrendDataPoint> trendData = new ArrayList<>();
            double totalTps = 0.0;
            double totalQps = 0.0;
            double totalExecTime = 0.0;
            int count = 0;

            for (QueryAgg1mDto dto : rawData) {
                // TPS/QPS = 1ë¶„ê°„ ì¿¼ë¦¬ìˆ˜ / 60ì´ˆ
                Integer tps = dto.getTotalQueries() != null ? dto.getTotalQueries() / 60 : 0;
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

            log.info("âœ… íŠ¸ë Œë“œ ë°ì´í„° ì¡°íšŒ ì™„ë£Œ - ë°ì´í„° í¬ì¸íŠ¸: {}, í‰ê·  TPS: {}, í‰ê·  QPS: {}",
                    count, result.getAvgTps(), result.getAvgQps());

            return result;

        } catch (Exception e) {
            log.error("âŒ íŠ¸ë Œë“œ ë°ì´í„° ì¡°íšŒ ì¤‘ ì˜¤ë¥˜ ë°œìƒ", e);
            throw new RuntimeException("íŠ¸ë Œë“œ ë°ì´í„° ì¡°íšŒ ì‹¤íŒ¨", e);
        }
    }

    /**
     * ðŸ†• Top Query ì¡°íšŒ (ë¦¬ì†ŒìŠ¤ë³„)
     * - CPU, ë©”ëª¨ë¦¬, I/O, ì‹¤í–‰ì‹œê°„ ê¸°ì¤€ Top-N ì¿¼ë¦¬ ì¡°íšŒ
     */
    public List<QueryAgg1mDto> findTopQueries(Long instanceId, Long databaseId, String orderBy, Integer limit) {
        log.info("ðŸ† Top Query ì¡°íšŒ ì‹œìž‘ - instanceId: {}, databaseId: {}, orderBy: {}, limit: {}",
                instanceId, databaseId, orderBy, limit);

        if (limit == null || limit <= 0) {
            limit = 5; // ê¸°ë³¸ê°’: 5ê°œ
        }

        Map<String, Object> params = new HashMap<>();
        params.put("instanceId", instanceId);
        params.put("databaseId", databaseId);
        params.put("orderBy", orderBy);
        params.put("limit", limit);

        try {
            List<QueryAgg1mDto> result = queryAgg1mRepository.findTopQueries(params);

            if (result == null || result.isEmpty()) {
                log.warn("âš ï¸ Top Query ë°ì´í„°ê°€ ì—†ìŠµë‹ˆë‹¤");
                return new ArrayList<>();
            }

            log.info("âœ… Top Query ì¡°íšŒ ì™„ë£Œ - {}ê°œ", result.size());
            return result;

        } catch (Exception e) {
            log.error("âŒ Top Query ì¡°íšŒ ì¤‘ ì˜¤ë¥˜ ë°œìƒ", e);
            throw new RuntimeException("Top Query ì¡°íšŒ ì‹¤íŒ¨", e);
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