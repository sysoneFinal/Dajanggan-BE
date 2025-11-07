package com.dajanggan.domain.engine.checkpoint.batch;

import com.dajanggan.domain.engine.checkpoint.domain.CheckpointAgg;
import com.dajanggan.domain.engine.checkpoint.repository.CheckpointAggregationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * - checkpoint_raw 데이터를 집계하여 checkpoint_agg에 저장
 * - 시간별/일별 집계 데이터 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckpointAggregationService {

    private final CheckpointAggregationRepository aggregationRepository;

    /**
     * 특정 인스턴스의 시간별 집계 데이터 생성
     * 
     * @param instanceId 인스턴스 ID
     * @param startTime 집계 시작 시간
     * @param endTime 집계 종료 시간
     */
    @Transactional
    public void aggregateHourlyData(Long instanceId, OffsetDateTime startTime, OffsetDateTime endTime) {
        try {
            log.debug("Aggregating hourly checkpoint data for instance: {} from {} to {}", 
                     instanceId, startTime, endTime);

            // 1. 시간별 집계 데이터 조회
            Map<String, Object> aggData = aggregationRepository.selectHourlyAggregation(
                    instanceId, startTime, endTime
            );

            if (aggData == null || aggData.isEmpty()) {
                log.warn("No raw data found for aggregation. Instance: {}, Period: {} ~ {}", 
                        instanceId, startTime, endTime);
                return;
            }

            // 2. CheckpointAgg 객체 생성
            CheckpointAgg checkpointAgg = buildCheckpointAgg(instanceId, aggData, startTime);

            // 3. 집계 데이터 저장
            aggregationRepository.insertCheckpointAgg(checkpointAgg);

            log.debug("Hourly aggregation completed for instance: {} at {}", instanceId, startTime);

        } catch (Exception e) {
            log.error("Failed to aggregate hourly checkpoint data for instance: {}", instanceId, e);
            throw new RuntimeException("Checkpoint aggregation failed for instance: " + instanceId, e);
        }
    }

    /**
     * CheckpointAgg 객체 생성
     */
    private CheckpointAgg buildCheckpointAgg(
            Long instanceId,
            Map<String, Object> aggData,
            OffsetDateTime collectedAt
    ) {
        // 집계 데이터 추출
        Double avgCheckpointReqRatio = getDoubleValue(aggData.get("avg_checkpoint_req_ratio"));
        Double avgWriteTime = getDoubleValue(aggData.get("avg_write_time"));
        Double avgSyncTime = getDoubleValue(aggData.get("avg_sync_time"));
        Double avgTotalTime = getDoubleValue(aggData.get("avg_total_time"));
        Long totalCheckpointsTimed = getLongValue(aggData.get("total_checkpoints_timed"));
        Long totalCheckpointsReq = getLongValue(aggData.get("total_checkpoints_req"));
        Long totalWalBytes = getLongValue(aggData.get("total_wal_bytes"));
        Long totalBuffersCheckpoint = getLongValue(aggData.get("total_buffers_checkpoint"));

        // 상태 결정 (임계값 기반)
        String status = determineStatus(avgCheckpointReqRatio, avgTotalTime);

        return CheckpointAgg.builder()
                .instanceId(instanceId)
                .collectedAt(collectedAt)
                .avgCheckpointReqRatio(avgCheckpointReqRatio)
                .avgWriteTime(avgWriteTime)
                .avgSyncTime(avgSyncTime)
                .avgTotalTime(avgTotalTime)
                .totalCheckpointsTimed(totalCheckpointsTimed)
                .totalCheckpointsReq(totalCheckpointsReq)
                .totalWalBytes(totalWalBytes)
                .totalBuffersCheckpoint(totalBuffersCheckpoint)
                .status(status)
                .build();
    }

    /**
     * 상태 결정 로직
     * - 요청 비율과 처리 시간을 기반으로 상태 판단
     */
    private String determineStatus(Double avgCheckpointReqRatio, Double avgTotalTime) {
        // 요청 기반 Checkpoint 비율이 높으면 WARNING
        if (avgCheckpointReqRatio != null && avgCheckpointReqRatio > 50.0) {
            return "WARNING";
        }

        // 평균 처리 시간이 너무 길면 WARNING
        if (avgTotalTime != null && avgTotalTime > 5000.0) { // 5초 이상
            return "WARNING";
        }

        // 요청 비율이 매우 높으면 CRITICAL
        if (avgCheckpointReqRatio != null && avgCheckpointReqRatio > 80.0) {
            return "CRITICAL";
        }

        return "NORMAL";
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
