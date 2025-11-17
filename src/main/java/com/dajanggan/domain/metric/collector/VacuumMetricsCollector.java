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

    /**
     * Vacuum 원시 지표 수집기
     */
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

            // Vacuum 메트릭 조회
            List<VacuumRawMetricDto> vacuumMetrics = getVacuumMetrics(jdbc, database, instance, collectedAt);

            log.info("🧹 [VACUUM] 쿼리 결과: {} 건", vacuumMetrics.size());

            // 첫 번째 데이터 샘플 출력
            if (!vacuumMetrics.isEmpty()) {
                VacuumRawMetricDto first = vacuumMetrics.get(0);
                log.info("🧹 [VACUUM] 샘플 데이터: table={}, schema={}, dead_tup={}, phase={}",
                        first.getTableName(), first.getSchemaName(),
                        first.getNDeadTup(), first.getSessionPhase());
            }

            if (vacuumMetrics.isEmpty()) {
                log.warn("🧹 [VACUUM] 수집된 데이터 없음 (dead_tup > 0인 테이블이 없음)");
                return;
            }

            // 원시 데이터 저장
            log.info("🧹 [VACUUM] MyBatis INSERT 호출 시작 - {} 건", vacuumMetrics.size());

            try {
                vacuumRawRepository.insertVacuumMetrics(vacuumMetrics);
                log.info("🧹 [VACUUM] ✅ MyBatis INSERT 완료");
            } catch (Exception e) {
                log.error("🧹 [VACUUM] ❌ MyBatis INSERT 실패", e);
                throw e;
            }

            // DB에 실제로 들어갔는지 확인
            try {
                Integer count = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM vacuum_raw_metrics WHERE collected_at = ?",
                        Integer.class,
                        collectedAt
                );
                log.info("🧹 [VACUUM] 저장 확인 - DB에 {} 건 존재 (collected_at={})", count, collectedAt);
            } catch (Exception e) {
                log.error("🧹 [VACUUM] 저장 확인 실패", e);
            }

            log.info("🧹 [VACUUM] [{}] Collected {} vacuum metrics for database: {} (instance: {}:{})",
                    collectedAt,
                    vacuumMetrics.size(),
                    database.getDatabaseName(),
                    instance.getHost(),
                    instance.getPort());

        } catch (Exception e) {
            log.error("🧹 [VACUUM] ❌ 수집 실패 - database: {}, error: {}",
                    database.getDatabaseName(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * pg_stat_progress_vacuum과 pg_stat_all_tables 조인하여 Vacuum 메트릭 조회
     */
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
                pv.num_dead_tuples
            FROM pg_stat_progress_vacuum pv
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
        )
        SELECT 
            ts.schemaname,
            ts.relname AS table_name,
            COALESCE(vp.session_phase, 'not_running') AS session_phase,
            COALESCE(vp.session_progress, 0) AS session_progress,
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
            ts.relsize_total_bytes
        FROM table_stats ts
        LEFT JOIN vacuum_progress vp ON ts.relid = vp.relid
        ORDER BY ts.n_dead_tup DESC
        LIMIT 1000
        """;

        log.debug("🧹 [VACUUM] SQL 실행: dead_tup > 0인 테이블 조회");
        List<VacuumRawMetricDto> result = jdbc.query(sql, new VacuumRawMetricRowMapper(database, instance, collectedAt));
        log.info("🧹 [VACUUM] 쿼리 결과: {} 건 조회됨", result.size());

        return result;
    }

    /**
     * RowMapper for VacuumRawMetricDto
     */
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
            dto.setSessionPhase(rs.getString("session_phase"));
            dto.setSessionProgress(rs.getDouble("session_progress"));

            Long heapBlksTotal = (Long) rs.getObject("heap_blks_total");
            dto.setHeapBlksTotal(heapBlksTotal);

            Long heapBlksScanned = (Long) rs.getObject("heap_blks_scanned");
            dto.setHeapBlksScanned(heapBlksScanned);

            Long heapBlksVacuumed = (Long) rs.getObject("heap_blks_vacuumed");
            dto.setHeapBlksVacuumed(heapBlksVacuumed);

            Long indexVacuumCount = (Long) rs.getObject("index_vacuum_count");
            dto.setIndexVacuumCount(indexVacuumCount);

            Long numDeadTuples = (Long) rs.getObject("num_dead_tuples");
            if (numDeadTuples != null) {
                dto.setTuplesDeleted(numDeadTuples);
            }

            // Tuple 정보
            dto.setNDeadTup(rs.getLong("n_dead_tup"));
            dto.setNLiveTup(rs.getLong("n_live_tup"));

            Long nModSinceAnalyze = (Long) rs.getObject("n_mod_since_analyze");
            dto.setNModSinceAnalyze(nModSinceAnalyze);

            // Vacuum 실행 여부 판단
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

            // 경과 시간 계산
            if (dto.getSessionStartedAt() != null) {
                long elapsedMs = collectedAt.toInstant().toEpochMilli()
                        - dto.getSessionStartedAt().toInstant().toEpochMilli();
                dto.setElapsedSeconds((double) elapsedMs / 1000.0);
            }

            // 테이블 크기
            dto.setRelsizeTotalBytes(rs.getLong("relsize_total_bytes"));

            dto.setCreatedAt(OffsetDateTime.now());

            return dto;
        }
    }
}