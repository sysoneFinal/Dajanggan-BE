package com.dajanggan.domain.metric.batch.aggregator;

import com.dajanggan.domain.common.util.MetricCollectionUtils;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.infrastructure.datasource.DataSourceFactory;
import com.dajanggan.domain.system.memory.domain.MemoryAgg;
import com.dajanggan.domain.system.memory.domain.MemoryRaw;
import com.dajanggan.domain.system.memory.dto.agg1m.MemoryAgg1mDto;
import com.dajanggan.domain.system.memory.repository.MemoryMapper;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Memory 1분 집계 Batch Job
 * memory_raw 테이블에서 최근 1분간 데이터를 읽어서
 * 이전 Raw 데이터와 비교하여 memory_agg에 저장
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MemoryAgg1mAggregator {

    private final JobRepository jobRepository;
    private final DataSource dataSource;
    private final PlatformTransactionManager batchTransactionManager;
    private final MemoryMapper memoryMapper;
    private final InstanceRepository instanceRepository;
    private final DataSourceFactory dataSourceFactory;

    /**
     * Job 정의
     */
    @Bean
    public Job memoryAgg1mJob() {
        return new JobBuilder("memoryAgg1mJob", jobRepository)
                .start(memoryAgg1mStep())
                .build();
    }

    /**
     * Step 정의
     */
    @Bean
    public Step memoryAgg1mStep() {
        return new StepBuilder("memoryAgg1mStep", jobRepository)
                .<MemoryAgg1mDto, MemoryAgg>chunk(10, batchTransactionManager)
                .reader(memoryAgg1mReader())
                .processor(memoryAgg1mProcessor())
                .writer(memoryAgg1mWriter())
                .build();
    }

    /**
     * Reader: memory_raw에서 최근 1분간 데이터를 instance_id + relname + database_name 조합별로 최신 데이터만 읽기
     * 이미 memory_agg_1m에 저장된 데이터는 제외
     */
    @Bean
    public JdbcCursorItemReader<MemoryAgg1mDto> memoryAgg1mReader() {
        String sql = """
    SELECT DISTINCT ON (r.instance_id, COALESCE(r.relname, ''), COALESCE(r.database_name, ''))
        r.instance_id,
        r.collected_at,
        r.relname,
        r.database_name
    FROM memory_raw r
    WHERE r.collected_at >= DATE_TRUNC('minute', CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - INTERVAL '2 minutes'
      AND r.collected_at < DATE_TRUNC('minute', CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
      AND NOT EXISTS (
          SELECT 1 
          FROM memory_agg_1m a
          WHERE a.instance_id = r.instance_id
            AND COALESCE(a.relname, '') = COALESCE(r.relname, '')
            AND COALESCE(a.database_name, '') = COALESCE(r.database_name, '')
            AND a.collected_at = DATE_TRUNC('minute', r.collected_at)
      )
    ORDER BY r.instance_id, COALESCE(r.relname, ''), COALESCE(r.database_name, ''), r.collected_at DESC
    """;

        return new JdbcCursorItemReaderBuilder<MemoryAgg1mDto>()
                .name("memoryAgg1mReader")
                .dataSource(dataSource)
                .sql(sql)
                .rowMapper(new MemoryAgg1mDtoRowMapper())
                .build();
    }

    /**
     * Processor: 각 조합별로 이전 Raw 데이터와 비교하여 Agg 생성
     */
    @Bean
    public ItemProcessor<MemoryAgg1mDto, MemoryAgg> memoryAgg1mProcessor() {
        return dto -> {
            Long instanceId = dto.getInstanceId();
            OffsetDateTime collectedAt = dto.getCollectedAt();
            String relname = dto.getRelname();
            String databaseName = dto.getDatabaseName();

            // 최신 Raw 데이터 조회 (0이 아닌 실제 데이터만)
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
            String currentRawSql = """
                SELECT * FROM memory_raw
                WHERE instance_id = ?
                  AND COALESCE(relname, '') = COALESCE(?, '')
                  AND COALESCE(database_name, '') = COALESCE(?, '')
                  AND collected_at = ?
                  AND (heap_blks_read + heap_blks_hit + idx_blks_read + idx_blks_hit) > 0
                ORDER BY (heap_blks_read + heap_blks_hit + idx_blks_read + idx_blks_hit) DESC
                LIMIT 1
                """;
            
            List<MemoryRaw> currentRawList = jdbcTemplate.query(currentRawSql,
                    new org.springframework.jdbc.core.BeanPropertyRowMapper<>(MemoryRaw.class),
                    instanceId, relname, databaseName, collectedAt);
            
            if (currentRawList.isEmpty()) {
                log.warn("Raw 데이터 없음: instanceId={}, relname={}, databaseName={}, collectedAt={}",
                        instanceId, relname, databaseName, collectedAt);
                return null; // Skip 처리
            }
            
            MemoryRaw currentRaw = currentRawList.get(0);

            // 이전 Raw 데이터 조회 (현재 collected_at보다 이전 데이터만, relname + database_name별)
            List<MemoryRaw> previousRawList = memoryMapper.selectPreviousRawByRelname(instanceId, collectedAt);
            Map<String, MemoryRaw> previousRawMap = new HashMap<>();
            for (MemoryRaw prev : previousRawList) {
                String relnameKey = prev.getRelname() != null ? prev.getRelname() : "";
                String databaseKey = prev.getDatabaseName() != null ? prev.getDatabaseName() : "";
                String key = relnameKey + "|" + databaseKey;
                previousRawMap.put(key, prev);
            }

            String relnameKey = relname != null ? relname : "";
            String databaseKey = databaseName != null ? databaseName : "";
            String key = relnameKey + "|" + databaseKey;
            MemoryRaw previousRaw = previousRawMap.get(key);

            // 이전 데이터가 없으면 첫 번째 수집이므로 Agg 생성 스킵
            if (previousRaw == null) {
                log.debug("첫 번째 수집: 이전 데이터가 없어 집계 데이터 생성을 스킵합니다. instanceId={}, relname={}", instanceId, relname);
                return null; // Skip 처리
            }

            // 전체 버퍼 수 계산 (relname=NULL, database_name=NULL인 행에서 가져옴)
            Long totalBuffers = calculateTotalBuffers(instanceId, collectedAt);

            // collected_at을 분 단위로 정규화 (memory_agg_1m에 저장될 때도 분 단위로 저장됨)
            OffsetDateTime normalizedCollectedAt = collectedAt.truncatedTo(ChronoUnit.MINUTES);
            
            // 이미 해당 시간에 집계된 데이터가 있는지 확인
            org.springframework.jdbc.core.JdbcTemplate checkJdbcTemplate = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
            String checkSql = """
                SELECT COUNT(*) 
                FROM memory_agg_1m
                WHERE instance_id = ?
                  AND COALESCE(relname, '') = COALESCE(?, '')
                  AND COALESCE(database_name, '') = COALESCE(?, '')
                  AND collected_at = ?
                """;
            Long existingCount = checkJdbcTemplate.queryForObject(checkSql, Long.class,
                    instanceId, relname, databaseName, normalizedCollectedAt);
            
            if (existingCount != null && existingCount > 0) {
                log.debug("이미 집계된 데이터 존재: instanceId={}, relname={}, databaseName={}, collectedAt={}",
                        instanceId, relname, databaseName, normalizedCollectedAt);
                return null; // Skip 처리
            }
            
            // Agg 데이터 생성
            return calculateAggregation(currentRaw, previousRaw, normalizedCollectedAt, totalBuffers);
        };
    }

    /**
     * Writer: Agg 데이터 저장
     */
    @Bean
    public ItemWriter<MemoryAgg> memoryAgg1mWriter() {
        return (Chunk<? extends MemoryAgg> chunk) -> {
            List<MemoryAgg> items = new ArrayList<>();
            for (MemoryAgg item : chunk.getItems()) {
                if (item != null) {
                    items.add(item);
                }
            }

            if (!items.isEmpty()) {
                memoryMapper.insertAggBatch(items);
                log.info("Memory 1분 집계 완료: {} 건 저장", items.size());
            }
        };
    }

    /**
     * 전체 버퍼 수 계산 (shared_buffers 설정값 조회)
     */
    private Long calculateTotalBuffers(Long instanceId, OffsetDateTime collectedAt) {
        try {
            // Instance 정보 조회
            Instance instance = instanceRepository.findById(instanceId)
                    .orElseThrow(() -> new RuntimeException("인스턴스를 찾을 수 없습니다: " + instanceId));
            
            // Instance의 데이터소스에서 shared_buffers 조회
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate = 
                    dataSourceFactory.createJdbcTemplate(instance, "postgres");
            
            String sql = """
                SELECT pg_size_bytes(current_setting('shared_buffers')) / 8192 as total_buffers
                """;
            
            Long totalBuffers = jdbcTemplate.queryForObject(sql, Long.class);
            
            if (totalBuffers == null || totalBuffers <= 0) {
                log.warn("shared_buffers 설정값을 조회할 수 없습니다. instanceId={}. 기본값 8192를 사용합니다.", instanceId);
                return 8192L;
            }
            
            log.debug("shared_buffers 설정값 조회: instanceId={}, total_buffers={}", instanceId, totalBuffers);
            return totalBuffers;
            
        } catch (Exception e) {
            log.warn("shared_buffers 설정값 조회 실패: instanceId={}, {}. 기본값 8192를 사용합니다.", instanceId, e.getMessage());
            return 8192L;
        }
    }

    /**
     * MemoryAgg 객체 생성 (증분 계산) - 기존 MemoryCollectionScheduler의 calculateAggregation 로직 재사용
     */
    private MemoryAgg calculateAggregation(MemoryRaw current, MemoryRaw previous,
                                           OffsetDateTime collectedAt, Long totalBuffers) {
        // 버퍼 사용률
        double bufferUsagePct = totalBuffers > 0 ? 
                (double) current.getBuffers() / totalBuffers * 100 : 0.0;

        // Dirty 비율
        double dirtyRatio = current.getBuffers() > 0 ? 
                (double) current.getDirtyBuffers() / current.getBuffers() * 100 : 0.0;

        // I/O 증분 계산
        long deltaHeapBlksRead = 0;
        long deltaHeapBlksHit = 0;
        long deltaIdxBlksRead = 0;
        long deltaIdxBlksHit = 0;
        double cacheHitRatio = 0.0;
        
        // 임시 파일 및 I/O 시간 증분
        long deltaTempFiles = 0;
        long deltaTempBytes = 0;
        double deltaBlkReadTime = 0.0;
        double deltaBlkWriteTime = 0.0;
        double tempFileRate = 0.0;
        double tempBytesPerSec = 0.0;
        double avgIoWaitTimeMs = 0.0;

        if (previous != null) {
            // 공통 유틸리티 사용 (stats_reset 대응)
            deltaHeapBlksRead = MetricCollectionUtils.calculateSafeDelta(
                    current.getHeapBlksRead(), previous.getHeapBlksRead());
            deltaHeapBlksHit = MetricCollectionUtils.calculateSafeDelta(
                    current.getHeapBlksHit(), previous.getHeapBlksHit());
            deltaIdxBlksRead = MetricCollectionUtils.calculateSafeDelta(
                    current.getIdxBlksRead(), previous.getIdxBlksRead());
            deltaIdxBlksHit = MetricCollectionUtils.calculateSafeDelta(
                    current.getIdxBlksHit(), previous.getIdxBlksHit());

            // 캐시 히트율 = hit / (hit + read) * 100
            long totalHit = deltaHeapBlksHit + deltaIdxBlksHit;
            long totalRead = deltaHeapBlksRead + deltaIdxBlksRead;
            long totalAccess = totalHit + totalRead;
            cacheHitRatio = totalAccess > 0 ? (double) totalHit / totalAccess * 100 : 0.0;
            
            // 임시 파일 증분
            deltaTempFiles = MetricCollectionUtils.calculateSafeDelta(
                    current.getTempFiles(), previous.getTempFiles());
            deltaTempBytes = MetricCollectionUtils.calculateSafeDelta(
                    current.getTempBytes(), previous.getTempBytes());
            
            // I/O 대기 시간 증분
            deltaBlkReadTime = MetricCollectionUtils.calculateSafeDelta(
                    current.getBlkReadTime(), previous.getBlkReadTime());
            deltaBlkWriteTime = MetricCollectionUtils.calculateSafeDelta(
                    current.getBlkWriteTime(), previous.getBlkWriteTime());
            
            // 수집 간격(초) 계산
            long intervalSeconds = ChronoUnit.SECONDS.between(
                    previous.getCollectedAt(), current.getCollectedAt()
            );
            if (intervalSeconds <= 0) {
                intervalSeconds = 60;
            }
            
            // 초당 비율 계산
            tempFileRate = (double) deltaTempFiles / intervalSeconds;
            tempBytesPerSec = (double) deltaTempBytes / intervalSeconds;
            
            // 평균 I/O 대기 시간 (ms)
            // blk_read_time과 blk_write_time은 이미 밀리초 단위의 누적 시간
            // 읽기 블록 수로 나눠서 평균 계산 (쓰기 횟수는 별도로 추적하지 않음)
            long totalReadBlocks = deltaHeapBlksRead + deltaIdxBlksRead;
            if (totalReadBlocks > 0) {
                // 읽기 대기 시간의 평균 + 쓰기 대기 시간 (쓰기 횟수 없으므로 합계 사용)
                avgIoWaitTimeMs = (deltaBlkReadTime / totalReadBlocks) + (deltaBlkWriteTime > 0 ? deltaBlkWriteTime : 0.0);
            } else {
                // 읽기 블록이 없으면 쓰기 대기 시간만 사용
                avgIoWaitTimeMs = deltaBlkWriteTime > 0 ? deltaBlkWriteTime : 0.0;
            }
        }
        
        // 버퍼 재사용 점수 계산 (0~100)
        double bufferReuseScore = current.getAvgUsagecount() != null ? 
                Math.min(current.getAvgUsagecount() * 20, 100.0) : 0.0;

        // 상태 판정
        String status = determineStatus(bufferUsagePct, dirtyRatio, cacheHitRatio);

        return MemoryAgg.builder()
                .instanceId(current.getInstanceId())
                .collectedAt(collectedAt)
                .relname(current.getRelname())
                .relkind(current.getRelkind())
                .avgBuffers((long) current.getBuffers())
                .avgBufferUsagePct(bufferUsagePct)
                .avgDirtyRatio(dirtyRatio)
                .avgPinnedBuffers((double) current.getPinnedBuffers())
                .deltaHeapBlksRead(deltaHeapBlksRead)
                .deltaHeapBlksHit(deltaHeapBlksHit)
                .deltaIdxBlksRead(deltaIdxBlksRead)
                .deltaIdxBlksHit(deltaIdxBlksHit)
                .cacheHitRatio(cacheHitRatio)
                .avgUsagecount(current.getAvgUsagecount())
                .bufferReuseScore(bufferReuseScore)
                .databaseName(current.getDatabaseName())
                .deltaTempFiles(deltaTempFiles)
                .deltaTempBytes(deltaTempBytes)
                .tempFileRate(tempFileRate)
                .tempBytesPerSec(tempBytesPerSec)
                .avgIoWaitTimeMs(avgIoWaitTimeMs)
                .status(status)
                .build();
    }

    /**
     * 상태 판정 로직
     */
    private String determineStatus(double bufferUsagePct, double dirtyRatio, double cacheHitRatio) {
        if (bufferUsagePct < 80 && dirtyRatio < 10 && cacheHitRatio > 90) {
            return "정상";
        } else if (bufferUsagePct < 90 && dirtyRatio < 20 && cacheHitRatio > 80) {
            return "주의";
        } else {
            return "위험";
        }
    }

    /**
     * RowMapper: DB 결과를 DTO로 매핑
     */
    private static class MemoryAgg1mDtoRowMapper implements RowMapper<MemoryAgg1mDto> {
        @Override
        public MemoryAgg1mDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            MemoryAgg1mDto dto = new MemoryAgg1mDto();
            dto.setInstanceId(rs.getLong("instance_id"));

            java.sql.Timestamp collectedAtTs = rs.getTimestamp("collected_at");
            if (collectedAtTs != null) {
                dto.setCollectedAt(OffsetDateTime.ofInstant(
                        collectedAtTs.toInstant(),
                        ZoneOffset.UTC
                ));
            }

            dto.setRelname(rs.getString("relname"));
            dto.setDatabaseName(rs.getString("database_name"));

            return dto;
        }
    }
}


