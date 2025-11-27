/** 작성자 : 서샘이 */
package com.dajanggan.domain.overview.service;

import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.infrastructure.datasource.DataSourceFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Slf4j
@Service
public class TransactionMetricsService {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final DataSourceFactory dataSourceFactory;

    public TransactionMetricsService(NamedParameterJdbcTemplate namedJdbcTemplate,
                                     DataSourceFactory dataSourceFactory) {
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.dataSourceFactory = dataSourceFactory;
    }

    public record TransactionStats(BigDecimal tps, long xactCommit, long xactRollback) {}

    public TransactionStats calculateTPS(Instance instance, String databaseName,
                                         Long instanceId, Long databaseId) {

        JdbcTemplate targetJdbc = dataSourceFactory.createJdbcTemplate(instance, databaseName);

        Map<String, Object> currentStats = targetJdbc.queryForMap(
                "SELECT xact_commit, xact_rollback FROM pg_stat_database WHERE datname = ?", databaseName
        );

        long currentXactCommit = ((Number) currentStats.get("xact_commit")).longValue();
        long currentXactRollback = ((Number) currentStats.get("xact_rollback")).longValue();
        long currentTotalXact = currentXactCommit + currentXactRollback;

        String previousStatsSql = """
                SELECT xact_commit, xact_rollback
                FROM database_metrics_agg
                WHERE instance_id = :instanceId
                  AND database_id = :databaseId
                ORDER BY collected_at DESC
                LIMIT 1
            """;

        MapSqlParameterSource previousParams = new MapSqlParameterSource()
                .addValue("instanceId", instanceId)
                .addValue("databaseId", databaseId);

        long previousTotalXact = 0;
        long prevXactCommit = 0;
        long prevXactRollback = 0;

        try {
            Map<String, Object> previousStats = namedJdbcTemplate.queryForMap(previousStatsSql, previousParams);
            prevXactCommit = ((Number) previousStats.get("xact_commit")).longValue();
            prevXactRollback = ((Number) previousStats.get("xact_rollback")).longValue();
            previousTotalXact = prevXactCommit + prevXactRollback;
        } catch (Exception e) {
            log.debug("이전 통계 없음 (최초 집계): instanceId={}, databaseId={}", instanceId, databaseId);
        }

        BigDecimal tps = BigDecimal.valueOf(currentTotalXact - previousTotalXact)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

        return new TransactionStats(tps, currentXactCommit, currentXactRollback);
    }
}

