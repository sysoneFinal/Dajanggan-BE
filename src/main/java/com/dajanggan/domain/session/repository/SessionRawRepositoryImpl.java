package com.dajanggan.domain.session.repository;

import com.dajanggan.domain.session.dto.raw.LockSessionDto;
import com.dajanggan.domain.session.dto.raw.SessionRawMetricDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SessionRawRepository의 JdbcTemplate 기반 구현체
 * 대상 PostgreSQL 인스턴스에 동적으로 연결하여 메트릭 수집
 */
@Repository
@RequiredArgsConstructor
public class SessionRawRepositoryImpl {

    /**
     * 대상 PostgreSQL 인스턴스의 활성 세션 조회
     */
    public List<SessionRawMetricDto> getActiveSessions(JdbcTemplate jdbcTemplate) {
        String sql = """
            SELECT
                a.pid,
                a.datid AS db_id,
                a.datname AS database_name,
                a.usename AS username,
                a.client_addr,
                a.application_name,
                a.state,
                a.wait_event_type,
                a.wait_event,
                a.query,
                a.query_start,
                b.blocking_pid,
                b.blocking_username,
                (SELECT setting::int FROM pg_settings WHERE name = 'max_connections') as max_connections
            FROM pg_stat_activity a
            LEFT JOIN LATERAL (
                SELECT 
                    unnest(pg_blocking_pids(a.pid)) AS blocking_pid,
                    blocker.usename AS blocking_username
                FROM pg_stat_activity blocker
                WHERE blocker.pid = ANY(pg_blocking_pids(a.pid))
                LIMIT 1
            ) b ON true
            WHERE a.datname IS NOT NULL
              AND a.pid != pg_backend_pid()
            ORDER BY a.query_start DESC NULLS LAST, a.pid
            """;

        return jdbcTemplate.query(sql, new SessionRawMetricRowMapper());
    }

    /**
     * 대상 PostgreSQL 인스턴스에서 대기 중인 락 정보 조회
     * @return Map<PID, LockSessionDto>
     */
    public Map<Integer, LockSessionDto> getWaitingLocks(JdbcTemplate jdbcTemplate) {
        String sql = """
            SELECT 
                l.pid,
                l.mode,              
                l.locktype,
                l.relation::regclass AS table_name,
                EXTRACT(EPOCH FROM (NOW() - a.state_change)) AS wait_duration_sec
            FROM pg_locks l
            JOIN pg_stat_activity a ON l.pid = a.pid
            WHERE l.granted = false
              AND l.pid IS NOT NULL
            """;

        Map<Integer, LockSessionDto> lockMap = new HashMap<>();

        jdbcTemplate.query(sql, rs -> {
            int pid = rs.getInt("pid");
            String mode = rs.getString("mode");
            String locktype = rs.getString("locktype");
            String tableName = rs.getString("table_name");
            double waitDuration = rs.getDouble("wait_duration_sec");

            // 가장 오래 대기한 락만 저장 (같은 PID가 여러 락 대기 중일 수 있음)
            lockMap.compute(pid, (k, existing) -> {
                if (existing == null || waitDuration > existing.getWaitDurationSec()) {
                    return new LockSessionDto(mode, locktype, tableName, waitDuration);
                }
                return existing;
            });
        });

        return lockMap;
    }

    /**
     * SessionRawMetricDto로 매핑
     */
    private static class SessionRawMetricRowMapper implements RowMapper<SessionRawMetricDto> {
        @Override
        public SessionRawMetricDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            SessionRawMetricDto dto = new SessionRawMetricDto();

            dto.setPid(rs.getInt("pid"));
            dto.setDatabasename(rs.getString("database_name"));
            dto.setUsername(rs.getString("username"));
            dto.setClientAddr(rs.getString("client_addr"));
            dto.setApplicationName(rs.getString("application_name"));
            dto.setState(rs.getString("state"));
            dto.setWaitEventType(rs.getString("wait_event_type"));
            dto.setWaitEvent(rs.getString("wait_event"));
            dto.setQuery(rs.getString("query"));
            dto.setMaxConnections(rs.getInt("max_connections"));

            // 블로킹 정보 매핑
            Integer blockingPid = (Integer) rs.getObject("blocking_pid");
            dto.setBlockingPid(blockingPid);
            dto.setBlockingUsername(rs.getString("blocking_username"));

            // Timestamp를 OffsetDateTime으로 변환
            Timestamp queryStartTs = rs.getTimestamp("query_start");
            if (queryStartTs != null) {
                dto.setQueryStart(OffsetDateTime.ofInstant(
                        queryStartTs.toInstant(),
                        ZoneId.systemDefault()
                ));
            }

            return dto;
        }
    }
}