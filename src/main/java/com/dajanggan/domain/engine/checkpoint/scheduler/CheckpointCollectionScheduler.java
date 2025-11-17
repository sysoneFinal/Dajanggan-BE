package com.dajanggan.domain.engine.checkpoint.scheduler;

import com.dajanggan.domain.engine.checkpoint.domain.CheckpointAgg;
import com.dajanggan.domain.engine.checkpoint.domain.CheckpointRaw;
import com.dajanggan.domain.engine.checkpoint.repository.CheckpointMapper;
import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.infrastructure.datasource.DataSourceFactory;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Checkpoint 메트릭 수집 스케줄러
 * 1분마다 실행:
 * 1. pg_stat_bgwriter에서 checkpoint 관련 데이터 수집
 * 2. Raw 데이터 저장
 * 3. 이전 데이터와 비교하여 증분 계산 후 Agg 저장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckpointCollectionScheduler {

    private final CheckpointMapper checkpointMapper;
    private final InstanceRepository instanceRepository;
    private final DataSourceFactory dataSourceFactory;

    @PostConstruct
    public void init() {
        log.info("========== CheckpointCollectionScheduler 초기화 완료 ==========");
    }

    /**
     * 1분마다 실행 (매분 0초)
     */
    @Scheduled(cron = "0 * * * * *")
    public void collectCheckpointMetrics() {
        log.info("========== Checkpoint 메트릭 수집 시작 ==========");

        try {
            LocalDateTime collectedAt = LocalDateTime.now();
            List<Long> instanceIds = checkpointMapper.selectActiveInstanceIds();
            log.info("처리 대상 인스턴스: {} 개", instanceIds.size());

            int successCount = 0;
            int failCount = 0;

            for (Long instanceId : instanceIds) {
                try {
                    processInstanceMetrics(instanceId, collectedAt);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("Checkpoint 메트릭 처리 실패: instanceId={}", instanceId, e);
                }
            }

            log.info("========== Checkpoint 메트릭 수집 완료: 성공={}, 실패={} ==========", successCount, failCount);

        } catch (Exception e) {
            log.error("Checkpoint 메트릭 수집 중 오류 발생", e);
        }
    }

    /**
     * 특정 인스턴스의 메트릭 처리
     */
    private void processInstanceMetrics(Long instanceId, LocalDateTime collectedAt) {
        Instance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("인스턴스를 찾을 수 없습니다: " + instanceId));

        JdbcTemplate jdbcTemplate = dataSourceFactory.createJdbcTemplate(instance, "postgres");
        Map<String, Object> currentData = collectFromPgStatBgwriter(jdbcTemplate);
        CheckpointRaw previousRaw = checkpointMapper.selectPreviousRaw(instanceId);

        CheckpointRaw raw = buildCheckpointRaw(instanceId, collectedAt, currentData);
        checkpointMapper.insertRaw(raw);
        log.debug("Raw 데이터 저장 완료: instanceId={}", instanceId);

        if (previousRaw != null) {
            CheckpointAgg agg = calculateAggregation(instanceId, collectedAt, raw, previousRaw);
            checkpointMapper.insertAgg(agg);
            log.debug("Agg 데이터 저장 완료: instanceId={}", instanceId);
        }

        log.info("메트릭 처리 완료: instanceId={}", instanceId);
    }

    /**
     * pg_stat_bgwriter에서 데이터 수집
     */
    private Map<String, Object> collectFromPgStatBgwriter(JdbcTemplate jdbcTemplate) {
        String query = """
            SELECT 
                checkpoints_timed,
                checkpoints_req,
                checkpoint_write_time,
                checkpoint_sync_time,
                buffers_checkpoint,
                buffers_clean,
                maxwritten_clean,
                buffers_backend,
                buffers_backend_fsync,
                buffers_alloc,
                stats_reset
            FROM pg_stat_bgwriter
            """;

        return jdbcTemplate.queryForMap(query);
    }

    /**
     * CheckpointRaw 객체 생성
     */
    private CheckpointRaw buildCheckpointRaw(Long instanceId, LocalDateTime collectedAt, 
                                             Map<String, Object> data) {
        Long checkpointsTimed = getLongValue(data, "checkpoints_timed");
        Long checkpointsReq = getLongValue(data, "checkpoints_req");
        Double writeTime = getDoubleValue(data, "checkpoint_write_time");
        Double syncTime = getDoubleValue(data, "checkpoint_sync_time");
        Long buffersCheckpoint = getLongValue(data, "buffers_checkpoint");
        Long buffersBackend = getLongValue(data, "buffers_backend");

        // 체크포인트 타입 결정 (마지막 체크포인트가 어떤 타입이었는지)
        String checkpointType = (checkpointsReq > 0) ? "requested" : "timed";

        return CheckpointRaw.builder()
                .instanceId(instanceId)
                .collectedAt(collectedAt)
                .checkpointType(checkpointType)
                .checkpointsTimed(checkpointsTimed)
                .checkpointsReq(checkpointsReq)
                .checkpointWriteTime(writeTime)
                .checkpointSyncTime(syncTime)
                .buffersCheckpoint(buffersCheckpoint)
                .buffersBackend(buffersBackend)
                .walBytes(BigDecimal.ZERO)  // WAL 관련은 별도 쿼리 필요
                .walFilesAdded(0L)
                .walFilesRemoved(0L)
                .checkpointDistance(0L)
                .avgBuffersPerSec(0.0)
                .build();
    }

    /**
     * 증분 계산하여 Agg 데이터 생성
     */
    private CheckpointAgg calculateAggregation(Long instanceId, LocalDateTime collectedAt,
                                                CheckpointRaw current, CheckpointRaw previous) {
        // 증분 계산
        long deltaTimedCheckpoints = current.getCheckpointsTimed() - previous.getCheckpointsTimed();
        long deltaReqCheckpoints = current.getCheckpointsReq() - previous.getCheckpointsReq();
        double deltaWriteTime = current.getCheckpointWriteTime() - previous.getCheckpointWriteTime();
        double deltaSyncTime = current.getCheckpointSyncTime() - previous.getCheckpointSyncTime();
        long deltaBuffersCheckpoint = current.getBuffersCheckpoint() - previous.getBuffersCheckpoint();

        // 총 체크포인트 수
        long totalCheckpoints = deltaTimedCheckpoints + deltaReqCheckpoints;

        // 요청형 체크포인트 비율 계산
        double reqRatio = 0.0;
        if (totalCheckpoints > 0) {
            reqRatio = (100.0 * deltaReqCheckpoints) / totalCheckpoints;
        }

        // 평균 시간 계산
        double avgWriteTime = totalCheckpoints > 0 ? deltaWriteTime / totalCheckpoints : 0.0;
        double avgSyncTime = totalCheckpoints > 0 ? deltaSyncTime / totalCheckpoints : 0.0;
        double avgTotalTime = avgWriteTime + avgSyncTime;

        // 상태 판단 (총 시간 기준)
        String status = "정상";
        if (avgTotalTime > 30) {
            status = "위험";
        } else if (avgTotalTime > 15) {
            status = "주의";
        }

        return CheckpointAgg.builder()
                .instanceId(instanceId)
                .collectedAt(collectedAt)
                .avgCheckpointReqRatio(reqRatio)
                .avgWriteTime(avgWriteTime)
                .avgSyncTime(avgSyncTime)
                .avgTotalTime(avgTotalTime)
                .totalCheckpointsTimed(deltaTimedCheckpoints)
                .totalCheckpointsReq(deltaReqCheckpoints)
                .totalWalBytes(BigDecimal.ZERO)  // WAL 관련은 별도 쿼리 필요
                .totalBuffersCheckpoint(deltaBuffersCheckpoint)
                .status(status)
                .build();
    }

    /**
     * Map에서 Long 값 추출 헬퍼
     */
    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    /**
     * Map에서 Double 값 추출 헬퍼
     */
    private Double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }
}
