// 작성자 : 김동현
package com.dajanggan.domain.engine.checkpoint.scheduler;

import com.dajanggan.domain.engine.checkpoint.domain.CheckpointAgg1m;
import com.dajanggan.domain.engine.checkpoint.domain.CheckpointAgg5m;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * Checkpoint 메트릭 수집 스케줄러
 * Raw 데이터만 수집 (집계는 Batch Aggregator에서 수행)
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
     * 1분마다 실행 (매분 5초)
     */
    @Scheduled(cron = "5 * * * * *")
    public void collectCheckpoint1mMetrics() {
        log.info("========== Checkpoint 1분 집계 시작 ==========");

        try {
            LocalDateTime collectedAt = LocalDateTime.now();
            List<Long> instanceIds = checkpointMapper.selectActiveInstanceIds();
            log.info("처리 대상 인스턴스: {} 개", instanceIds.size());

            int successCount = 0;
            int failCount = 0;

            for (Long instanceId : instanceIds) {
                try {
                    processInstance1mMetrics(instanceId, collectedAt);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("Checkpoint 1분 집계 처리 실패: instanceId={}", instanceId, e);
                }
            }

            log.info("========== Checkpoint 1분 집계 완료: 성공={}, 실패={} ==========", successCount, failCount);

        } catch (Exception e) {
            log.error("Checkpoint 1분 집계 중 오류 발생", e);
        }
    }

    /**
     * 5분마다 실행 (매 5분의 10초)
     */
    @Scheduled(cron = "10 */5 * * * *")
    public void collectCheckpoint5mMetrics() {
        log.info("========== Checkpoint 5분 집계 시작 ==========");

        try {
            // 현재 시간을 시스템 시간대에서 가져온 후 UTC로 변환
            // LocalDateTime.now()는 시간대 정보가 없으므로, ZonedDateTime을 사용
            ZonedDateTime nowZoned = ZonedDateTime.now(ZoneId.systemDefault());
            LocalDateTime collectedAt = nowZoned.toLocalDateTime();
            log.debug("수집 시간 (로컬): {}", collectedAt);
            List<Long> instanceIds = checkpointMapper.selectActiveInstanceIds();
            log.info("처리 대상 인스턴스: {} 개", instanceIds.size());

            int successCount = 0;
            int failCount = 0;

            for (Long instanceId : instanceIds) {
                try {
                    processInstance5mMetrics(instanceId, collectedAt);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("Checkpoint 5분 집계 처리 실패: instanceId={}", instanceId, e);
                }
            }

            log.info("========== Checkpoint 5분 집계 완료: 성공={}, 실패={} ==========", successCount, failCount);

        } catch (Exception e) {
            log.error("Checkpoint 5분 집계 중 오류 발생", e);
        }
    }

    /**
     * 특정 인스턴스의 1분 집계 처리
     */
    private void processInstance1mMetrics(Long instanceId, LocalDateTime collectedAt) {
        Instance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("인스턴스를 찾을 수 없습니다: " + instanceId));

        JdbcTemplate jdbcTemplate = dataSourceFactory.createJdbcTemplate(instance, "postgres");
        Map<String, Object> currentData = collectFromPgStatBgwriter(jdbcTemplate);
        Map<String, Object> walData = collectWalData(jdbcTemplate);
        CheckpointRaw previousRaw = checkpointMapper.selectPreviousRaw(instanceId);

        CheckpointRaw raw = buildCheckpointRaw(instanceId, collectedAt, currentData, walData, previousRaw);
        checkpointMapper.insertRaw(raw);
        log.debug("Raw 데이터 저장 완료: instanceId={}", instanceId);

        // Agg 데이터 생성은 배치로 처리
        log.info("1분 집계 처리 완료: instanceId={}", instanceId);
    }

    /**
     * 특정 인스턴스의 5분 집계 처리
     * Agg 데이터 생성은 배치로 처리
     */
    private void processInstance5mMetrics(Long instanceId, LocalDateTime collectedAt) {
        // 5분 집계는 배치로 처리되므로 여기서는 아무 작업도 하지 않음
        log.debug("5분 집계는 배치로 처리됩니다: instanceId={}", instanceId);
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
     * WAL 관련 데이터 수집
     * pg_stat_wal과 pg_current_wal_lsn()을 사용하여 WAL 정보 수집
     */
    private Map<String, Object> collectWalData(JdbcTemplate jdbcTemplate) {
        try {
            // PostgreSQL 13+ 버전에서는 pg_stat_wal 사용
            String query = """
                    SELECT 
                        COALESCE(wal_records, 0) AS wal_records,
                        COALESCE(wal_fpi, 0) AS wal_fpi,
                        COALESCE(wal_bytes, 0) AS wal_bytes,
                        COALESCE(wal_buffers_full, 0) AS wal_buffers_full,
                        COALESCE(wal_write, 0) AS wal_write,
                        COALESCE(wal_sync, 0) AS wal_sync,
                        COALESCE(wal_write_time, 0) AS wal_write_time,
                        COALESCE(wal_sync_time, 0) AS wal_sync_time,
                        stats_reset
                    FROM pg_stat_wal
                    """;
            return jdbcTemplate.queryForMap(query);
        } catch (Exception e) {
            // pg_stat_wal이 없는 경우 (PostgreSQL 12 이하) 또는 오류 발생 시
            log.warn("pg_stat_wal 조회 실패, 기본값 사용: {}", e.getMessage());
            // 기본값 반환
            return Map.of(
                    "wal_records", 0L,
                    "wal_fpi", 0L,
                    "wal_bytes", 0L,
                    "wal_buffers_full", 0L,
                    "wal_write", 0L,
                    "wal_sync", 0L,
                    "wal_write_time", 0.0,
                    "wal_sync_time", 0.0
            );
        }
    }

    /**
     * CheckpointRaw 객체 생성
     */
    private CheckpointRaw buildCheckpointRaw(Long instanceId, LocalDateTime collectedAt,
                                             Map<String, Object> bgwriterData, 
                                             Map<String, Object> walData,
                                             CheckpointRaw previousRaw) {
        Long checkpointsTimed = getLongValue(bgwriterData, "checkpoints_timed");
        Long checkpointsReq = getLongValue(bgwriterData, "checkpoints_req");
        Double writeTime = getDoubleValue(bgwriterData, "checkpoint_write_time");
        Double syncTime = getDoubleValue(bgwriterData, "checkpoint_sync_time");
        Long buffersCheckpoint = getLongValue(bgwriterData, "buffers_checkpoint");
        Long buffersBackend = getLongValue(bgwriterData, "buffers_backend");

        // WAL 데이터 추출
        Long walBytes = getLongValue(walData, "wal_bytes");
        Long walWrite = getLongValue(walData, "wal_write");
        Long walSync = getLongValue(walData, "wal_sync");

        // 체크포인트 타입 판단 (이전 데이터와 비교)
        String checkpointType = "unknown";
        if (previousRaw != null) {
            long deltaTimed = calculateSafeDelta(checkpointsTimed, previousRaw.getCheckpointsTimed());
            long deltaReq = calculateSafeDelta(checkpointsReq, previousRaw.getCheckpointsReq());
            
            if (deltaReq > 0) {
                checkpointType = "requested";
            } else if (deltaTimed > 0) {
                checkpointType = "timed";
            }
        }

        // 체크포인트 거리 계산 (이전 WAL 바이트와 비교)
        Long checkpointDistance = 0L;
        if (previousRaw != null && previousRaw.getWalBytes() != null) {
            long deltaWalBytes = calculateSafeDelta(walBytes, previousRaw.getWalBytes().longValue());
            checkpointDistance = deltaWalBytes;
        }

        // 평균 버퍼 처리량 계산 (초당)
        Double avgBuffersPerSec = 0.0;
        if (previousRaw != null && writeTime != null && previousRaw.getCheckpointWriteTime() != null) {
            double deltaTime = calculateSafeDelta(writeTime, previousRaw.getCheckpointWriteTime());
            if (deltaTime > 0) {
                long deltaBuffers = calculateSafeDelta(buffersCheckpoint, previousRaw.getBuffersCheckpoint());
                avgBuffersPerSec = (deltaBuffers * 1000.0) / deltaTime; // 밀리초를 초로 변환
            }
        }
        Long walFilesAdded = walWrite;
        Long walFilesRemoved = 0L; // 실제로는 pg_stat_archiver에서 가져와야 함

        return CheckpointRaw.builder()
                .instanceId(instanceId)
                .collectedAt(toUtcOffsetDateTime(collectedAt))
                .checkpointType(checkpointType)
                .checkpointsTimed(checkpointsTimed)
                .checkpointsReq(checkpointsReq)
                .checkpointWriteTime(writeTime)
                .checkpointSyncTime(syncTime)
                .buffersCheckpoint(buffersCheckpoint)
                .buffersBackend(buffersBackend)
                .walBytes(BigDecimal.valueOf(walBytes))
                .walFilesAdded(walFilesAdded)
                .walFilesRemoved(walFilesRemoved)
                .checkpointDistance(checkpointDistance)
                .avgBuffersPerSec(avgBuffersPerSec)
                .build();
    }

    /**
     * 증분 계산하여 1분 집계 데이터 생성
     */
    private CheckpointAgg1m calculateAggregation1m(Long instanceId, LocalDateTime collectedAt,
                                                   CheckpointRaw current, CheckpointRaw previous) {
        // 증분 계산 (음수 방어: stats_reset 발생 시 현재 값 사용)
        long deltaTimedCheckpoints = calculateSafeDelta(
                current.getCheckpointsTimed(),
                previous.getCheckpointsTimed()
        );
        long deltaReqCheckpoints = calculateSafeDelta(
                current.getCheckpointsReq(),
                previous.getCheckpointsReq()
        );
        double deltaWriteTime = calculateSafeDelta(
                current.getCheckpointWriteTime(),
                previous.getCheckpointWriteTime()
        );
        double deltaSyncTime = calculateSafeDelta(
                current.getCheckpointSyncTime(),
                previous.getCheckpointSyncTime()
        );
        long deltaBuffersCheckpoint = calculateSafeDelta(
                current.getBuffersCheckpoint(),
                previous.getBuffersCheckpoint()
        );

        // WAL 바이트 증분 계산
        BigDecimal deltaWalBytes = BigDecimal.ZERO;
        if (current.getWalBytes() != null && previous.getWalBytes() != null) {
            long currentWalBytes = current.getWalBytes().longValue();
            long previousWalBytes = previous.getWalBytes().longValue();
            long delta = calculateSafeDelta(currentWalBytes, previousWalBytes);
            deltaWalBytes = BigDecimal.valueOf(delta);
        }

        // 총 체크포인트 수
        long totalCheckpoints = deltaTimedCheckpoints + deltaReqCheckpoints;

        // 1분 동안 체크포인트가 없는 경우 (정상 상황)
        if (totalCheckpoints == 0) {
            return CheckpointAgg1m.builder()
                    .instanceId(instanceId)
                    .collectedAt(toUtcOffsetDateTime(collectedAt))
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

        // 요청형 체크포인트 비율 계산
        double reqRatio = (100.0 * deltaReqCheckpoints) / totalCheckpoints;

        // 평균 시간 계산 (밀리초를 초로 변환)
        double avgWriteTime = (deltaWriteTime / 1000.0) / totalCheckpoints;
        double avgSyncTime = (deltaSyncTime / 1000.0) / totalCheckpoints;
        double avgTotalTime = avgWriteTime + avgSyncTime;

        // 상태 판단 개선 (체크포인트 평균 시간 + 요청형 비율 고려)
        String status = determineCheckpointStatus(avgTotalTime, reqRatio);

        return CheckpointAgg1m.builder()
                .instanceId(instanceId)
                .collectedAt(toUtcOffsetDateTime(collectedAt))
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

    /**
     * 1분 집계 데이터를 기반으로 5분 집계 데이터 생성
     */
    private CheckpointAgg5m calculateAggregation5m(Long instanceId, LocalDateTime collectedAt,
                                                   List<CheckpointAgg1m> agg1mList) {
        // 1분 집계 데이터들을 합산/평균 계산
        long totalCheckpointsTimed = 0;
        long totalCheckpointsReq = 0;
        double sumWriteTime = 0.0;
        double sumSyncTime = 0.0;
        double sumTotalTime = 0.0;
        BigDecimal totalWalBytes = BigDecimal.ZERO;
        long totalBuffersCheckpoint = 0;
        int validDataCount = 0;  // 체크포인트가 발생한 데이터만 카운트

        for (CheckpointAgg1m agg1m : agg1mList) {
            totalCheckpointsTimed += (agg1m.getTotalCheckpointsTimed() != null ? agg1m.getTotalCheckpointsTimed() : 0);
            totalCheckpointsReq += (agg1m.getTotalCheckpointsReq() != null ? agg1m.getTotalCheckpointsReq() : 0);
            totalBuffersCheckpoint += (agg1m.getTotalBuffersCheckpoint() != null ? agg1m.getTotalBuffersCheckpoint() : 0);

            // 체크포인트가 발생한 경우만 평균 계산에 포함
            if (agg1m.getAvgTotalTime() != null && agg1m.getAvgTotalTime() > 0) {
                sumWriteTime += (agg1m.getAvgWriteTime() != null ? agg1m.getAvgWriteTime() : 0.0);
                sumSyncTime += (agg1m.getAvgSyncTime() != null ? agg1m.getAvgSyncTime() : 0.0);
                sumTotalTime += agg1m.getAvgTotalTime();
                validDataCount++;
            }

            if (agg1m.getTotalWalBytes() != null) {
                totalWalBytes = totalWalBytes.add(agg1m.getTotalWalBytes());
            }
        }

        // 평균 계산 (체크포인트가 발생한 데이터만 평균)
        double avgWriteTime = validDataCount > 0 ? sumWriteTime / validDataCount : 0.0;
        double avgSyncTime = validDataCount > 0 ? sumSyncTime / validDataCount : 0.0;
        double avgTotalTime = validDataCount > 0 ? sumTotalTime / validDataCount : 0.0;

        // 요청형 체크포인트 비율 계산
        long totalCheckpoints = totalCheckpointsTimed + totalCheckpointsReq;
        double reqRatio = 0.0;
        if (totalCheckpoints > 0) {
            reqRatio = (100.0 * totalCheckpointsReq) / totalCheckpoints;
        }

        // 상태 판단 (평균 총 시간 + 요청형 비율 고려)
        String status = determineCheckpointStatus(avgTotalTime, reqRatio);

        return CheckpointAgg5m.builder()
                .instanceId(instanceId)
                .collectedAt(toUtcOffsetDateTime(collectedAt))
                .avgCheckpointReqRatio(reqRatio)
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
     * LocalDateTime을 시스템 시간대 기준으로 UTC OffsetDateTime으로 변환
     */
    private OffsetDateTime toUtcOffsetDateTime(LocalDateTime localDateTime) {
        // LocalDateTime을 시스템 시간대(한국 시간)로 해석
        ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.systemDefault());
        // 같은 시점을 UTC로 변환
        OffsetDateTime utcDateTime = zonedDateTime.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
        log.debug("시간 변환: {} (로컬) → {} (UTC)", localDateTime, utcDateTime);
        return utcDateTime;
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

    /**
     * 안전한 증분 계산 (Long 타입)
     * stats_reset 발생 시 음수가 나오면 현재 값을 반환
     */
    private long calculateSafeDelta(Long current, Long previous) {
        if (current == null || previous == null) {
            return 0L;
        }
        long delta = current - previous;
        return delta >= 0 ? delta : current;
    }

    /**
     * 안전한 증분 계산 (Double 타입)
     * stats_reset 발생 시 음수가 나오면 현재 값을 반환
     */
    private double calculateSafeDelta(Double current, Double previous) {
        if (current == null || previous == null) {
            return 0.0;
        }
        double delta = current - previous;
        return delta >= 0 ? delta : current;
    }

    /**
     * 체크포인트 상태 판단
     * 평균 총 시간과 요청형 체크포인트 비율을 고려하여 상태 결정
     */
    private String determineCheckpointStatus(double avgTotalTime, double reqRatio) {
        // 요청형 체크포인트 비율이 높으면 위험 (checkpoint_segments 설정이 부족)
        if (reqRatio > 50.0) {
            return "위험";
        } else if (reqRatio > 30.0) {
            return "주의";
        }

        // 평균 처리 시간이 길면 위험
        if (avgTotalTime > 30.0) {
            return "위험";
        } else if (avgTotalTime > 15.0) {
            return "주의";
        }

        return "정상";
    }
}
