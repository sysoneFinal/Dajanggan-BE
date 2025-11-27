/** 작성자 : 서샘이 */
package com.dajanggan.domain.session.repository;

import com.dajanggan.domain.session.dto.raw.LockSessionDto;
import com.dajanggan.domain.session.dto.raw.SessionRawMetricDto;
import lombok.RequiredArgsConstructor;
import org.postgresql.core.BaseConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

    private final JdbcTemplate dajanganJdbcTemplate;

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
            a.xact_start,
            a.query_start,
            (pg_blocking_pids(a.pid))[1] AS blocking_pid,
            (SELECT usename FROM pg_stat_activity WHERE pid = (pg_blocking_pids(a.pid))[1]) AS blocking_username,
            (SELECT setting::int FROM pg_settings WHERE name = 'max_connections') as max_connections
        FROM pg_stat_activity a
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
     * PostgreSQL COPY 방식으로 원시 세션 데이터를 빠르게 저장
     */
    public void insertSessionMetricsCopy( List<SessionRawMetricDto> sessions) throws Exception {
        if (sessions.isEmpty()) return;

        // PostgreSQL COPY는 텍스트 형식 스트림 필요
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX");
        OffsetDateTime now = OffsetDateTime.now();

        for (SessionRawMetricDto s : sessions) {
            sb.append(s.getDatabaseId() != null ? s.getDatabaseId() : "\\N").append('\t')
                    .append(s.getInstanceId() != null ? s.getInstanceId() : "\\N").append('\t')
                    .append(s.getCollectedAt() != null ? s.getCollectedAt().format(formatter) : "\\N").append('\t')
                    .append(s.getPid() != null ? s.getPid() : "\\N").append('\t')
                    .append(escapeValue(s.getUsername())).append('\t')
                    .append(escapeValue(s.getDatabasename())).append('\t')
                    .append(escapeValue(s.getClientAddr())).append('\t')
                    .append(escapeValue(s.getApplicationName())).append('\t')
                    .append(escapeValue(s.getState())).append('\t')
                    .append(s.getMaxConnections() != null ? s.getMaxConnections() : "\\N").append('\t')
                    .append(escapeValue(s.getWaitEventType())).append('\t')
                    .append(escapeValue(s.getWaitEvent())).append('\t')
                    .append(escapeValue(s.getWaitClass())).append('\t')
                    .append(escapeValue(s.getBottleneckCause())).append('\t')
                    .append(escapeValue(s.getImpactLevel())).append('\t')
                    .append(s.getWaitDurationSec() != null ? s.getWaitDurationSec() : "\\N").append('\t')
                    .append(s.getBlockingPid() != null ? s.getBlockingPid() : "\\N").append('\t')
                    .append(escapeValue(s.getBlockingUsername())).append('\t')
                    .append(s.getXactStart() != null ? s.getXactStart().format(formatter) : "\\N").append('\t')
                    .append(escapeValue(s.getQueryType())).append('\t')
                    .append(escapeValue(s.getQuery())).append('\t')
                    .append(s.getQueryStart() != null ? s.getQueryStart().format(formatter) : "\\N").append('\t')
                    .append(s.getQueryAgeSec() != null ? s.getQueryAgeSec() : "\\N").append('\t')
                    .append(s.getCpuUsage() != null ? s.getCpuUsage() : "\\N").append('\t')
                    .append(s.getMemoryUsageMb() != null ? s.getMemoryUsageMb() : "\\N").append('\t')
                    .append(escapeValue(s.getLockType())).append('\t')
                    .append(s.getLockDurationMs() != null ? s.getLockDurationMs() : "\\N").append('\t')
                    .append(s.getIsDeadlock() != null && s.getIsDeadlock() ? "t" : "f").append('\t')
                    .append(now.format(formatter)) // created_at - 현재 시간
                    .append('\n');
        }

        // JDBC Connection 얻기 (try-with-resources로 안전하게 관리)
        try (Connection connection = dajanganJdbcTemplate.getDataSource().getConnection()) {
            BaseConnection baseConnection = connection.unwrap(BaseConnection.class);
            org.postgresql.copy.CopyManager copyManager = new org.postgresql.copy.CopyManager(baseConnection);
            String copySql = "COPY session_metrics_raw(" +
                    "database_id, instance_id, collected_at, pid, username, databasename, client_addr, " +
                    "application_name, state, max_connections, wait_event_type, wait_event, wait_class, " +
                    "bottleneck_cause, impact_level, wait_duration_sec, blocking_pid, blocking_username, " +
                    "xact_start, query_type, query, query_start, query_age_sec, " +
                    "cpu_usage, memory_usage_mb, lock_type, lock_duration_ms, is_deadlock, created_at" +
                    ") FROM STDIN WITH (FORMAT text, DELIMITER E'\\t', NULL '\\N')";

            copyManager.copyIn(copySql, new StringReader(sb.toString()));
        }
    }

    /**
     * COPY 방식에서 특수 문자(탭, 개행, 백슬래시)를 이스케이프하는 헬퍼
     */
    private String escapeValue(String value) {
        if (value == null) return "\\N";
        return value.replace("\\", "\\\\")
                .replace("\t", " ")
                .replace("\n", " ")
                .replace("\r", " ");
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
            Timestamp xactStartTs = rs.getTimestamp("xact_start");
            if (xactStartTs != null) {
                dto.setXactStart(OffsetDateTime.ofInstant(
                        xactStartTs.toInstant(),
                        ZoneId.systemDefault()
                ));
            }

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