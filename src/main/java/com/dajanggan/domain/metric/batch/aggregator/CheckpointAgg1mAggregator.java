package com.dajanggan.domain.metric.batch.aggregator;

import com.dajanggan.domain.common.util.MetricCollectionUtils;
import com.dajanggan.domain.engine.checkpoint.domain.CheckpointAgg1m;
import com.dajanggan.domain.engine.checkpoint.domain.CheckpointRaw;
import com.dajanggan.domain.engine.checkpoint.dto.agg1m.CheckpointAgg1mDto;
import com.dajanggan.domain.engine.checkpoint.repository.CheckpointMapper;
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
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Checkpoint 1분 집계 Batch Job
 * checkpoint_raw 테이블에서 최근 1분간 데이터를 읽어서
 * 이전 Raw 데이터와 비교하여 checkpoint_agg_1m에 저장
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CheckpointAgg1mAggregator {

    private final JobRepository jobRepository;
    private final DataSource dataSource;
    private final PlatformTransactionManager batchTransactionManager;
    private final CheckpointMapper checkpointMapper;

    @Bean
    public Job checkpointAgg1mJob() {
        return new JobBuilder("checkpointAgg1mJob", jobRepository)
                .start(checkpointAgg1mStep())
                .build();
    }

    @Bean
    public Step checkpointAgg1mStep() {
        return new StepBuilder("checkpointAgg1mStep", jobRepository)
                .<CheckpointAgg1mDto, CheckpointAgg1m>chunk(10, batchTransactionManager)
                .reader(checkpointAgg1mReader())
                .processor(checkpointAgg1mProcessor())
                .writer(checkpointAgg1mWriter())
                .build();
    }

    @Bean
    public JdbcCursorItemReader<CheckpointAgg1mDto> checkpointAgg1mReader() {
        String sql = """
            SELECT DISTINCT ON (instance_id)
                instance_id,
                collected_at
            FROM checkpoint_raw
            WHERE collected_at >= DATE_TRUNC('minute', CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - INTERVAL '1 minute'
              AND collected_at < DATE_TRUNC('minute', CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
            ORDER BY instance_id, collected_at DESC
            """;

        return new JdbcCursorItemReaderBuilder<CheckpointAgg1mDto>()
                .name("checkpointAgg1mReader")
                .dataSource(dataSource)
                .sql(sql)
                .rowMapper(new CheckpointAgg1mDtoRowMapper())
                .build();
    }

    @Bean
    public ItemProcessor<CheckpointAgg1mDto, CheckpointAgg1m> checkpointAgg1mProcessor() {
        return dto -> {
            Long instanceId = dto.getInstanceId();
            OffsetDateTime collectedAt = dto.getCollectedAt();

            // 최신 Raw 데이터 조회
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
            String currentRawSql = """
                SELECT * FROM checkpoint_raw
                WHERE instance_id = ?
                  AND collected_at = ?
                LIMIT 1
                """;

            List<CheckpointRaw> currentRawList = jdbcTemplate.query(currentRawSql,
                    new org.springframework.jdbc.core.BeanPropertyRowMapper<>(CheckpointRaw.class),
                    instanceId, collectedAt);

            if (currentRawList.isEmpty()) {
                log.warn("Raw 데이터 없음: instanceId={}, collectedAt={}", instanceId, collectedAt);
                return null;
            }

            CheckpointRaw currentRaw = currentRawList.get(0);
            CheckpointRaw previousRaw = checkpointMapper.selectPreviousRaw(instanceId);

            // 첫 번째 수집 시에도 집계 데이터 생성 (previousRaw가 null이면 빈 집계 데이터 생성)
            if (previousRaw == null) {
                return CheckpointAgg1m.builder()
                        .instanceId(instanceId)
                        .collectedAt(collectedAt)
                        .avgCheckpointReqRatio(0.0)
                        .avgWriteTime(0.0)
                        .avgSyncTime(0.0)
                        .avgTotalTime(0.0)
                        .totalCheckpointsTimed(0L)
                        .totalCheckpointsReq(0L)
                        .totalWalBytes(BigDecimal.ZERO)
                        .totalBuffersCheckpoint(0L)
                        .status("정상")
                        .build();
            }

            return calculateAggregation1m(instanceId, collectedAt, currentRaw, previousRaw);
        };
    }

    @Bean
    public ItemWriter<CheckpointAgg1m> checkpointAgg1mWriter() {
        return (Chunk<? extends CheckpointAgg1m> chunk) -> {
            List<CheckpointAgg1m> items = new ArrayList<>();
            for (CheckpointAgg1m item : chunk.getItems()) {
                if (item != null) {
                    items.add(item);
                }
            }

            if (!items.isEmpty()) {
                for (CheckpointAgg1m agg : items) {
                    checkpointMapper.insertAgg1m(agg);
                }
                log.info("Checkpoint 1분 집계 완료: {} 건 저장", items.size());
            }
        };
    }

    private CheckpointAgg1m calculateAggregation1m(Long instanceId, OffsetDateTime collectedAt,
                                                   CheckpointRaw current, CheckpointRaw previous) {
        long deltaTimedCheckpoints = MetricCollectionUtils.calculateSafeDelta(
                current.getCheckpointsTimed(),
                previous.getCheckpointsTimed()
        );
        long deltaReqCheckpoints = MetricCollectionUtils.calculateSafeDelta(
                current.getCheckpointsReq(),
                previous.getCheckpointsReq()
        );
        double deltaWriteTime = MetricCollectionUtils.calculateSafeDelta(
                current.getCheckpointWriteTime(),
                previous.getCheckpointWriteTime()
        );
        double deltaSyncTime = MetricCollectionUtils.calculateSafeDelta(
                current.getCheckpointSyncTime(),
                previous.getCheckpointSyncTime()
        );
        long deltaBuffersCheckpoint = MetricCollectionUtils.calculateSafeDelta(
                current.getBuffersCheckpoint(),
                previous.getBuffersCheckpoint()
        );

        BigDecimal deltaWalBytes = BigDecimal.ZERO;
        if (current.getWalBytes() != null && previous.getWalBytes() != null) {
            long currentWalBytes = current.getWalBytes().longValue();
            long previousWalBytes = previous.getWalBytes().longValue();
            long delta = MetricCollectionUtils.calculateSafeDelta(currentWalBytes, previousWalBytes);
            deltaWalBytes = BigDecimal.valueOf(delta);
        }

        long totalCheckpoints = deltaTimedCheckpoints + deltaReqCheckpoints;

        if (totalCheckpoints == 0) {
            return CheckpointAgg1m.builder()
                    .instanceId(instanceId)
                    .collectedAt(collectedAt)
                    .avgCheckpointReqRatio(0.0)
                    .avgWriteTime(0.0)
                    .avgSyncTime(0.0)
                    .avgTotalTime(0.0)
                    .totalCheckpointsTimed(0L)
                    .totalCheckpointsReq(0L)
                    .totalWalBytes(deltaWalBytes)
                    .totalBuffersCheckpoint(deltaBuffersCheckpoint)
                    .status("정상")
                    .build();
        }

        double reqRatio = (100.0 * deltaReqCheckpoints) / totalCheckpoints;
        double avgWriteTime = (deltaWriteTime / 1000.0) / totalCheckpoints;
        double avgSyncTime = (deltaSyncTime / 1000.0) / totalCheckpoints;
        double avgTotalTime = avgWriteTime + avgSyncTime;
        String status = determineCheckpointStatus(avgTotalTime, reqRatio);

        return CheckpointAgg1m.builder()
                .instanceId(instanceId)
                .collectedAt(collectedAt)
                .avgCheckpointReqRatio(reqRatio)
                .avgWriteTime(avgWriteTime)
                .avgSyncTime(avgSyncTime)
                .avgTotalTime(avgTotalTime)
                .totalCheckpointsTimed(deltaTimedCheckpoints)
                .totalCheckpointsReq(deltaReqCheckpoints)
                .totalWalBytes(deltaWalBytes)
                .totalBuffersCheckpoint(deltaBuffersCheckpoint)
                .status(status)
                .build();
    }

    private String determineCheckpointStatus(double avgTotalTime, double reqRatio) {
        if (avgTotalTime < 1.0 && reqRatio < 10) {
            return "정상";
        } else if (avgTotalTime < 5.0 && reqRatio < 30) {
            return "주의";
        } else {
            return "위험";
        }
    }

    private static class CheckpointAgg1mDtoRowMapper implements RowMapper<CheckpointAgg1mDto> {
        @Override
        public CheckpointAgg1mDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            CheckpointAgg1mDto dto = new CheckpointAgg1mDto();
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



