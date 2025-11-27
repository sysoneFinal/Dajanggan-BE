package com.dajanggan.domain.metric.collector;

import com.dajanggan.domain.event.detector.VacuumEventDetector;
import com.dajanggan.domain.event.dto.EventLog;
import com.dajanggan.domain.event.service.EventService;
import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.vacuum.dto.VacuumHistoryDto;
import com.dajanggan.domain.vacuum.dto.raw.VacuumRawMetricDto;
import com.dajanggan.domain.vacuum.repository.VacuumHistoryMapper;
import com.dajanggan.domain.vacuum.repository.VacuumRawRepository;
import com.dajanggan.infrastructure.datasource.DataSourceFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Vacuum 메트릭 수집기 (Database 단위)
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class VacuumMetricsCollector {

    private final VacuumRawRepository vacuumRawRepository;
    private final VacuumHistoryMapper vacuumHistoryMapper;
    private final DataSourceFactory dataSourceFactory;
    private final VacuumEventDetector vacuumEventDetector;
    private final EventService eventService;

    public void collect(Instance instance, Database database, String decryptedPassword, OffsetDateTime collectedAt) {
        log.info("🧹 [VACUUM] ===== 수집 시작 ===== ");
        log.info("🧹 [VACUUM] DB: {}, Instance: {}:{}",
                database.getDatabaseName(), instance.getHost(), instance.getPort());

        JdbcTemplate jdbc;
        try {
            jdbc = dataSourceFactory.createJdbcTemplate(instance, database.getDatabaseName(), decryptedPassword);
            log.info("🧹 [VACUUM] JdbcTemplate 생성 성공");
        } catch (Exception e) {
            log.error("🧹 [VACUUM] ❌ JdbcTemplate 생성 실패", e);
            throw e;
        }

        List<VacuumRawMetricDto> vacuumMetrics;
        try {
            log.info("🧹 [VACUUM] 쿼리 실행 중...");

            vacuumMetrics = getVacuumMetrics(jdbc, database, instance, collectedAt);

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

            // 완료된 vacuum 세션 감지 및 저장
            List<VacuumRawMetricDto> completedSessions = detectCompletedVacuumSessions(
                    database, instance, vacuumMetrics);

            if (!completedSessions.isEmpty()) {
                log.info("🧹 [VACUUM] 완료된 vacuum 세션 {} 건 감지", completedSessions.size());
                vacuumMetrics.addAll(completedSessions);
            }

            vacuumRawRepository.insertVacuumMetrics(vacuumMetrics);
            log.info("🧹 [VACUUM] ✅ {} 건 저장 완료 (실행 중: {}, 완료: {})",
                    vacuumMetrics.size(),
                    vacuumMetrics.size() - completedSessions.size(),
                    completedSessions.size());

        } catch (Exception e) {
            log.error("🧹 [VACUUM] ❌ 수집 실패: {}", e.getMessage(), e);
            throw e;
        }

        // 2. 전체 vacuum 대상으로 이벤트 감지
        List<EventLog> events = vacuumEventDetector.detectEvents(
                vacuumMetrics,
                database.getDatabaseId(),
                instance.getInstanceId(),
                database.getDatabaseName(),
                instance.getInstanceName()
        );

        // 3. 이벤트 저장
        eventService.saveEvents(events);
        log.info("🧹 [VACUUM] ✅ 이벤트 감지 및 저장 완료: {}건 (instance: {}, database: {})",
                events.size(), instance.getInstanceName(), database.getDatabaseName());
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
        -- ✅ 실제 실행 시간: pg_stat_activity의 xact_start 기준 (없으면 backend_start 사용)
        COALESCE(
            EXTRACT(EPOCH FROM (NOW() - act.xact_start)),
            EXTRACT(EPOCH FROM (NOW() - act.backend_start))
        ) AS elapsed_seconds,
        -- ✅ vacuum 세션의 transaction_age (차단이 없어도 실행 중인 vacuum의 트랜잭션 경과 시간)
        -- xact_start가 있으면 사용, 없으면 backend_start 사용 (autovacuum은 트랜잭션을 시작하지 않을 수 있음)
        CASE 
            WHEN act.xact_start IS NOT NULL 
            THEN EXTRACT(EPOCH FROM (NOW() - act.xact_start))::BIGINT 
            WHEN act.backend_start IS NOT NULL
            THEN EXTRACT(EPOCH FROM (NOW() - act.backend_start))::BIGINT
            ELSE NULL 
        END AS vacuum_transaction_age,
        -- ✅ autovacuum 여부 판단
        CASE WHEN act.query LIKE 'autovacuum:%' THEN true ELSE false END AS is_autovacuum
    FROM pg_stat_progress_vacuum pv
    JOIN pg_stat_activity act ON pv.pid = act.pid
),
                    table_stats AS (
      SELECT\s
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
      WHERE st.schemaname NOT IN ('pg_catalog', 'information_schema', 'pg_toast')  -- ✅ pg_toast 추가
        AND (st.n_dead_tup > 0 OR st.last_vacuum IS NOT NULL OR st.last_autovacuum IS NOT NULL)
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
                -- ✅ 테이블의 bloat ratio를 인덱스에도 적용
                -- table_stats 필터 조건 때문에 일부 테이블이 누락될 수 있으므로
                -- 직접 pg_stat_all_tables에서 조회하여 모든 테이블의 인덱스에 대해 계산
                'ratio', COALESCE(
                    (SELECT 
                        CASE 
                            WHEN st.n_live_tup + st.n_dead_tup > 0
                            THEN (st.n_dead_tup::NUMERIC / NULLIF(st.n_live_tup + st.n_dead_tup, 0))
                            ELSE 0.0
                        END
                     FROM pg_stat_all_tables st
                     WHERE st.relid = i.indrelid
                       AND st.schemaname NOT IN ('pg_catalog', 'information_schema', 'pg_toast')
                     LIMIT 1),
                    0.0
                )
            )
        ) AS index_bloat_info
    FROM pg_index i
    JOIN pg_class c ON i.indexrelid = c.oid
    JOIN pg_class tc ON i.indrelid = tc.oid
    JOIN pg_namespace ns ON tc.relnamespace = ns.oid
    WHERE c.relkind = 'i'
      AND tc.relkind = 'r'
      AND ns.nspname NOT IN ('pg_catalog', 'information_schema')
    GROUP BY i.indrelid
),
blocked_tables AS (
    SELECT DISTINCT
        waiting_l.relation::regclass::text AS table_name,
        blocker_act.pid AS blocker_pid,
        blocker_l.mode AS lock_mode,
        CASE 
            WHEN blocker_act.xact_start IS NOT NULL 
            THEN EXTRACT(EPOCH FROM (NOW() - blocker_act.xact_start))::INT 
            ELSE NULL 
        END AS blocked_seconds,
        CASE 
            WHEN blocker_act.xact_start IS NOT NULL 
            THEN EXTRACT(EPOCH FROM (NOW() - blocker_act.xact_start))::BIGINT 
            ELSE NULL 
        END AS transaction_age,
        blocker_act.state AS query_state,
        blocker_act.query AS blocker_query
    FROM pg_locks waiting_l
    JOIN pg_stat_activity waiting_act ON waiting_l.pid = waiting_act.pid
    CROSS JOIN LATERAL (
        SELECT unnest(pg_blocking_pids(waiting_l.pid)) AS blocker_pid
        WHERE pg_blocking_pids(waiting_l.pid) IS NOT NULL
          AND array_length(pg_blocking_pids(waiting_l.pid), 1) > 0
    ) blockers
    JOIN pg_stat_activity blocker_act ON blockers.blocker_pid = blocker_act.pid
    LEFT JOIN pg_locks blocker_l ON blocker_act.pid = blocker_l.pid
        AND blocker_l.relation = waiting_l.relation
        AND blocker_l.granted = true
    WHERE waiting_l.granted = false
      AND waiting_l.relation IS NOT NULL
),
autovacuum_settings AS (
    -- ✅ 인스턴스 레벨 설정이므로 테이블별로 저장할 필요 없음
    -- 단일 행으로 반환하여 모든 테이블에 동일한 값 적용
    SELECT 
        COALESCE(
            (SELECT setting FROM pg_settings WHERE name = 'autovacuum_vacuum_cost_delay')::INT,
            20
        ) AS cost_delay_ms,
        COALESCE(
            (SELECT setting FROM pg_settings WHERE name = 'autovacuum_max_workers')::INT,
            3
        ) AS max_workers
    LIMIT 1
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
    (SELECT cost_delay_ms FROM autovacuum_settings) AS autovacuum_cost_delay_ms,
    (SELECT max_workers FROM autovacuum_settings) AS max_workers,
    (SELECT COUNT(*) FROM pg_stat_progress_vacuum)::INT AS active_workers,
    bt.blocker_pid,
    bt.lock_mode AS blocker_lock_mode,
    bt.blocked_seconds,
    -- ✅ 차단이 있으면 차단 세션의 transaction_age, 없으면 실행 중인 vacuum 세션의 transaction_age
    COALESCE(bt.transaction_age, vp.vacuum_transaction_age) AS transaction_age,
    bt.query_state,
    bt.blocker_query,
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
-- ✅ autovacuum_settings는 인스턴스 레벨이므로 JOIN 불필요 (서브쿼리로 직접 참조)
LEFT JOIN blocked_tables bt ON ts.schemaname || '.' || ts.relname = bt.table_name
LEFT JOIN wraparound_info wi ON ts.schemaname || '.' || ts.relname = wi.table_name
ORDER BY ts.n_dead_tup DESC
LIMIT 1000
""";

        log.debug("🧹 [VACUUM] SQL 실행: 실제 실행 시간 계산 포함");
        return jdbc.query(sql, new VacuumRawMetricRowMapper(database, instance, collectedAt));
    }

    /**
     * 완료된 vacuum 세션 감지
     */
    private List<VacuumRawMetricDto> detectCompletedVacuumSessions(
            Database database,
            Instance instance,  // ✅ 파라미터 추가
            List<VacuumRawMetricDto> currentMetrics) {

        List<VacuumRawMetricDto> completedSessions = new ArrayList<>();

        try {
            List<Map<String, Object>> previousTimes =
                    vacuumRawRepository.findPreviousVacuumTimes(database.getDatabaseId());

            Map<String, Map<String, Object>> previousMap = previousTimes.stream()
                    .collect(Collectors.toMap(
                            m -> (String) m.get("table_name"),
                            m -> m,
                            (v1, v2) -> v1
                    ));

            for (VacuumRawMetricDto current : currentMetrics) {
                if (current.getTableName() == null) continue;

                Map<String, Object> previous = previousMap.get(current.getTableName());
                if (previous == null) continue;

                Timestamp prevLastVacuumTs = (Timestamp) previous.get("last_vacuum");
                Timestamp prevLastAutovacuumTs = (Timestamp) previous.get("last_autovacuum");
                OffsetDateTime prevLastVacuum = prevLastVacuumTs != null ?
                        prevLastVacuumTs.toInstant().atOffset(ZoneOffset.UTC) : null;
                OffsetDateTime prevLastAutovacuum = prevLastAutovacuumTs != null ?
                        prevLastAutovacuumTs.toInstant().atOffset(ZoneOffset.UTC) : null;

                OffsetDateTime currLastVacuum = current.getLastVacuum();
                OffsetDateTime currLastAutovacuum = current.getLastAutovacuum();

                // last_autovacuum이 변경된 경우
                if (currLastAutovacuum != null &&
                        (prevLastAutovacuum == null || currLastAutovacuum.isAfter(prevLastAutovacuum))) {

                    VacuumRawMetricDto completed = createCompletedSessionDto(current, true, currLastAutovacuum);
                    completedSessions.add(completed);

                    // ✅ vacuum_history에 INSERT
                    saveVacuumHistory(database, instance, current, "autovacuum", currLastAutovacuum);

                    log.debug("🧹 [VACUUM] 완료된 autovacuum 감지: table={}, completed_at={}",
                            current.getTableName(), currLastAutovacuum);
                }

                // last_vacuum이 변경된 경우
                if (currLastVacuum != null &&
                        (prevLastVacuum == null || currLastVacuum.isAfter(prevLastVacuum)) &&
                        (currLastAutovacuum == null || currLastVacuum.isAfter(currLastAutovacuum))) {

                    VacuumRawMetricDto completed = createCompletedSessionDto(current, false, currLastVacuum);
                    completedSessions.add(completed);

                    // ✅ vacuum_history에 INSERT
                    saveVacuumHistory(database, instance, current, "vacuum", currLastVacuum);

                    log.debug("🧹 [VACUUM] 완료된 manual vacuum 감지: table={}, completed_at={}",
                            current.getTableName(), currLastVacuum);
                }
            }
        } catch (Exception e) {
            log.error("🧹 [VACUUM] 완료된 세션 감지 중 오류 발생", e);
        }

        return completedSessions;
    }

    /**
     * ✅ vacuum_history 테이블에 저장
     */
    private void saveVacuumHistory(
            Database database,
            Instance instance,
            VacuumRawMetricDto current,
            String vacuumType,
            OffsetDateTime executedAt) {

        try {
            // 상태 판단
            String status = "정상";
            if (current.getBloatRatio() != null && current.getBloatRatio() > 0.05) {
                status = "주의";
            } else if (current.getNDeadTup() != null && current.getNDeadTup() > 100_000) {
                status = "주의";
            }

            // 마지막 vacuum 세션의 duration 조회
            Integer durationSeconds = vacuumRawRepository.findLastVacuumDuration(
                    database.getDatabaseId(),
                    current.getTableName()
            );

            VacuumHistoryDto.Entity history = VacuumHistoryDto.Entity.builder()
                    .databaseId(database.getDatabaseId())
                    .instanceId(instance.getInstanceId())
                    .tableName(current.getTableName())
                    .schemaName(current.getSchemaName() != null ? current.getSchemaName() : "public")
                    .vacuumType(vacuumType)
                    .executedAt(executedAt)
                    .durationSeconds(durationSeconds)
                    .deadTuplesBefore(current.getNDeadTup())
                    .bloatRatioBefore(current.getBloatRatio())
                    .status(status)
                    .build();

            vacuumHistoryMapper.insertVacuumHistory(history);

            log.debug("🧹 [VACUUM] vacuum_history INSERT: table={}, type={}, duration={}s",
                    current.getTableName(), vacuumType, durationSeconds);

        } catch (Exception e) {
            log.error("🧹 [VACUUM] vacuum_history INSERT 실패: {}", e.getMessage());
        }
    }

    /**
     * 완료된 vacuum 세션 DTO 생성
     */
    private VacuumRawMetricDto createCompletedSessionDto(
            VacuumRawMetricDto base,
            boolean isAutovacuum,
            OffsetDateTime completedAt) {

        VacuumRawMetricDto dto = new VacuumRawMetricDto();

        // 기본 정보 복사
        dto.setDatabaseId(base.getDatabaseId());
        dto.setInstanceId(base.getInstanceId());
        dto.setCollectedAt(base.getCollectedAt());
        dto.setTableName(base.getTableName());
        dto.setSchemaName(base.getSchemaName());

        // 완료된 세션 정보
        dto.setSessionPhase("completed");
        dto.setSessionProgress(100.0);
        dto.setAutovacuum(isAutovacuum);
        dto.setSessionTrigger(isAutovacuum ? "autovacuum" : "manual");
        dto.setSessionStartedAt(completedAt);
        dto.setElapsedSeconds(null); // 완료 시간은 알 수 없음

        // 현재 상태 정보 복사
        dto.setNDeadTup(base.getNDeadTup());
        dto.setNLiveTup(base.getNLiveTup());
        dto.setNModSinceAnalyze(base.getNModSinceAnalyze());
        dto.setRelsizeTotalBytes(base.getRelsizeTotalBytes());
        dto.setBloatBytes(base.getBloatBytes());
        dto.setBloatRatio(base.getBloatRatio());
        dto.setLastVacuum(base.getLastVacuum());
        dto.setLastAutovacuum(base.getLastAutovacuum());

        dto.setCreatedAt(OffsetDateTime.now());

        return dto;
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

            // ✅ last_vacuum과 last_autovacuum을 항상 DTO에 설정
            var lastAutovacuum = rs.getTimestamp("last_autovacuum");
            var lastVacuum = rs.getTimestamp("last_vacuum");

            if (lastVacuum != null) {
                dto.setLastVacuum(lastVacuum.toInstant().atOffset(collectedAt.getOffset()));
            }
            if (lastAutovacuum != null) {
                dto.setLastAutovacuum(lastAutovacuum.toInstant().atOffset(collectedAt.getOffset()));
            }

            // ✅ 실행 중인 세션 여부 확인
            boolean isRunning = sessionPhase != null && !"not_running".equals(sessionPhase);
            
            // ✅ elapsed_seconds는 모든 경우에 가져오기 (디버깅용)
            Double elapsedSeconds = (Double) rs.getObject("elapsed_seconds");

            if (isRunning) {
                // 실행 중이면 pg_stat_activity에서 판단한 autovacuum 여부 사용
                boolean isRunningAutovacuum = rs.getBoolean("is_running_autovacuum");
                dto.setAutovacuum(isRunningAutovacuum);
                dto.setSessionTrigger(isRunningAutovacuum ? "autovacuum" : "manual");
                dto.setSessionStartedAt(collectedAt);

                // ✅ 실제 경과 시간 (실행 중인 세션만)
                dto.setElapsedSeconds(elapsedSeconds);
            } else {
                // 실행 중이 아니면 마지막 vacuum 정보 기록
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
            
            // blocked_seconds와 transaction_age 디버깅
            Integer blockedSeconds = (Integer) rs.getObject("blocked_seconds");
            Long transactionAge = (Long) rs.getObject("transaction_age");
            
            // 디버깅: 모든 경우에 로그 출력 (sessionPhase와 elapsedSeconds는 이미 위에서 선언됨)
            if (dto.getTableName() != null) {
                log.debug("🔍 [VACUUM] table={}, session_phase={}, elapsed_seconds={}, transaction_age={}, blocked_seconds={}, blocker_pid={}", 
                        dto.getTableName(), sessionPhase, elapsedSeconds, transactionAge, blockedSeconds, dto.getBlockerPid());
            }
            
            dto.setBlockedSeconds(blockedSeconds);
            dto.setTransactionAge(transactionAge);
            dto.setQueryState(rs.getString("query_state"));
            dto.setBlockerQuery(rs.getString("blocker_query"));
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