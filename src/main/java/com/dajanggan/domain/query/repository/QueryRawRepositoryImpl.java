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
 * - 대상 PostgreSQL 인스턴스에 동적으로 연결하여 메트릭 수집
 * - pg_stat_statements와 pg_stat_activity 조인하여 상세 정보 수집
 *
 * @author 이해든
 */
@Repository
@RequiredArgsConstructor
public class QueryRawRepositoryImpl {

    /**
     * 대상 PostgreSQL 인스턴스의 쿼리 메트릭 조회
     * - pg_stat_statements: 쿼리 실행 통계
     * - pg_stat_activity: 현재 실행 중인 쿼리 정보
     * - 파라미터($1, $2, ...) 포함 쿼리는 수집 제외
     *
     * @param jdbcTemplate 대상 DB에 연결된 JdbcTemplate
     * @return 수집된 쿼리 메트릭 목록 (최대 1000건)
     */
    public List<QueryRawMetricDto> getQueryMetrics(JdbcTemplate jdbcTemplate) {
        String sql = """
            WITH query_stats AS (
                SELECT 
                    s.queryid::text as query_id,
                    s.query as query_text,
                    s.calls as execution_count,
                    s.mean_exec_time as execution_time_ms,
                    CASE WHEN s.calls > 0 THEN s.total_plan_time / s.calls ELSE 0 END as planning_time_ms,
                    (s.shared_blks_hit + s.shared_blks_read + s.local_blks_hit + s.local_blks_read) as io_blocks,
                    -- CPU 사용량 추정: execution_time 기반 (단위: %)
                    CASE 
                        WHEN s.mean_exec_time > 0 THEN 
                            LEAST(100, (s.mean_exec_time / 1000.0) * 10)
                        ELSE 0 
                    END as cpu_usage_percent,
                    -- 메모리 사용량 추정: temp blocks 기반 (단위: MB)
                    ROUND(
                        ((s.temp_blks_written + s.temp_blks_read) * 8.0 / 1024.0)::numeric, 
                        2
                    ) as memory_usage_mb,
                    d.datname as database_name,
                    r.rolname as username,
                    -- pg_stat_activity 정보 (현재 실행 중인 쿼리)
                    a.state,
                    a.application_name,
                    a.client_addr::text as client_addr
                FROM pg_stat_statements s
                JOIN pg_database d ON s.dbid = d.oid
                JOIN pg_roles r ON s.userid = r.oid
                LEFT JOIN pg_stat_activity a ON a.query = s.query 
                    AND a.datname = d.datname 
                    AND a.usename = r.rolname
                    AND a.state = 'active'
                WHERE d.datname IS NOT NULL
                  AND s.query NOT LIKE '%pg_stat_statements%'
                  AND s.query NOT LIKE '--%'
                  AND s.query NOT LIKE '%테이블%'
                  AND s.query NOT LIKE '%데이터%'
                  AND s.query NOT LIKE '%파티션%'
                  AND s.query NOT LIKE '%$%'
                  AND s.calls > 0
            )
            SELECT 
                query_id,
                query_text,
                execution_count,
                execution_time_ms,
                planning_time_ms,
                io_blocks,
                cpu_usage_percent,
                memory_usage_mb,
                database_name,
                username,
                COALESCE(state, 'idle') as state,
                COALESCE(application_name, 'unknown') as application_name,
                client_addr
            FROM query_stats
            ORDER BY execution_time_ms DESC
            LIMIT 1000
            """;

        return jdbcTemplate.query(sql, new QueryRawMetricRowMapper());
    }

    /**
     * QueryRawMetricDto RowMapper
     * ResultSet을 QueryRawMetricDto 객체로 변환
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

            Double executionTime = rs.getDouble("execution_time_ms");
            dto.setExecutionTimeMs(executionTime != null ? BigDecimal.valueOf(executionTime) : null);

            Double planningTime = rs.getDouble("planning_time_ms");
            dto.setPlanningTimeMs(planningTime != null ? BigDecimal.valueOf(planningTime) : null);

            Long ioBlocks = rs.getLong("io_blocks");
            dto.setIoBlocks(ioBlocks);

            double cpuUsage = rs.getDouble("cpu_usage_percent");
            if (!rs.wasNull()) {
                dto.setCpuUsagePercent(BigDecimal.valueOf(cpuUsage));
            } else {
                dto.setCpuUsagePercent(null);
            }

            double memoryUsage = rs.getDouble("memory_usage_mb");
            if (!rs.wasNull()) {
                dto.setMemoryUsageMb(BigDecimal.valueOf(memoryUsage));
            } else {
                dto.setMemoryUsageMb(null);
            }

            dto.setState(rs.getString("state"));
            dto.setApplicationName(rs.getString("application_name"));
            dto.setClientAddr(rs.getString("client_addr"));

            return dto;
        }
    }
}