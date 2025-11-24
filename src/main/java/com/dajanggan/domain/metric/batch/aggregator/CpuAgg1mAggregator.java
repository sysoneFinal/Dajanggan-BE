package com.dajanggan.domain.metric.batch.aggregator;

import com.dajanggan.domain.common.util.MetricCollectionUtils;
import com.dajanggan.domain.system.cpu.domain.CpuAgg;
import com.dajanggan.domain.system.cpu.domain.CpuRaw;
import com.dajanggan.domain.system.cpu.dto.agg1m.CpuAgg1mDto;
import com.dajanggan.domain.system.cpu.repository.CpuMapper;
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
import java.util.List;

/**
 * CPU 1분 집계 Batch Job
 * cpu_raw 테이블에서 최근 1분간 데이터를 읽어서
 * 이전 Raw 데이터와 비교하여 cpu_agg_1m에 저장
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CpuAgg1mAggregator {

    private final JobRepository jobRepository;
    private final DataSource dataSource;
    private final PlatformTransactionManager batchTransactionManager;
    private final CpuMapper cpuMapper;

    /**
     * Job 정의
     */
    @Bean
    public Job cpuAgg1mJob() {
        return new JobBuilder("cpuAgg1mJob", jobRepository)
                .start(cpuAgg1mStep())
                .build();
    }

    /**
     * Step 정의
     */
    @Bean
    public Step cpuAgg1mStep() {
        return new StepBuilder("cpuAgg1mStep", jobRepository)
                .<CpuAgg1mDto, CpuAgg>chunk(10, batchTransactionManager)
                .reader(cpuAgg1mReader())
                .processor(cpuAgg1mProcessor())
                .writer(cpuAgg1mWriter())
                .build();
    }

    /**
     * Reader: cpu_raw에서 최근 1분간 데이터를 instance_id별로 최신 데이터만 읽기
     * 
     * 수정: 수집 스케줄러(매분 5초)가 저장한 현재 분 데이터를 집계 스케줄러(매분 10초)가 읽도록 변경
     * - 이전: 현재 시각 기준 1분 전 데이터 읽음 (15:24:10 실행 시 15:23:00 데이터)
     * - 수정: 현재 시각 기준 2분 전 ~ 현재 시각 데이터 읽음 (최근 2분간 데이터 중 최신)
     */
    @Bean
    public JdbcCursorItemReader<CpuAgg1mDto> cpuAgg1mReader() {
        String sql = """
            SELECT DISTINCT ON (instance_id)
                instance_id,
                collected_at
            FROM cpu_raw
            WHERE collected_at >= DATE_TRUNC('minute', CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - INTERVAL '2 minutes'
              AND collected_at <= DATE_TRUNC('minute', CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
            ORDER BY instance_id, collected_at DESC
            """;

        return new JdbcCursorItemReaderBuilder<CpuAgg1mDto>()
                .name("cpuAgg1mReader")
                .dataSource(dataSource)
                .sql(sql)
                .rowMapper(new CpuAgg1mDtoRowMapper())
                .build();
    }

    /**
     * Processor: 각 인스턴스별로 이전 Raw 데이터와 비교하여 Agg 생성
     */
    @Bean
    public ItemProcessor<CpuAgg1mDto, CpuAgg> cpuAgg1mProcessor() {
        return dto -> {
            Long instanceId = dto.getInstanceId();
            OffsetDateTime collectedAt = dto.getCollectedAt();

            // 최신 Raw 데이터 조회
            List<CpuRaw> recentRaws = cpuMapper.selectCpuRawByTimeRange(
                    instanceId,
                    collectedAt.minusMinutes(1),
                    collectedAt.plusMinutes(1)
            );

            if (recentRaws.isEmpty()) {
                log.warn("Raw 데이터 없음: instanceId={}, collectedAt={}", instanceId, collectedAt);
                return null; // Skip 처리
            }

            // 가장 최신 Raw 데이터 사용
            CpuRaw raw = recentRaws.stream()
                    .filter(r -> r.getCollectedAt().equals(collectedAt))
                    .findFirst()
                    .orElse(recentRaws.get(0));
            
            // 디버깅: raw 데이터 로깅
            log.info("=== [디버깅] raw 데이터: instanceId={}, collected_at={}, xact_commit={}, xact_rollback={}", 
                    instanceId, raw.getCollectedAt(), raw.getXactCommit(), raw.getXactRollback());

            // 이전 Raw 데이터 조회 (현재 collectedAt보다 이전 데이터)
            CpuRaw previousRaw = cpuMapper.selectPreviousRaw(instanceId, collectedAt);
            
            // 디버깅: previousRaw 조회 결과 로깅
            log.info("=== [디버깅] previousRaw 조회 결과: instanceId={}, collectedAt={}, previousRaw={}", 
                    instanceId, collectedAt, previousRaw != null ? "존재" : "null");
            if (previousRaw != null) {
                log.info("=== [디버깅] previousRaw 상세: collected_at={}, xact_commit={}, xact_rollback={}", 
                        previousRaw.getCollectedAt(), 
                        previousRaw.getXactCommit(), 
                        previousRaw.getXactRollback());
            }

            // 이전 데이터가 없으면 첫 번째 수집이므로 Agg 생성 스킵
            if (previousRaw == null) {
                log.debug("첫 번째 수집: 이전 데이터가 없어 집계 데이터 생성을 스킵합니다. instanceId={}", instanceId);
                return null; // Skip 처리
            }

            // Agg 데이터 생성
            return buildCpuAgg(instanceId, collectedAt, raw, previousRaw);
        };
    }

    /**
     * Writer: Agg 데이터 저장
     */
    @Bean
    public ItemWriter<CpuAgg> cpuAgg1mWriter() {
        return (Chunk<? extends CpuAgg> chunk) -> {
            List<CpuAgg> items = new ArrayList<>();
            for (CpuAgg item : chunk.getItems()) {
                if (item != null) {
                    items.add(item);
                }
            }

            if (!items.isEmpty()) {
                for (CpuAgg agg : items) {
                    cpuMapper.insertAgg(agg);
                }
                log.info("CPU 1분 집계 완료: {} 건 저장", items.size());
            }
        };
    }

    /**
     * CpuAgg 객체 생성
     */
    private CpuAgg buildCpuAgg(Long instanceId, OffsetDateTime collectedAt,
                               CpuRaw raw, CpuRaw previousRaw) {
        log.info("=== [디버깅] buildCpuAgg 시작: instanceId={}, collectedAt={}, " +
                "raw.xactCommit={}, raw.xactRollback={}, " +
                "previousRaw.xactCommit={}, previousRaw.xactRollback={}", 
                instanceId, collectedAt,
                raw.getXactCommit(), raw.getXactRollback(),
                previousRaw.getXactCommit(), previousRaw.getXactRollback());
        
        // 상태 판정 로직
        String status = determineStatus(raw);

        // 트랜잭션 증분 및 TPS 계산
        Long deltaXactCommit = 0L;
        Long deltaXactRollback = 0L;
        Double xactCommitRate = 0.0;
        Double xactRollbackRate = 0.0;

        // previousRaw와 raw의 트랜잭션 데이터가 모두 유효한 경우에만 증분 계산
        if (previousRaw != null 
                && raw.getXactCommit() != null && raw.getXactRollback() != null
                && previousRaw.getXactCommit() != null && previousRaw.getXactRollback() != null) {
            
            // stats_reset 대응을 위한 안전한 증분 계산
            deltaXactCommit = MetricCollectionUtils.calculateSafeDelta(
                    raw.getXactCommit(), previousRaw.getXactCommit());
            deltaXactRollback = MetricCollectionUtils.calculateSafeDelta(
                    raw.getXactRollback(), previousRaw.getXactRollback());

            // 수집 간격(초) 계산 (기본 60초)
            long intervalSeconds = ChronoUnit.SECONDS.between(
                    previousRaw.getCollectedAt(), raw.getCollectedAt()
            );
            if (intervalSeconds <= 0) {
                intervalSeconds = 60;
            }

            // TPS 계산: 초당 트랜잭션 수
            if (intervalSeconds > 0) {
                xactCommitRate = deltaXactCommit.doubleValue() / intervalSeconds;
                xactRollbackRate = deltaXactRollback.doubleValue() / intervalSeconds;
            }
            
            log.debug("트랜잭션 증분 계산 완료: instanceId={}, deltaXactCommit={}, deltaXactRollback={}, xactCommitRate={}, xactRollbackRate={}", 
                    instanceId, deltaXactCommit, deltaXactRollback, xactCommitRate, xactRollbackRate);
        } else {
            log.warn("트랜잭션 데이터 누락으로 증분 계산 스킵: instanceId={}, " +
                    "previousRaw.xactCommit={}, previousRaw.xactRollback={}, " +
                    "raw.xactCommit={}, raw.xactRollback={}", 
                    instanceId,
                    previousRaw != null ? previousRaw.getXactCommit() : "null",
                    previousRaw != null ? previousRaw.getXactRollback() : "null",
                    raw.getXactCommit(),
                    raw.getXactRollback());
        }

        CpuAgg result = CpuAgg.builder()
                .instanceId(instanceId)
                .collectedAt(collectedAt)
                .avgTotalConnections((double) raw.getTotalConnections())
                .avgActiveConnections((double) raw.getActiveConnections())
                .avgIdleConnections((double) raw.getIdleConnections())
                .avgIdleInTransaction((double) raw.getIdleInTransaction())
                .avgWaitingSessions((double) raw.getWaitingSessions())
                .avgWaitingForLock((double) raw.getWaitingForLock())
                .avgWaitingForIo((double) raw.getWaitingForIo())
                .avgWaitEventClient((double) raw.getWaitEventClient())
                .avgWaitEventActivity((double) raw.getWaitEventActivity())
                .avgWaitEventBufferpin((double) raw.getWaitEventBufferpin())
                .avgWaitEventLwlock((double) raw.getWaitEventLwlock())
                .avgWaitEventTimeout((double) raw.getWaitEventTimeout())
                .avgWaitEventIpc((double) raw.getWaitEventIpc())
                .avgClientBackend((double) raw.getClientBackendCount())
                .avgAutovacuumWorker((double) raw.getAutovacuumWorkerCount())
                .avgParallelWorker((double) raw.getParallelWorkerCount())
                .avgBackgroundWorker((double) raw.getBackgroundWorkerCount())
                .avgLongRunningQueries((double) raw.getLongRunningQueries())
                .maxQueryDurationSec(raw.getMaxQueryDurationSec())
                // 트랜잭션 통계
                .databaseName(raw.getDatabaseName())
                .deltaXactCommit(deltaXactCommit)
                .deltaXactRollback(deltaXactRollback)
                .xactCommitRate(xactCommitRate)
                .xactRollbackRate(xactRollbackRate)
                .status(status)
                .build();
        
        log.info("=== [디버깅] buildCpuAgg 완료: instanceId={}, deltaXactCommit={}, deltaXactRollback={}, xactCommitRate={}, xactRollbackRate={}", 
                instanceId, deltaXactCommit, deltaXactRollback, xactCommitRate, xactRollbackRate);
        
        return result;
    }

    /**
     * 상태 판정 로직
     */
    private String determineStatus(CpuRaw raw) {
        long waitingSessions = raw.getWaitingSessions();
        long longRunningQueries = raw.getLongRunningQueries();

        if (waitingSessions < 5 && longRunningQueries < 3) {
            return "정상";
        } else if (waitingSessions < 10 && longRunningQueries < 5) {
            return "주의";
        } else {
            return "위험";
        }
    }

    /**
     * RowMapper: DB 결과를 DTO로 매핑
     */
    private static class CpuAgg1mDtoRowMapper implements RowMapper<CpuAgg1mDto> {
        @Override
        public CpuAgg1mDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            CpuAgg1mDto dto = new CpuAgg1mDto();
            dto.setInstanceId(rs.getLong("instance_id"));

            java.sql.Timestamp collectedAtTs = rs.getTimestamp("collected_at");
            if (collectedAtTs != null) {
                dto.setCollectedAt(OffsetDateTime.ofInstant(
                        collectedAtTs.toInstant(),
                        ZoneOffset.UTC
                ));
            }

            dto.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            return dto;
        }
    }
}