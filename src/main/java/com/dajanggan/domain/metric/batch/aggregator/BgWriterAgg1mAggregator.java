package com.dajanggan.domain.metric.batch.aggregator;

import com.dajanggan.domain.common.util.MetricCollectionUtils;
import com.dajanggan.domain.engine.bgwriter.domain.BgWriterAgg1m;
import com.dajanggan.domain.engine.bgwriter.domain.BgWriterRaw;
import com.dajanggan.domain.engine.bgwriter.dto.agg1m.BgWriterAgg1mDto;
import com.dajanggan.domain.engine.bgwriter.repository.BgWriterMapper;
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
import java.util.ArrayList;
import java.util.List;

/**
 * BGWriter 1분 집계 Batch Job
 * bgwriter_raw 테이블에서 최근 1분간 데이터를 읽어서
 * 이전 Raw 데이터와 비교하여 bgwriter_agg_1m에 저장
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class BgWriterAgg1mAggregator {

    private final JobRepository jobRepository;
    private final DataSource dataSource;
    private final PlatformTransactionManager batchTransactionManager;
    private final BgWriterMapper bgWriterMapper;

    /**
     * Job 정의
     */
    @Bean
    public Job bgWriterAgg1mJob() {
        return new JobBuilder("bgWriterAgg1mJob", jobRepository)
                .start(bgWriterAgg1mStep())
                .build();
    }

    /**
     * Step 정의
     */
    @Bean
    public Step bgWriterAgg1mStep() {
        return new StepBuilder("bgWriterAgg1mStep", jobRepository)
                .<BgWriterAgg1mDto, BgWriterAgg1m>chunk(10, batchTransactionManager)
                .reader(bgWriterAgg1mReader())
                .processor(bgWriterAgg1mProcessor())
                .writer(bgWriterAgg1mWriter())
                .build();
    }

    /**
     * Reader: bgwriter_raw에서 최근 1분간 데이터를 instance_id별로 최신 데이터만 읽기
     */
    @Bean
    public JdbcCursorItemReader<BgWriterAgg1mDto> bgWriterAgg1mReader() {
        String sql = """
            SELECT DISTINCT ON (instance_id)
                instance_id,
                collected_at
            FROM bgwriter_raw
            WHERE collected_at >= DATE_TRUNC('minute', CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - INTERVAL '1 minute'
              AND collected_at < DATE_TRUNC('minute', CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
            ORDER BY instance_id, collected_at DESC
            """;

        return new JdbcCursorItemReaderBuilder<BgWriterAgg1mDto>()
                .name("bgWriterAgg1mReader")
                .dataSource(dataSource)
                .sql(sql)
                .rowMapper(new BgWriterAgg1mDtoRowMapper())
                .build();
    }

    /**
     * Processor: 각 인스턴스별로 이전 Raw 데이터와 비교하여 Agg 생성
     */
    @Bean
    public ItemProcessor<BgWriterAgg1mDto, BgWriterAgg1m> bgWriterAgg1mProcessor() {
        return dto -> {
            Long instanceId = dto.getInstanceId();
            OffsetDateTime collectedAt = dto.getCollectedAt();

            // 최신 Raw 데이터 조회
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
            String currentRawSql = """
                SELECT * FROM bgwriter_raw
                WHERE instance_id = ?
                  AND collected_at = ?
                LIMIT 1
                """;

            List<BgWriterRaw> currentRawList = jdbcTemplate.query(currentRawSql,
                    new org.springframework.jdbc.core.BeanPropertyRowMapper<>(BgWriterRaw.class),
                    instanceId, collectedAt);

            if (currentRawList.isEmpty()) {
                log.warn("Raw 데이터 없음: instanceId={}, collectedAt={}", instanceId, collectedAt);
                return null; // Skip 처리
            }

            BgWriterRaw currentRaw = currentRawList.get(0);

            // 이전 Raw 데이터 조회 (현재 collectedAt보다 이전 데이터)
            BgWriterRaw previousRaw = bgWriterMapper.selectPreviousRaw(instanceId, collectedAt);

            // Agg 데이터 생성 (previousRaw가 없어도 기본값으로 생성)
            return calculateAggregation1m(instanceId, collectedAt, currentRaw, previousRaw);
        };
    }

    /**
     * Writer: Agg 데이터 저장
     */
    @Bean
    public ItemWriter<BgWriterAgg1m> bgWriterAgg1mWriter() {
        return (Chunk<? extends BgWriterAgg1m> chunk) -> {
            List<BgWriterAgg1m> items = new ArrayList<>();
            for (BgWriterAgg1m item : chunk.getItems()) {
                if (item != null) {
                    items.add(item);
                }
            }

            if (!items.isEmpty()) {
                for (BgWriterAgg1m agg : items) {
                    bgWriterMapper.insertAgg1m(agg);
                }
                log.info("BGWriter 1분 집계 완료: {} 건 저장", items.size());
            }
        };
    }

    /**
     * BgWriterAgg1m 객체 생성 (증분 계산)
     */
    private BgWriterAgg1m calculateAggregation1m(Long instanceId, OffsetDateTime collectedAt,
                                                 BgWriterRaw current, BgWriterRaw previous) {
        // 증분 계산 (previousRaw가 없으면 delta는 0으로 처리)
        long deltaBuffersClean = 0L;
        long deltaBuffersBackend = 0L;
        long deltaBackendFsync = 0L;
        long deltaMaxwrittenClean = 0L;

        if (previous != null) {
            // 증분 계산 (음수 방어: stats_reset 발생 시 현재 값 사용)
            deltaBuffersClean = MetricCollectionUtils.calculateSafeDelta(
                    current.getBuffersClean(),
                    previous.getBuffersClean()
            );
            deltaBuffersBackend = MetricCollectionUtils.calculateSafeDelta(
                    current.getBuffersBackend(),
                    previous.getBuffersBackend()
            );
            deltaBackendFsync = MetricCollectionUtils.calculateSafeDelta(
                    current.getBuffersBackendFsync(),
                    previous.getBuffersBackendFsync()
            );
            deltaMaxwrittenClean = MetricCollectionUtils.calculateSafeDelta(
                    current.getMaxwrittenClean(),
                    previous.getMaxwrittenClean()
            );
        } else {
            log.debug("첫 번째 수집: previousRaw가 없어 delta를 0으로 설정. instanceId={}", instanceId);
        }

        // Backend Flush 비율 계산
        long totalBuffers = deltaBuffersClean + deltaBuffersBackend;
        double backendFlushRatio = 0.0;
        if (totalBuffers > 0) {
            backendFlushRatio = (100.0 * deltaBuffersBackend) / totalBuffers;
        }

        // Clean Rate 계산 (버퍼/초)
        double cleanRate = deltaBuffersClean / 60.0;

        // BGWriter 사이클 시간 계산 (밀리초)
        Double avgCycleTimeMs = null;
        if (deltaBuffersClean > 0) {
            double estimatedCycles = Math.max(1.0, deltaBuffersClean / 1000.0);
            avgCycleTimeMs = 60000.0 / estimatedCycles;
            if (avgCycleTimeMs > 60000.0) {
                avgCycleTimeMs = 60000.0;
            }
        }

        // 상태 판단 (Backend Flush 비율 + Maxwritten Clean 고려)
        String status = determineBgWriterStatus(backendFlushRatio, deltaMaxwrittenClean);

        return BgWriterAgg1m.builder()
                .instanceId(instanceId)
                .collectedAt(collectedAt)
                .avgBackendFlushRatio(backendFlushRatio)
                .avgCleanRate(cleanRate)
                .totalBuffersClean(deltaBuffersClean)
                .totalBuffersBackend(deltaBuffersBackend)
                .totalBackendFsync(deltaBackendFsync)
                .totalMaxwrittenClean(deltaMaxwrittenClean)
                .status(status)
                .avgCycleTimeMs(avgCycleTimeMs)
                .build();
    }

    /**
     * 상태 판정 로직
     */
    private String determineBgWriterStatus(double backendFlushRatio, long maxwrittenClean) {
        if (backendFlushRatio < 10 && maxwrittenClean == 0) {
            return "정상";
        } else if (backendFlushRatio < 30 && maxwrittenClean < 5) {
            return "주의";
        } else {
            return "위험";
        }
    }

    /**
     * RowMapper: DB 결과를 DTO로 매핑
     */
    private static class BgWriterAgg1mDtoRowMapper implements RowMapper<BgWriterAgg1mDto> {
        @Override
        public BgWriterAgg1mDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            BgWriterAgg1mDto dto = new BgWriterAgg1mDto();
            dto.setInstanceId(rs.getLong("instance_id"));

            java.sql.Timestamp collectedAtTs = rs.getTimestamp("collected_at");
            if (collectedAtTs != null) {
                dto.setCollectedAt(OffsetDateTime.ofInstant(
                        collectedAtTs.toInstant(),
                        ZoneOffset.UTC
                ));
            }

            return dto;
        }
    }
}