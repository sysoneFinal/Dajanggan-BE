package com.dajanggan.domain.metric.collector;

import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.vacuum.dto.raw.VacuumRawMetricDto;
import com.dajanggan.domain.vacuum.repository.VacuumRawRepository;
import com.dajanggan.infrastructure.datasource.DataSourceFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Vacuum 메트릭 수집기 (Database 단위)
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class VacuumMetricsCollector {

    private final VacuumRawRepository vacuumRawRepository;
    private final DataSourceFactory dataSourceFactory;

    public void collect(Instance instance, Database database, OffsetDateTime collectedAt) {
        log.info("🧹 [VACUUM] ===== 수집 시작 ===== ");
        log.info("🧹 [VACUUM] DB: {}, Instance: {}:{}",
                database.getDatabaseName(), instance.getHost(), instance.getPort());

        JdbcTemplate jdbc;
        try {
            jdbc = dataSourceFactory.createJdbcTemplate(instance, database.getDatabaseName());
            log.info("🧹 [VACUUM] JdbcTemplate 생성 성공");
        } catch (Exception e) {
            log.error("🧹 [VACUUM] ❌ JdbcTemplate 생성 실패", e);
            throw e;
        }

        try {
            log.info("🧹 [VACUUM] 쿼리 실행 중...");

            List<VacuumRawMetricDto> vacuumMetrics = getVacuumMetrics(jdbc, database, instance, collectedAt);

            log.info("🧹 [VACUUM] 쿼리 결과: {} 건", vacuumMetrics.size());

            if (!vacuumMetrics.isEmpty()) {
                VacuumRawMetricDto first = vacuumMetrics.get(0);
                log.info("🧹 [VACUUM] 샘플 데이터: table={}, bloat_bytes={}, bloat_ratio={}",
                        first.getTableName(), first.getBloatBytes(), first.getBloatRatio());
            }

            if (vacuumMetrics.isEmpty()) {
                log.warn("🧹 [VACUUM] 수집된 데이터 없음");
                return;
            }

            vacuumRawRepository.insertVacuumMetrics(vacuumMetrics);
            log.info("🧹 [VACUUM] ✅ {} 건 저장 완료", vacuumMetrics.size());

        } catch (Exception e) {
            log.error("🧹 [VACUUM] ❌ 수집 실패: {}", e.getMessage(), e);
            throw e;
        }
    }

    // VacuumMetricsCollector.java - getVacuumMetrics 메서드의 SQL 수정
// VacuumMetricsCollector.java - getVacuumMetrics 메서드의 SQL 수정

    private List<VacuumRawMetricDto> getVacuumMetrics(
            JdbcTemplate jdbc,
            Database database,
            Instance instance,
            OffsetDateTime collectedAt) {

        String sql = """

                WITH vacuum_progress AS (
    SELECT 
        pv.relid,
        pv.phase AS session_phase,
        CASE 
            WHEN pv.heap_blks_total > 0 
            THEN (pv.heap_blks_scanned::NUMERIC / pv.heap_blks_total::NUMERIC * 100)
            ELSE 0 
        END AS session_progress,
        pv.heap_blks_total,
        pv.heap_blks_scanned,
        pv.heap_blks_vacuumed,
        pv.index_vacuum_count,
        pv.num_dead_tuples,
        pv.pid AS vacuum_pid,
        -- ✅ 실제 실행 시간: pg_stat_activity의 xact_start 기준
        EXTRACT(EPOCH FROM (NOW() - act.xact_start)) AS elapsed_seconds,
        -- ✅ autovacuum 여부 판단
        CASE WHEN act.query LIKE 'autovacuum:%' THEN true ELSE false END AS is_autovacuum
    FROM pg_stat_progress_vacuum pv
    JOIN pg_stat_activity act ON pv.pid = act.pid
),
table_stats AS (
    SELECT 
        st.relid,
        st.schemaname,
        st.relname,
        st.n_dead_tup,
        st.n_live_tup,
        st.n_mod_since_analyze,
        st.last_vacuum,
        st.last_autovacuum,
        pg_total_relation_size(st.relid) AS relsize_total_bytes
    FROM pg_stat_all_tables st
    WHERE st.schemaname NOT IN ('pg_catalog', 'information_schema')
      AND st.n_dead_tup > 0
),
xmin_horizon AS (
    SELECT
        MIN(xact_start) as oldest_xact,
        COALESCE(
            EXTRACT(EPOCH FROM (NOW() - MIN(xact_start))),
            0
        ) as xmin_horizon_age_seconds
    FROM pg_stat_activity
    WHERE xact_start IS NOT NULL
),
bloat_estimation AS (
       SELECT
           ts.relid,
           ts.relsize_total_bytes AS total_bytes,
           CASE
               WHEN ts.n_live_tup > 0 AND ts.n_dead_tup > 0
               THEN (
                   (ts.n_dead_tup::NUMERIC /
                    NULLIF(ts.n_live_tup + ts.n_dead_tup, 0)) *
                   ts.relsize_total_bytes
               )::BIGINT
               ELSE 0
           END AS bloat_bytes,
           CASE\s
               WHEN (ts.n_live_tup + ts.n_dead_tup) > 0
               THEN (ts.n_dead_tup::NUMERIC /
                     NULLIF(ts.n_live_tup + ts.n_dead_tup, 0))
               ELSE 0
           END AS bloat_ratio
       FROM table_stats ts
    ),
index_bloat AS (
    SELECT 
        i.indrelid AS table_oid,
        jsonb_agg(
            jsonb_build_object(
                'name', c.relname,
                'bytes', pg_relation_size(i.indexrelid),
                'ratio', 0.0
            )
        ) AS index_bloat_info
    FROM pg_index i
    JOIN pg_class c ON i.indexrelid = c.oid
    WHERE c.relkind = 'i'
    GROUP BY i.indrelid
),
blocked_tables AS (
    SELECT 
        l.relation::regclass::text AS table_name,
        l.pid AS blocker_pid,
        l.mode AS lock_mode,
        EXTRACT(EPOCH FROM (NOW() - act.xact_start))::INT AS blocked_seconds,
        act.state AS query_state
    FROM pg_locks l
    JOIN pg_stat_activity act ON l.pid = act.pid
    WHERE l.granted = false
      AND l.relation IS NOT NULL
),
autovacuum_settings AS (
    SELECT 
        c.oid::regclass::text AS table_name,
        COALESCE(
            (SELECT setting FROM pg_settings WHERE name = 'autovacuum_vacuum_cost_delay')::INT,
            20
        ) AS cost_delay_ms,
        COALESCE(
            (SELECT setting FROM pg_settings WHERE name = 'autovacuum_max_workers')::INT,
            3
        ) AS max_workers
    FROM pg_class c
    WHERE c.relkind = 'r'
),
wraparound_info AS (
    SELECT 
        c.oid::regclass::text AS table_name,
        age(c.relfrozenxid) AS age_current_xid,
        (SELECT setting::BIGINT FROM pg_settings WHERE name = 'autovacuum_freeze_max_age') AS age_max_freeze,
        (age(c.relfrozenxid)::NUMERIC / 
         NULLIF((SELECT setting::BIGINT FROM pg_settings WHERE name = 'autovacuum_freeze_max_age'), 0) * 100
        ) AS wraparound_progress
    FROM pg_class c
    WHERE c.relkind = 'r'
)
SELECT 
    ts.schemaname,
    ts.relname AS table_name,
    COALESCE(vp.session_phase, 'not_running') AS session_phase,
    COALESCE(vp.session_progress, 0) AS session_progress,
    (SELECT xmin_horizon_age_seconds FROM xmin_horizon) AS blocker_xmin_horizon,
    vp.heap_blks_total,
    vp.heap_blks_scanned,
    vp.heap_blks_vacuumed,
    vp.index_vacuum_count,
    vp.num_dead_tuples,
    ts.n_dead_tup,
    ts.n_live_tup,
    ts.n_mod_since_analyze,
    ts.last_vacuum,
    ts.last_autovacuum,
    ts.relsize_total_bytes,
    COALESCE(be.bloat_bytes, 0) AS bloat_bytes,
    COALESCE(be.bloat_ratio, 0.0) AS bloat_ratio,
    COALESCE(ib.index_bloat_info::TEXT, '[]') AS index_bloat_info,
    avs.cost_delay_ms AS autovacuum_cost_delay_ms,
    avs.max_workers,
    (SELECT COUNT(*) FROM pg_stat_progress_vacuum)::INT AS active_workers,
    bt.blocker_pid,
    bt.lock_mode AS blocker_lock_mode,
    bt.blocked_seconds,
    bt.query_state,
    CASE WHEN bt.blocker_pid IS NOT NULL THEN true ELSE false END AS is_blocked,
    wi.age_current_xid,
    wi.age_max_freeze,
    wi.wraparound_progress,
    -- ✅ 실행 중인 세션만 elapsed_seconds 값이 있음
    vp.elapsed_seconds,
    -- ✅ 실행 중인 세션의 autovacuum 여부
    COALESCE(vp.is_autovacuum, false) AS is_running_autovacuum
FROM table_stats ts
LEFT JOIN vacuum_progress vp ON ts.relid = vp.relid
LEFT JOIN bloat_estimation be ON ts.relid = be.relid
LEFT JOIN index_bloat ib ON ts.relid = ib.table_oid
LEFT JOIN autovacuum_settings avs ON ts.schemaname || '.' || ts.relname = avs.table_name
LEFT JOIN blocked_tables bt ON ts.schemaname || '.' || ts.relname = bt.table_name
LEFT JOIN wraparound_info wi ON ts.schemaname || '.' || ts.relname = wi.table_name
ORDER BY ts.n_dead_tup DESC
LIMIT 1000
""";

        log.debug("🧹 [VACUUM] SQL 실행: 실제 실행 시간 계산 포함");
        return jdbc.query(sql, new VacuumRawMetricRowMapper(database, instance, collectedAt));
    }

    private static class VacuumRawMetricRowMapper implements RowMapper<VacuumRawMetricDto> {
        private final Database database;
        private final Instance instance;
        private final OffsetDateTime collectedAt;

        public VacuumRawMetricRowMapper(Database database, Instance instance, OffsetDateTime collectedAt) {
            this.database = database;
            this.instance = instance;
            this.collectedAt = collectedAt;
        }

        @Override
        public VacuumRawMetricDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            VacuumRawMetricDto dto = new VacuumRawMetricDto();

            // 기본 정보
            dto.setDatabaseId(database.getDatabaseId());
            dto.setInstanceId(instance.getInstanceId());
            dto.setCollectedAt(collectedAt);
            dto.setSchemaName(rs.getString("schemaname"));
            dto.setTableName(rs.getString("table_name"));

            // Vacuum 진행 정보
            String sessionPhase = rs.getString("session_phase");
            dto.setSessionPhase(sessionPhase);
            dto.setSessionProgress(rs.getDouble("session_progress"));
            dto.setHeapBlksTotal((Long) rs.getObject("heap_blks_total"));
            dto.setHeapBlksScanned((Long) rs.getObject("heap_blks_scanned"));
            dto.setHeapBlksVacuumed((Long) rs.getObject("heap_blks_vacuumed"));
            dto.setIndexVacuumCount((Long) rs.getObject("index_vacuum_count"));

            java.math.BigDecimal xminHorizon = rs.getBigDecimal("blocker_xmin_horizon");
            if (xminHorizon != null) {
                dto.setBlockerXminHorizon(xminHorizon.longValue());
            }

            Long numDeadTuples = (Long) rs.getObject("num_dead_tuples");
            if (numDeadTuples != null) {
                dto.setTuplesDeleted(numDeadTuples);
            }

            // Tuple 정보
            dto.setNDeadTup(rs.getLong("n_dead_tup"));
            dto.setNLiveTup(rs.getLong("n_live_tup"));
            dto.setNModSinceAnalyze((Long) rs.getObject("n_mod_since_analyze"));

            // ✅ 실행 중인 세션 여부 확인
            boolean isRunning = sessionPhase != null && !"not_running".equals(sessionPhase);

            if (isRunning) {
                // 실행 중이면 pg_stat_activity에서 판단한 autovacuum 여부 사용
                boolean isRunningAutovacuum = rs.getBoolean("is_running_autovacuum");
                dto.setAutovacuum(isRunningAutovacuum);
                dto.setSessionTrigger(isRunningAutovacuum ? "autovacuum" : "manual");
                dto.setSessionStartedAt(collectedAt);

                // ✅ 실제 경과 시간 (실행 중인 세션만)
                Double elapsedSeconds = (Double) rs.getObject("elapsed_seconds");
                dto.setElapsedSeconds(elapsedSeconds);
            } else {
                // 실행 중이 아니면 마지막 vacuum 정보 기록
                var lastAutovacuum = rs.getTimestamp("last_autovacuum");
                var lastVacuum = rs.getTimestamp("last_vacuum");

                if (lastAutovacuum != null && (lastVacuum == null || lastAutovacuum.after(lastVacuum))) {
                    dto.setAutovacuum(true);
                    dto.setSessionTrigger("autovacuum");
                    dto.setSessionStartedAt(lastAutovacuum.toInstant().atOffset(collectedAt.getOffset()));
                } else if (lastVacuum != null) {
                    dto.setAutovacuum(false);
                    dto.setSessionTrigger("manual");
                    dto.setSessionStartedAt(lastVacuum.toInstant().atOffset(collectedAt.getOffset()));
                }

                // ✅ 실행 중이 아니면 elapsed_seconds는 NULL
                dto.setElapsedSeconds(null);
            }

            // 테이블 크기
            dto.setRelsizeTotalBytes(rs.getLong("relsize_total_bytes"));

            // Bloat 정보
            dto.setBloatBytes(rs.getLong("bloat_bytes"));
            dto.setBloatRatio(rs.getDouble("bloat_ratio"));
            dto.setIndexBloatInfo(rs.getString("index_bloat_info"));

            // 추가 필드
            dto.setAutovacuumCostDelayMs((Integer) rs.getObject("autovacuum_cost_delay_ms"));
            dto.setMaxWorkers((Integer) rs.getObject("max_workers"));
            dto.setActiveWorkers((Integer) rs.getObject("active_workers"));
            dto.setBlockerPid((Integer) rs.getObject("blocker_pid"));
            dto.setBlockerLockMode(rs.getString("blocker_lock_mode"));
            dto.setBlockedSeconds((Integer) rs.getObject("blocked_seconds"));
            dto.setQueryState(rs.getString("query_state"));
            dto.setIsBlocked(rs.getBoolean("is_blocked"));
            dto.setAgeCurrentXid((Long) rs.getObject("age_current_xid"));
            dto.setAgeMaxFreeze((Long) rs.getObject("age_max_freeze"));

            Double wraparoundProgress = rs.getDouble("wraparound_progress");
            if (!rs.wasNull()) {
                dto.setWraparoundProgress(wraparoundProgress);
            }

            dto.setCreatedAt(OffsetDateTime.now());

            return dto;
        }
    }
}