package com.dajanggan.domain.session.repository;

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
                pid,
                datid AS db_id,
                datname AS database_name,
                usename AS username,
                client_addr,
                application_name,
                state,
                wait_event_type,
                wait_event,
                query,
                query_start
            FROM pg_stat_activity
            WHERE datname IS NOT NULL
              AND pid != pg_backend_pid()
            ORDER BY query_start DESC NULLS LAST
            """;

        return jdbcTemplate.query(sql, new SessionRawMetricRowMapper());
    }

    /**
     * 대상 PostgreSQL 인스턴스의 현재 락 정보 조회
     * @return Map<PID, LockMode>
     */
    public Map<Integer, String> getCurrentLocks(JdbcTemplate jdbcTemplate) {
        String sql = """
            SELECT 
                pid,
                mode
            FROM pg_locks
            WHERE granted = true
              AND pid IS NOT NULL
            """;

        Map<Integer, String> lockMap = new HashMap<>();
        
        jdbcTemplate.query(sql, rs -> {
            int pid = rs.getInt("pid");
            String mode = rs.getString("mode");
            // 같은 PID가 여러 락을 가질 수 있으므로, 가장 강한 락만 저장하거나 연결
            lockMap.merge(pid, mode, (existing, newMode) -> existing + "," + newMode);
        });

        return lockMap;
    }

    /**
     * SessionRawMetricDto RowMapper
     */
    private static class SessionRawMetricRowMapper implements RowMapper<SessionRawMetricDto> {
        @Override
        public SessionRawMetricDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            SessionRawMetricDto dto = new SessionRawMetricDto();
            
            dto.setPid(rs.getInt("pid"));
            dto.setDatabasename(rs.getString("database_name")); // 수정: database_name
            dto.setUsername(rs.getString("username"));
            dto.setClientAddr(rs.getString("client_addr"));
            dto.setApplicationName(rs.getString("application_name"));
            dto.setState(rs.getString("state"));
            dto.setWaitEventType(rs.getString("wait_event_type"));
            dto.setWaitEvent(rs.getString("wait_event"));
            dto.setQuery(rs.getString("query"));
            
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
