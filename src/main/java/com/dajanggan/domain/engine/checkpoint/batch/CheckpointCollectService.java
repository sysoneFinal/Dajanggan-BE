package com.dajanggan.domain.engine.checkpoint.batch;

import com.dajanggan.domain.engine.checkpoint.domain.CheckpointRaw;
import com.dajanggan.domain.engine.checkpoint.repository.CheckpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * - pg_stat_bgwriter에서 실시간 데이터 수집
 * - 이전 수집값과 차이 계산 (누적 카운터)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckpointCollectService {

    private final CheckpointRepository checkpointRepository;

    /**
     * 특정 인스턴스의 Checkpoint 데이터 수집
     * 
     * @param instanceId 인스턴스 ID
     */
    @Transactional
    public void collectCheckpointData(Long instanceId) {
        try {
            log.debug("Collecting checkpoint data for instance: {}", instanceId);

            // 1. pg_stat_bgwriter에서 현재 통계 조회
            Map<String, Object> currentStats = checkpointRepository.selectCurrentBgwriterStats(instanceId);

            if (currentStats == null || currentStats.isEmpty()) {
                log.warn("No bgwriter stats found for instance: {}", instanceId);
                return;
            }

            // 2. 이전 수집 데이터 조회
            CheckpointRaw previousRaw = checkpointRepository.selectLatestCheckpointRaw(instanceId);

            // 3. 차이값 계산 (누적 카운터이므로)
            CheckpointRaw checkpointRaw = calculateDifference(instanceId, currentStats, previousRaw);

            // 4. 데이터 저장
            if (checkpointRaw != null) {
                checkpointRepository.insertCheckpointRaw(checkpointRaw);
                log.debug("Checkpoint data collected successfully for instance: {}", instanceId);
            }

        } catch (Exception e) {
            log.error("Failed to collect checkpoint data for instance: {}", instanceId, e);
            throw new RuntimeException("Checkpoint data collection failed for instance: " + instanceId, e);
        }
    }

    /**
     * 이전 값과 현재 값의 차이 계산
     * - pg_stat_bgwriter는 누적 카운터이므로 차이값을 구해야 함
     */
    private CheckpointRaw calculateDifference(
            Long instanceId,
            Map<String, Object> currentStats,
            CheckpointRaw previousRaw
    ) {
        OffsetDateTime collectedAt = OffsetDateTime.now();

        // 현재 값 추출
        Long currentCheckpointsTimed = getLongValue(currentStats.get("checkpoints_timed"));
        Long currentCheckpointsReq = getLongValue(currentStats.get("checkpoints_req"));
        Double currentWriteTime = getDoubleValue(currentStats.get("checkpoint_write_time"));
        Double currentSyncTime = getDoubleValue(currentStats.get("checkpoint_sync_time"));
        Long currentBuffersCheckpoint = getLongValue(currentStats.get("buffers_checkpoint"));

        // 이전 데이터가 없으면 현재 값 그대로 저장 (초기 수집)
        if (previousRaw == null) {
            log.info("Initial checkpoint data collection for instance: {}", instanceId);
            return buildCheckpointRaw(
                    instanceId,
                    collectedAt,
                    currentCheckpointsTimed,
                    currentCheckpointsReq,
                    currentWriteTime,
                    currentSyncTime,
                    currentBuffersCheckpoint
            );
        }

        // 차이값 계산
        Long diffCheckpointsTimed = currentCheckpointsTimed - previousRaw.getCheckpointsTimed();
        Long diffCheckpointsReq = currentCheckpointsReq - previousRaw.getCheckpointsReq();
        Double diffWriteTime = currentWriteTime - previousRaw.getCheckpointWriteTime();
        Double diffSyncTime = currentSyncTime - previousRaw.getCheckpointSyncTime();
        Long diffBuffersCheckpoint = currentBuffersCheckpoint - previousRaw.getBuffersCheckpoint();

        // stats_reset 체크 (PostgreSQL이 통계를 리셋한 경우)
        if (diffCheckpointsTimed < 0 || diffCheckpointsReq < 0) {
            log.warn("Stats reset detected for instance: {}. Using current values.", instanceId);
            return buildCheckpointRaw(
                    instanceId,
                    collectedAt,
                    currentCheckpointsTimed,
                    currentCheckpointsReq,
                    currentWriteTime,
                    currentSyncTime,
                    currentBuffersCheckpoint
            );
        }

        // 차이값이 0이면 수집하지 않음 (변화 없음)
        if (diffCheckpointsTimed == 0 && diffCheckpointsReq == 0) {
            log.debug("No checkpoint activity for instance: {}", instanceId);
            return null;
        }

        return buildCheckpointRaw(
                instanceId,
                collectedAt,
                diffCheckpointsTimed,
                diffCheckpointsReq,
                diffWriteTime,
                diffSyncTime,
                diffBuffersCheckpoint
        );
    }

    /**
     * CheckpointRaw 객체 생성
     */
    private CheckpointRaw buildCheckpointRaw(
            Long instanceId,
            OffsetDateTime collectedAt,
            Long checkpointsTimed,
            Long checkpointsReq,
            Double writeTime,
            Double syncTime,
            Long buffersCheckpoint
    ) {
        // Checkpoint 타입 결정
        String checkpointType = (checkpointsReq != null && checkpointsReq > 0) ? "requested" : "timed";

        // WAL bytes 계산 (임시로 버퍼 * 8KB로 추정)
        Long walBytes = buffersCheckpoint != null ? buffersCheckpoint * 8192 : 0L;

        return CheckpointRaw.builder()
                .instanceId(instanceId)
                .collectedAt(collectedAt)
                .checkpointType(checkpointType)
                .checkpointsTimed(checkpointsTimed != null ? checkpointsTimed : 0L)
                .checkpointsReq(checkpointsReq != null ? checkpointsReq : 0L)
                .checkpointWriteTime(writeTime != null ? writeTime : 0.0)
                .checkpointSyncTime(syncTime != null ? syncTime : 0.0)
                .buffersCheckpoint(buffersCheckpoint != null ? buffersCheckpoint : 0L)
                .walBytes(walBytes)
                .build();
    }

    /**
     * Object를 Long으로 변환
     */
    private Long getLongValue(Object value) {
        if (value == null) return 0L;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof String) return Long.parseLong((String) value);
        return 0L;
    }

    /**
     * Object를 Double로 변환
     */
    private Double getDoubleValue(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Float) return ((Float) value).doubleValue();
        if (value instanceof Integer) return ((Integer) value).doubleValue();
        if (value instanceof Long) return ((Long) value).doubleValue();
        if (value instanceof String) return Double.parseDouble((String) value);
        return 0.0;
    }
}
