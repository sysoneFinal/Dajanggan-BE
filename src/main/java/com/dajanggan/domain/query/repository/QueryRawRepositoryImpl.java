package com.dajanggan.domain.query.repository;

import com.dajanggan.domain.query.dto.raw.QueryRawMetricDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * QueryRawRepository의 JdbcTemplate 기반 구현체
 * 대상 PostgreSQL 인스턴스에 동적으로 연결하여 메트릭 수집
 */
@Repository
@RequiredArgsConstructor
public class QueryRawRepositoryImpl {

    /**
     * 대상 PostgreSQL 인스턴스의 쿼리 메트릭 조회
     * pg_stat_statements 뷰 사용
     */
    public List<QueryRawMetricDto> getQueryMetrics(JdbcTemplate jdbcTemplate) {
        String sql = """
            SELECT 
                s.queryid::text as query_id,
                s.query as query_text,
                s.calls as execution_count,
                s.mean_exec_time as execution_time_ms,
                s.total_plan_time / s.calls as planning_time_ms,
                (s.shared_blks_hit + s.shared_blks_read + s.local_blks_hit + s.local_blks_read) as io_blocks,
                d.datname as database_name,
                r.rolname as username
            FROM pg_stat_statements s
            JOIN pg_database d ON s.dbid = d.oid
            JOIN pg_roles r ON s.userid = r.oid
            WHERE d.datname IS NOT NULL
              AND s.query NOT LIKE '%pg_stat_statements%'
            ORDER BY s.mean_exec_time DESC
            LIMIT 1000
            """;

        return jdbcTemplate.query(sql, new QueryRawMetricRowMapper());
    }

    /**
     * QueryRawMetricDto RowMapper
     */
    private static class QueryRawMetricRowMapper implements RowMapper<QueryRawMetricDto> {
        @Override
        public QueryRawMetricDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            QueryRawMetricDto dto = new QueryRawMetricDto();

            dto.setQueryId(rs.getString("query_id"));
            dto.setQueryText(rs.getString("query_text"));
            dto.setExecutionCount(rs.getInt("execution_count"));
            dto.setDatabasename(rs.getString("database_name"));
            dto.setUsername(rs.getString("username"));

            // BigDecimal 변환
            Double executionTime = rs.getDouble("execution_time_ms");
            dto.setExecutionTimeMs(executionTime != null ? BigDecimal.valueOf(executionTime) : null);

            Double planningTime = rs.getDouble("planning_time_ms");
            dto.setPlanningTimeMs(planningTime != null ? BigDecimal.valueOf(planningTime) : null);

            Long ioBlocks = rs.getLong("io_blocks");
            dto.setIoBlocks(ioBlocks);

            return dto;
        }
    }
}