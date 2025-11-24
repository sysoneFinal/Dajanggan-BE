package com.dajanggan.domain.metric.batch.aggregator;

import com.dajanggan.domain.common.util.MetricCollectionUtils;
import com.dajanggan.domain.system.disk.domain.DiskIoAgg;
import com.dajanggan.domain.system.disk.domain.DiskIoRaw;
import com.dajanggan.domain.system.disk.dto.agg1m.DiskIoAgg1mDto;
import com.dajanggan.domain.system.disk.repository.DiskIoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Disk I/O 1분 집계 Batch Job
 * disk_io_raw 테이블에서 최근 1분간 데이터를 읽어서
 * 이전 Raw 데이터와 비교하여 disk_io_agg에 저장
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DiskIoAgg1mAggregator {

    private final JobRepository jobRepository;
    private final DataSource dataSource;
    private final PlatformTransactionManager batchTransactionManager;
    private final DiskIoMapper diskIoMapper;

    /**
     * Job 정의
     */
    @Bean
    public Job diskIoAgg1mJob() {
        return new JobBuilder("diskIoAgg1mJob", jobRepository)
                .start(diskIoAgg1mStep())
                .build();
    }

    /**
     * Step 정의
     */
    @Bean
    public Step diskIoAgg1mStep() {
        return new StepBuilder("diskIoAgg1mStep", jobRepository)
                .<DiskIoAgg1mDto, DiskIoAgg>chunk(10, batchTransactionManager)
                .reader(diskIoAgg1mReader())
                .processor(diskIoAgg1mProcessor())
                .writer(diskIoAgg1mWriter())
                .build();
    }

    /**
     * Reader: disk_io_raw에서 최근 1분간 데이터를 instance_id + backend_type + database_name 조합별로 최신 데이터만 읽기
     */
    @Bean
    public JdbcCursorItemReader<DiskIoAgg1mDto> diskIoAgg1mReader() {
        String sql = """
            SELECT DISTINCT ON (instance_id, backend_type, COALESCE(database_name, ''))
                instance_id,
                collected_at,
                backend_type,
                database_name
            FROM disk_io_raw
            WHERE collected_at >= DATE_TRUNC('minute', CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - INTERVAL '1 minute'
              AND collected_at < DATE_TRUNC('minute', CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
            ORDER BY instance_id, backend_type, COALESCE(database_name, ''), collected_at DESC
            """;

        return new JdbcCursorItemReaderBuilder<DiskIoAgg1mDto>()
                .name("diskIoAgg1mReader")
                .dataSource(dataSource)
                .sql(sql)
                .rowMapper(new DiskIoAgg1mDtoRowMapper())
                .build();
    }

    /**
     * Processor: 각 조합별로 이전 Raw 데이터와 비교하여 Agg 생성
     */
    @Bean
    public ItemProcessor<DiskIoAgg1mDto, DiskIoAgg> diskIoAgg1mProcessor() {
        return dto -> {
            Long instanceId = dto.getInstanceId();
            OffsetDateTime collectedAt = dto.getCollectedAt();
            String backendType = dto.getBackendType();
            String databaseName = dto.getDatabaseName();

            // 최신 Raw 데이터 조회 (Reader에서 읽은 정보로 직접 조회)
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
            String currentRawSql = """
                SELECT * FROM disk_io_raw
                WHERE instance_id = ?
                  AND backend_type = ?
                  AND COALESCE(database_name, '') = COALESCE(?, '')
                  AND collected_at = ?
                LIMIT 1
                """;

            List<DiskIoRaw> currentRawList = jdbcTemplate.query(currentRawSql,
                    new org.springframework.jdbc.core.BeanPropertyRowMapper<>(DiskIoRaw.class),
                    instanceId, backendType, databaseName, collectedAt);

            if (currentRawList.isEmpty()) {
                log.warn("Raw 데이터 없음: instanceId={}, backendType={}, databaseName={}, collectedAt={}",
                        instanceId, backendType, databaseName, collectedAt);
                return null; // Skip 처리
            }

            DiskIoRaw currentRaw = currentRawList.get(0);

            // 이전 Raw 데이터 조회 (backend_type별)
            List<DiskIoRaw> previousRawList = diskIoMapper.selectPreviousRawByBackendType(instanceId, collectedAt);
            Map<String, DiskIoRaw> previousRawMap = new HashMap<>();
            for (DiskIoRaw prev : previousRawList) {
                String key = prev.getBackendType() + "_" + (prev.getDatabaseName() != null ? prev.getDatabaseName() : "");
                previousRawMap.put(key, prev);
            }

            String key = backendType + "_" + (databaseName != null ? databaseName : "");
            DiskIoRaw previousRaw = previousRawMap.get(key);

            // 이전 데이터가 없으면 첫 번째 수집이므로 Agg 생성 스킵
            if (previousRaw == null) {
                log.debug("첫 번째 수집: 이전 데이터가 없어 집계 데이터 생성을 스킵합니다. instanceId={}, backendType={}", instanceId, backendType);
                return null; // Skip 처리
            }

            // Agg 데이터 생성
            return calculateAggregation(currentRaw, previousRaw, collectedAt);
        };
    }

    /**
     * Writer: Agg 데이터 저장
     */
    @Bean
    public ItemWriter<DiskIoAgg> diskIoAgg1mWriter() {
        return (Chunk<? extends DiskIoAgg> chunk) -> {
            List<DiskIoAgg> items = new ArrayList<>();
            for (DiskIoAgg item : chunk.getItems()) {
                if (item != null) {
                    items.add(item);
                }
            }

            if (!items.isEmpty()) {
                diskIoMapper.insertAggBatch(items);
                log.info("Disk I/O 1분 집계 완료: {} 건 저장", items.size());
            }
        };
    }

    /**
     * DiskIoAgg 객체 생성 (증분 계산)
     */
    private DiskIoAgg calculateAggregation(DiskIoRaw current, DiskIoRaw previous,
                                           OffsetDateTime collectedAt) {
        // 증분 계산 (stats_reset 대응을 위한 안전한 계산)
        long deltaReads = MetricCollectionUtils.calculateSafeDelta(
                current.getReads(), previous.getReads());
        double deltaReadTime = MetricCollectionUtils.calculateSafeDelta(
                current.getReadTime(), previous.getReadTime());
        long deltaWrites = MetricCollectionUtils.calculateSafeDelta(
                current.getWrites(), previous.getWrites());
        double deltaWriteTime = MetricCollectionUtils.calculateSafeDelta(
                current.getWriteTime(), previous.getWriteTime());
        long deltaWritebacks = MetricCollectionUtils.calculateSafeDelta(
                current.getWritebacks(), previous.getWritebacks());
        long deltaExtendCount = MetricCollectionUtils.calculateSafeDelta(
                current.getExtendCount(), previous.getExtendCount());
        long deltaHits = MetricCollectionUtils.calculateSafeDelta(
                current.getHits(), previous.getHits());
        long deltaEvictions = MetricCollectionUtils.calculateSafeDelta(
                current.getEvictions(), previous.getEvictions());
        long deltaFsyncs = MetricCollectionUtils.calculateSafeDelta(
                current.getFsyncs(), previous.getFsyncs());
        double deltaFsyncTime = MetricCollectionUtils.calculateSafeDelta(
                current.getFsyncTime(), previous.getFsyncTime());

        // pg_stat_database 메트릭 증분
        long deltaBlksRead = MetricCollectionUtils.calculateSafeDelta(
                current.getBlksRead(), previous.getBlksRead());
        long deltaBlksHit = MetricCollectionUtils.calculateSafeDelta(
                current.getBlksHit(), previous.getBlksHit());

        // pg_stat_bgwriter 메트릭 증분
        long deltaBuffersBackendFsync = MetricCollectionUtils.calculateSafeDelta(
                current.getBuffersBackendFsync(), previous.getBuffersBackendFsync());
        long deltaBuffersCheckpoint = MetricCollectionUtils.calculateSafeDelta(
                current.getBuffersCheckpoint(), previous.getBuffersCheckpoint());
        long deltaBuffersClean = MetricCollectionUtils.calculateSafeDelta(
                current.getBuffersClean(), previous.getBuffersClean());
        long deltaBuffersBackend = MetricCollectionUtils.calculateSafeDelta(
                current.getBuffersBackend(), previous.getBuffersBackend());

        // 평균 레이턴시 계산
        double avgReadLatency = deltaReads > 0 ? deltaReadTime / deltaReads : 0.0;
        double avgWriteLatency = deltaWrites > 0 ? deltaWriteTime / deltaWrites : 0.0;

        // 읽기/쓰기 비율
        double readWriteRatio = deltaWrites > 0 ?
                (double) deltaReads / deltaWrites : 0.0;

        // 캐시 히트율 (pg_stat_io) = hits / (hits + reads) * 100
        long totalAccess = deltaHits + deltaReads;
        double cacheHitRatio = totalAccess > 0 ?
                (double) deltaHits / totalAccess * 100 : 0.0;

        // Buffer Hit Ratio (pg_stat_database) = blks_hit / (blks_hit + blks_read) * 100
        long totalBlocks = deltaBlksHit + deltaBlksRead;
        double bufferHitRatio = totalBlocks > 0 ?
                (double) deltaBlksHit / totalBlocks * 100 : 0.0;

        // Backend Fsync Rate 계산 (초당 fsync 수)
        long intervalSeconds = ChronoUnit.SECONDS.between(
                previous.getCollectedAt(), current.getCollectedAt()
        );
        if (intervalSeconds <= 0) {
            intervalSeconds = 60;
        }
        double backendFsyncRate = (double) deltaBuffersBackendFsync / intervalSeconds;

        // 상태 판정 (Buffer Hit Ratio 우선 사용)
        String status = determineStatus(avgReadLatency, avgWriteLatency,
                bufferHitRatio > 0 ? bufferHitRatio : cacheHitRatio);

        return DiskIoAgg.builder()
                .instanceId(current.getInstanceId())
                .collectedAt(collectedAt)
                .backendType(current.getBackendType())
                .databaseName(current.getDatabaseName())
                .deltaReads(deltaReads)
                .deltaReadTime(deltaReadTime)
                .deltaWrites(deltaWrites)
                .deltaWriteTime(deltaWriteTime)
                .deltaWritebacks(deltaWritebacks)
                .deltaExtendCount(deltaExtendCount)
                .deltaHits(deltaHits)
                .deltaEvictions(deltaEvictions)
                .deltaFsyncs(deltaFsyncs)
                .deltaFsyncTime(deltaFsyncTime)
                .deltaBlksRead(deltaBlksRead)
                .deltaBlksHit(deltaBlksHit)
                .avgReadLatencyMs(avgReadLatency)
                .avgWriteLatencyMs(avgWriteLatency)
                .readWriteRatio(readWriteRatio)
                .cacheHitRatio(cacheHitRatio)
                .bufferHitRatio(bufferHitRatio)
                .deltaBuffersBackendFsync(deltaBuffersBackendFsync)
                .deltaBuffersCheckpoint(deltaBuffersCheckpoint)
                .deltaBuffersClean(deltaBuffersClean)
                .deltaBuffersBackend(deltaBuffersBackend)
                .backendFsyncRate(backendFsyncRate)
                .status(status)
                .build();
    }

    /**
     * 상태 판정 로직
     */
    private String determineStatus(double avgReadLatency, double avgWriteLatency, double hitRatio) {
        double maxLatency = Math.max(avgReadLatency, avgWriteLatency);

        if (maxLatency < 10 && hitRatio > 90) {
            return "정상";
        } else if (maxLatency < 50 && hitRatio > 80) {
            return "주의";
        } else {
            return "위험";
        }
    }

    /**
     * RowMapper: DB 결과를 DTO로 매핑
     */
    private static class DiskIoAgg1mDtoRowMapper implements RowMapper<DiskIoAgg1mDto> {
        @Override
        public DiskIoAgg1mDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            DiskIoAgg1mDto dto = new DiskIoAgg1mDto();
            dto.setInstanceId(rs.getLong("instance_id"));

            java.sql.Timestamp collectedAtTs = rs.getTimestamp("collected_at");
            if (collectedAtTs != null) {
                dto.setCollectedAt(OffsetDateTime.ofInstant(
                        collectedAtTs.toInstant(),
                        ZoneOffset.UTC
                ));
            }

            dto.setBackendType(rs.getString("backend_type"));
            dto.setDatabaseName(rs.getString("database_name"));

            return dto;
        }
    }
}