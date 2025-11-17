package com.dajanggan.domain.overview.service;

import com.dajanggan.domain.instance.domain.Instance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
public class QueryMetricsService {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public QueryMetricsService(NamedParameterJdbcTemplate namedJdbcTemplate) {
        this.namedJdbcTemplate = namedJdbcTemplate;
    }

    public record QueryStats(
            int slowQueryCount,
            String topSlowQuery1,
            BigDecimal topSlowQuery1Time,
            String topSlowQuery2,
            BigDecimal topSlowQuery2Time,
            String topSlowQuery3,
            BigDecimal topSlowQuery3Time,
            long deadTupleTotal
    ) {}

    public QueryStats aggregate(Instance instance, String databaseName,
                                Long instanceId, Long databaseId) {

        // Query metrics from agg table
        String querySql = """
            SELECT 
                COALESCE(slow_query_count, 0) as slow_query_count,
                top_slow_query_1,
                COALESCE(top_slow_query_1_time, 0) as top_slow_query_1_time,
                top_slow_query_2,
                COALESCE(top_slow_query_2_time, 0) as top_slow_query_2_time,
                top_slow_query_3,
                COALESCE(top_slow_query_3_time, 0) as top_slow_query_3_time
            FROM query_metrics_agg_5m
            WHERE instance_id = :instanceId
              AND database_id = :databaseId
            ORDER BY collected_at DESC
            LIMIT 1
        """;

        MapSqlParameterSource queryParams = new MapSqlParameterSource()
                .addValue("instanceId", instanceId)
                .addValue("databaseId", databaseId);

        int slowQueryCount = 0;
        String q1 = null, q2 = null, q3 = null;
        BigDecimal t1 = BigDecimal.ZERO, t2 = BigDecimal.ZERO, t3 = BigDecimal.ZERO;

        try {
            var queryResult = namedJdbcTemplate.queryForMap(querySql, queryParams);
            slowQueryCount = ((Number) queryResult.get("slow_query_count")).intValue();
            q1 = (String) queryResult.get("top_slow_query_1");
            t1 = (BigDecimal) queryResult.get("top_slow_query_1_time");
            q2 = (String) queryResult.get("top_slow_query_2");
            t2 = (BigDecimal) queryResult.get("top_slow_query_2_time");
            q3 = (String) queryResult.get("top_slow_query_3");
            t3 = (BigDecimal) queryResult.get("top_slow_query_3_time");
        } catch (Exception e) {
            log.debug("쿼리 메트릭 없음: instanceId={}, databaseId={}", instanceId, databaseId);
        }

        // Vacuum metrics
        String vacuumSql = """
            SELECT COALESCE(SUM(total_dead_tuples), 0) as total_dead_tuples
            FROM vacuum_metrics_agg_1m
            WHERE instance_id = :instanceId
              AND database_id = :databaseId
              AND collected_at >= now() - interval '1 minute'
        """;

        MapSqlParameterSource vacuumParams = new MapSqlParameterSource()
                .addValue("instanceId", instanceId)
                .addValue("databaseId", databaseId);

        long deadTuples = 0;
        try {
            var vacuumResult = namedJdbcTemplate.queryForMap(vacuumSql, vacuumParams);
            deadTuples = ((Number) vacuumResult.get("total_dead_tuples")).longValue();
        } catch (Exception e) {
            log.debug("Vacuum 메트릭 없음: instanceId={}, databaseId={}", instanceId, databaseId);
        }

        return new QueryStats(slowQueryCount, q1, t1, q2, t2, q3, t3, deadTuples);
    }
}
