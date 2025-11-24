package com.dajanggan.domain.engine.checkpoint.service;

import com.dajanggan.domain.engine.checkpoint.dto.*;
import com.dajanggan.domain.engine.checkpoint.repository.CheckpointMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckpointService {

    private final CheckpointMapper checkpointMapper;

    /**
     * Checkpoint 대시보드 데이터 조회
     * @param instanceId PostgreSQL 인스턴스 ID
     * @return Checkpoint 대시보드 데이터
     */
    public CheckpointDashboardResponse getCheckpointDashboard(Long instanceId) {
        log.debug("Checkpoint 대시보드 데이터 조회 시작 - instanceId: {}", instanceId);

        // instanceId가 null이면 예외 발생
        if (instanceId == null) {
            log.error("instanceId가 필수입니다");
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }

        // endTime을 약간 늦춰서 최신 데이터를 확실히 포함
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(1);

        log.info("데이터 조회 시작 - instanceId: {}", instanceId);

        try {
            // 1. 요청형 체크포인트 비율 조회
            CheckpointDashboardResponse.RequestRatio requestRatio = getRequestRatio(instanceId);

            // 모든 차트를 15분 데이터로 변경 (checkpoint_agg_1m 사용)
            LocalDateTime chartStart = endTime.minusMinutes(15);

            // 2. 평균 쓰기 시간 시계열 데이터 조회 (최근 15분, 1분 집계)
            CheckpointDashboardResponse.AvgWriteTime avgWriteTime = getAvgWriteTime(instanceId, chartStart, endTime);

            // 3. 체크포인트 발생 횟수 데이터 조회 (최근 15분, 1분 집계)
            CheckpointDashboardResponse.Occurrence occurrence = getOccurrence(instanceId, chartStart, endTime);

            // 4. WAL 생성량 데이터 조회 (최근 15분, 1분 집계)
            CheckpointDashboardResponse.WalGeneration walGeneration = getWalGeneration(instanceId, chartStart, endTime);

            // 5. 처리 시간 데이터 조회 (최근 15분, 1분 집계)
            CheckpointDashboardResponse.ProcessTime processTime = getProcessTime(instanceId, chartStart, endTime);

            // 6. 버퍼 처리량 데이터 조회 (최근 15분, 1분 집계)
            CheckpointDashboardResponse.Buffer buffer = getBuffer(instanceId, chartStart, endTime);

            // 7. 체크포인트 간격 데이터 조회 (최근 15분, 1분 집계)
            CheckpointDashboardResponse.CheckpointInterval checkpointInterval = getCheckpointInterval(instanceId, chartStart, endTime);

            // 8. 위젯용 최근 통계 (15분)
            CheckpointDashboardResponse.RecentStats recentStats = getRecentStats15m(instanceId, chartStart, endTime);

            // 모든 데이터를 15분 데이터로 통일
            return CheckpointDashboardResponse.builder()
                    .requestRatio(requestRatio)
                    .avgWriteTime(avgWriteTime)
                    .occurrence(occurrence)
                    .walGeneration(walGeneration)
                    .processTime(processTime)
                    .buffer(buffer)
                    .checkpointInterval(checkpointInterval)
                    .recentStats(recentStats)
                    .build();

        } catch (Exception e) {
            log.error("Checkpoint 대시보드 데이터 조회 중 오류 발생", e);
            throw new RuntimeException("Checkpoint 데이터 조회 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 요청형 체크포인트 비율 조회
     */
    private CheckpointDashboardResponse.RequestRatio getRequestRatio(Long instanceId) {
        Map<String, Object> data = checkpointMapper.selectRequestRatio(instanceId);

        if (data == null || data.isEmpty()) {
            log.warn("요청형 체크포인트 비율 데이터 없음 - instanceId: {}", instanceId);
            return CheckpointDashboardResponse.RequestRatio.builder()
                    .value(0.0)
                    .requestedCount(0L)
                    .timedCount(0L)
                    .build();
        }

        return CheckpointDashboardResponse.RequestRatio.builder()
                .value(getDoubleValue(data, "value"))
                .requestedCount(getLongValue(data, "requestedcount"))
                .timedCount(getLongValue(data, "timedcount"))
                .build();
    }

    /**
     * 평균 쓰기 시간 시계열 데이터 조회 (15분, checkpoint_agg_1m 사용)
     */
    private CheckpointDashboardResponse.AvgWriteTime getAvgWriteTime(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> dataList = checkpointMapper.selectAvgWriteTimeTimeSeries(instanceId, startTime, endTime, 1);

        if (dataList == null || dataList.isEmpty()) {
            log.warn("평균 쓰기 시간 데이터 없음 - instanceId: {}", instanceId);
            return createEmptyAvgWriteTime();
        }

        List<String> categories = dataList.stream()
                .map(data -> getString(data, "time_label"))
                .collect(Collectors.toList());

        List<Double> values = dataList.stream()
                .map(data -> getDoubleValue(data, "avg_write_time"))
                .collect(Collectors.toList());

        double average = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);

        return CheckpointDashboardResponse.AvgWriteTime.builder()
                .categories(categories)
                .data(values)
                .average(average)
                .max(max)
                .min(min)
                .build();
    }

    /**
     * 체크포인트 발생 횟수 시계열 데이터 조회 (15분, checkpoint_agg_1m 사용)
     */
    private CheckpointDashboardResponse.Occurrence getOccurrence(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> dataList = checkpointMapper.selectOccurrenceTimeSeries(instanceId, startTime, endTime, 1);

        if (dataList == null || dataList.isEmpty()) {
            log.warn("체크포인트 발생 횟수 데이터 없음 - instanceId: {}", instanceId);
            return createEmptyOccurrence();
        }

        List<String> categories = dataList.stream()
                .map(data -> getString(data, "time_label"))
                .collect(Collectors.toList());

        List<Long> requested = dataList.stream()
                .map(data -> getLongValue(data, "requested"))
                .collect(Collectors.toList());

        List<Long> timed = dataList.stream()
                .map(data -> getLongValue(data, "timed"))
                .collect(Collectors.toList());

        long requestedTotal = requested.stream().mapToLong(Long::longValue).sum();
        long timedTotal = timed.stream().mapToLong(Long::longValue).sum();

        double ratio = 0.0;
        if (requestedTotal + timedTotal > 0) {
            ratio = (double) requestedTotal / (requestedTotal + timedTotal) * 100.0;
        }

        return CheckpointDashboardResponse.Occurrence.builder()
                .categories(categories)
                .requested(requested)
                .timed(timed)
                .requestedTotal(requestedTotal)
                .timedTotal(timedTotal)
                .ratio(ratio)
                .build();
    }

    /**
     * WAL 생성량 시계열 데이터 조회 (15분, checkpoint_agg_1m 사용)
     */
    private CheckpointDashboardResponse.WalGeneration getWalGeneration(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> dataList = checkpointMapper.selectWalGenerationTimeSeries(instanceId, startTime, endTime, 1);

        if (dataList == null || dataList.isEmpty()) {
            log.warn("WAL 생성량 데이터 없음 - instanceId: {}", instanceId);
            return createEmptyWalGeneration();
        }

        List<String> categories = dataList.stream()
                .map(data -> getString(data, "time_label"))
                .collect(Collectors.toList());

        List<Long> values = dataList.stream()
                .map(data -> getLongValue(data, "wal_bytes"))
                .collect(Collectors.toList());

        long total = values.stream().mapToLong(Long::longValue).sum();
        double average = values.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long max = values.stream().mapToLong(Long::longValue).max().orElse(0L);

        return CheckpointDashboardResponse.WalGeneration.builder()
                .categories(categories)
                .data(values)
                .total(total)
                .average(average)
                .max(max)
                .build();
    }

    /**
     * 처리 시간 시계열 데이터 조회 (15분, checkpoint_agg_1m 사용)
     */
    private CheckpointDashboardResponse.ProcessTime getProcessTime(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> dataList = checkpointMapper.selectProcessTimeTimeSeries(instanceId, startTime, endTime, 1);

        if (dataList == null || dataList.isEmpty()) {
            log.warn("처리 시간 데이터 없음 - instanceId: {}", instanceId);
            return createEmptyProcessTime();
        }

        List<String> categories = dataList.stream()
                .map(data -> getString(data, "time_label"))
                .collect(Collectors.toList());

        List<Double> syncTime = dataList.stream()
                .map(data -> getDoubleValue(data, "sync_time"))
                .collect(Collectors.toList());

        List<Double> writeTime = dataList.stream()
                .map(data -> getDoubleValue(data, "write_time"))
                .collect(Collectors.toList());

        double avgSync = syncTime.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgWrite = writeTime.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgTotal = avgSync + avgWrite;

        return CheckpointDashboardResponse.ProcessTime.builder()
                .categories(categories)
                .syncTime(syncTime)
                .writeTime(writeTime)
                .avgSync(avgSync)
                .avgWrite(avgWrite)
                .avgTotal(avgTotal)
                .build();
    }

    /**
     * 버퍼 처리량 시계열 데이터 조회 (15분, checkpoint_agg_1m 사용)
     */
    private CheckpointDashboardResponse.Buffer getBuffer(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> dataList = checkpointMapper.selectBufferTimeSeries(instanceId, startTime, endTime, 1);

        if (dataList == null || dataList.isEmpty()) {
            log.warn("버퍼 처리량 데이터 없음 - instanceId: {}", instanceId);
            return createEmptyBuffer();
        }

        List<String> categories = dataList.stream()
                .map(data -> getString(data, "time_label"))
                .collect(Collectors.toList());

        List<Double> values = dataList.stream()
                .map(data -> getDoubleValue(data, "buffers_per_sec"))
                .collect(Collectors.toList());

        double average = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);

        return CheckpointDashboardResponse.Buffer.builder()
                .categories(categories)
                .data(values)
                .average(average)
                .max(max)
                .min(min)
                .build();
    }

    /**
     * 체크포인트 간격 시계열 데이터 조회 (15분, checkpoint_agg_1m 사용)
     */
    private CheckpointDashboardResponse.CheckpointInterval getCheckpointInterval(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> dataList = checkpointMapper.selectCheckpointIntervalTimeSeries(instanceId, startTime, endTime, 1);

        if (dataList == null || dataList.isEmpty()) {
            log.warn("체크포인트 간격 데이터 없음 - instanceId: {}", instanceId);
            return createEmptyCheckpointInterval();
        }

        List<String> categories = dataList.stream()
                .map(data -> getString(data, "time_label"))
                .collect(Collectors.toList());

        List<Double> values = dataList.stream()
                .map(data -> getDoubleValue(data, "interval_minutes"))
                .collect(Collectors.toList());

        double average = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);

        return CheckpointDashboardResponse.CheckpointInterval.builder()
                .categories(categories)
                .data(values)
                .average(average)
                .max(max)
                .min(min)
                .build();
    }

    /**
     * 최근 통계 조회
     */
    private CheckpointDashboardResponse.RecentStats getRecentStats(Long instanceId) {
        Map<String, Object> data = checkpointMapper.selectRecentStats(instanceId);

        if (data == null || data.isEmpty()) {
            log.warn("최근 통계 데이터 없음 - instanceId: {}", instanceId);
            return createEmptyRecentStats();
        }

        return CheckpointDashboardResponse.RecentStats.builder()
                .buffersWritten(getLongValue(data, "bufferswritten"))
                .avgTotalProcessTime(getDoubleValue(data, "avgtotalprocesstime"))
                .checkpointDistance(getDoubleValue(data, "checkpointdistance"))
                .checkpointInterval(getDoubleValue(data, "checkpointinterval"))
                .avgWalGenerationSpeed(getDoubleValue(data, "avgwalgenerationspeed"))
                .build();
    }

    /**
     * 최근 통계 조회 (15분, checkpoint_agg_1m 사용)
     */
    private CheckpointDashboardResponse.RecentStats getRecentStats15m(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> data = checkpointMapper.selectRecentStats15m(instanceId, startTime, endTime);

        if (data == null || data.isEmpty()) {
            log.warn("최근 통계 데이터 없음 (15분) - instanceId: {}", instanceId);
            return createEmptyRecentStats();
        }

        return CheckpointDashboardResponse.RecentStats.builder()
                .buffersWritten(getLongValue(data, "bufferswritten"))
                .avgTotalProcessTime(getDoubleValue(data, "avgtotalprocesstime"))
                .checkpointDistance(getDoubleValue(data, "checkpointdistance"))
                .checkpointInterval(getDoubleValue(data, "checkpointinterval"))
                .avgWalGenerationSpeed(getDoubleValue(data, "avgwalgenerationspeed"))
                .build();
    }

    /**
     * 체크포인트 발생 횟수 위젯 조회 (15분, checkpoint_agg_1m 사용)
     */
    private CheckpointDashboardResponse.Occurrence getOccurrenceWidget15m(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> data = checkpointMapper.selectOccurrenceWidget15m(instanceId, startTime, endTime);

        if (data == null || data.isEmpty()) {
            log.warn("체크포인트 발생 횟수 위젯 데이터 없음 (15분) - instanceId: {}", instanceId);
            return createEmptyOccurrence();
        }

        long requestedTotal = getLongValue(data, "requestedtotal");
        long timedTotal = getLongValue(data, "timedtotal");
        double ratio = 0.0;
        if (requestedTotal + timedTotal > 0) {
            ratio = (double) requestedTotal / (requestedTotal + timedTotal) * 100.0;
        }

        return CheckpointDashboardResponse.Occurrence.builder()
                .categories(new ArrayList<>())
                .requested(new ArrayList<>())
                .timed(new ArrayList<>())
                .requestedTotal(requestedTotal)
                .timedTotal(timedTotal)
                .ratio(ratio)
                .build();
    }

    /**
     * WAL 생성량 위젯 조회 (15분, checkpoint_agg_1m 사용)
     */
    private CheckpointDashboardResponse.WalGeneration getWalGenerationWidget15m(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> data = checkpointMapper.selectWalGenerationWidget15m(instanceId, startTime, endTime);

        if (data == null || data.isEmpty()) {
            log.warn("WAL 생성량 위젯 데이터 없음 (15분) - instanceId: {}", instanceId);
            return createEmptyWalGeneration();
        }

        long total = getLongValue(data, "total");
        double average = getDoubleValue(data, "average");
        long max = getLongValue(data, "max");

        return CheckpointDashboardResponse.WalGeneration.builder()
                .categories(new ArrayList<>())
                .data(new ArrayList<>())
                .total(total)
                .average(average)
                .max(max)
                .build();
    }

    /**
     * 버퍼 처리량 위젯 조회 (15분, checkpoint_agg_1m 사용)
     */
    private CheckpointDashboardResponse.Buffer getBufferWidget15m(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> data = checkpointMapper.selectBufferWidget15m(instanceId, startTime, endTime);

        if (data == null || data.isEmpty()) {
            log.warn("버퍼 처리량 위젯 데이터 없음 (15분) - instanceId: {}", instanceId);
            return createEmptyBuffer();
        }

        double average = getDoubleValue(data, "average");
        double max = getDoubleValue(data, "max");
        double min = getDoubleValue(data, "min");

        return CheckpointDashboardResponse.Buffer.builder()
                .categories(new ArrayList<>())
                .data(new ArrayList<>())
                .average(average)
                .max(max)
                .min(min)
                .build();
    }

    /**
     * Checkpoint 리스트 데이터 조회
     * @param instanceId PostgreSQL 인스턴스 ID
     * @param timeRange 시간 범위 (1h, 6h, 24h, 7d)
     * @param statusList 상태 필터 리스트
     * @return Checkpoint 리스트 데이터
     */
    public CheckpointListResponse getCheckpointList(Long instanceId, String timeRange, List<String> statusList, Integer page, Integer size) {
        log.debug("Checkpoint 리스트 데이터 조회 시작 - instanceId: {}, timeRange: {}, statusList: {}, page: {}, size: {}",
                instanceId, timeRange, statusList, page, size);

        // instanceId가 null이면 예외 발생
        if (instanceId == null) {
            log.error("instanceId가 필수입니다");
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }

        // 페이징 파라미터 설정 (기본값: page=0, size=20)
        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : 20;
        if (pageNum < 0) pageNum = 0;
        if (pageSize < 1) pageSize = 20;
        if (pageSize > 100) pageSize = 100; // 최대 100개로 제한
        int offset = pageNum * pageSize;

        // 시간 범위 계산
        // endTime을 약간 늦춰서 최신 데이터를 확실히 포함
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(1);
        LocalDateTime startTime = calculateStartTime(endTime, timeRange);

        log.info("리스트 데이터 조회 범위: {} ~ {} (최신 데이터 포함), 페이징: page={}, size={}",
                startTime, endTime, pageNum, pageSize);

        try {
            // 총 개수 조회
            int intervalMinutes = determineIntervalMinutes(startTime, endTime);
            Long totalCount = checkpointMapper.countCheckpointList(instanceId, startTime, endTime, statusList, intervalMinutes);

            // 페이징된 리스트 데이터 조회
            List<Map<String, Object>> dataList = checkpointMapper.selectCheckpointListWithPaging(
                    instanceId,
                    startTime,
                    endTime,
                    statusList,
                    intervalMinutes,
                    offset,
                    pageSize
            );

            // DTO 변환
            List<CheckpointListItem> items = new ArrayList<>();
            if (dataList != null && !dataList.isEmpty()) {
                items = dataList.stream()
                        .map(this::convertToListItem)
                        .collect(Collectors.toList());
            }

            // 총 페이지 수 계산
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);

            return CheckpointListResponse.builder()
                    .data(items)
                    .total(totalCount)
                    .page(pageNum)
                    .size(pageSize)
                    .totalPages(totalPages)
                    .build();

        } catch (Exception e) {
            log.error("Checkpoint 리스트 데이터 조회 중 오류 발생", e);
            throw new RuntimeException("Checkpoint 리스트 데이터 조회 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 시간 범위 문자열을 기준으로 시작 시간 계산
     * @param endTime 종료 시간
     * @param timeRange 시간 범위 (1h, 6h, 24h, 7d)
     * @return 시작 시간
     */
    private LocalDateTime calculateStartTime(LocalDateTime endTime, String timeRange) {
        if (timeRange == null || timeRange.isEmpty()) {
            timeRange = "1h"; // 기본값
        }

        switch (timeRange.toLowerCase()) {
            case "1h":
                return endTime.minusHours(1);
            case "6h":
                return endTime.minusHours(6);
            case "24h":
                return endTime.minusHours(24);
            case "7d":
                return endTime.minusDays(7);
            default:
                log.warn("알 수 없는 시간 범위: {}. 기본값(1h) 사용", timeRange);
                return endTime.minusHours(1);
        }
    }

    /**
     * Map 데이터를 ListItem DTO로 변환
     * @param data Map 데이터
     * @return ListItem DTO
     */
    private CheckpointListItem convertToListItem(Map<String, Object> data) {
        return CheckpointListItem.builder()
                .id(getString(data, "id"))
                .timestamp(getString(data, "timestamp"))
                .type(getString(data, "type"))
                .writeTime(getDoubleValue(data, "writetime"))
                .syncTime(getDoubleValue(data, "synctime"))
                .totalTime(getDoubleValue(data, "totaltime"))
                .walGenerated(getString(data, "walgenerated"))
                .walFilesAdded(getLongValue(data, "walfilesadded"))
                .walFilesRemoved(getLongValue(data, "walfilesremoved"))
                .checkpointDistance(getString(data, "checkpointdistance"))
                .buffersWritten(getLongValue(data, "bufferswritten"))
                .buffersBackend(getLongValue(data, "buffersbackend"))
                .avgBuffersPerSec(getDoubleValue(data, "avgbufferspersec"))
                .status(getString(data, "status"))
                .build();
    }

    // ========== 유틸리티 메서드 ==========

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : "";
    }

    private Double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0.0;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Double 변환 실패: key={}, value={}", key, value);
            return 0.0;
        }
    }

    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0L;
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Long 변환 실패: key={}, value={}", key, value);
            return 0L;
        }
    }

    // ========== 빈 데이터 생성 메서드 ==========

    private CheckpointDashboardResponse.AvgWriteTime createEmptyAvgWriteTime() {
        return CheckpointDashboardResponse.AvgWriteTime.builder()
                .categories(new ArrayList<>())
                .data(new ArrayList<>())
                .average(0.0)
                .max(0.0)
                .min(0.0)
                .build();
    }

    private CheckpointDashboardResponse.Occurrence createEmptyOccurrence() {
        return CheckpointDashboardResponse.Occurrence.builder()
                .categories(new ArrayList<>())
                .requested(new ArrayList<>())
                .timed(new ArrayList<>())
                .requestedTotal(0L)
                .timedTotal(0L)
                .ratio(0.0)
                .build();
    }

    private CheckpointDashboardResponse.WalGeneration createEmptyWalGeneration() {
        return CheckpointDashboardResponse.WalGeneration.builder()
                .categories(new ArrayList<>())
                .data(new ArrayList<>())
                .total(0L)
                .average(0.0)
                .max(0L)
                .build();
    }

    private CheckpointDashboardResponse.ProcessTime createEmptyProcessTime() {
        return CheckpointDashboardResponse.ProcessTime.builder()
                .categories(new ArrayList<>())
                .syncTime(new ArrayList<>())
                .writeTime(new ArrayList<>())
                .avgSync(0.0)
                .avgWrite(0.0)
                .avgTotal(0.0)
                .build();
    }

    private CheckpointDashboardResponse.Buffer createEmptyBuffer() {
        return CheckpointDashboardResponse.Buffer.builder()
                .categories(new ArrayList<>())
                .data(new ArrayList<>())
                .average(0.0)
                .max(0.0)
                .min(0.0)
                .build();
    }

    private CheckpointDashboardResponse.CheckpointInterval createEmptyCheckpointInterval() {
        return CheckpointDashboardResponse.CheckpointInterval.builder()
                .categories(new ArrayList<>())
                .data(new ArrayList<>())
                .average(0.0)
                .max(0.0)
                .min(0.0)
                .build();
    }

    private CheckpointDashboardResponse.RecentStats createEmptyRecentStats() {
        return CheckpointDashboardResponse.RecentStats.builder()
                .buffersWritten(0L)
                .avgTotalProcessTime(0.0)
                .checkpointDistance(0.0)
                .checkpointInterval(0.0)
                .avgWalGenerationSpeed(0.0)
                .build();
    }

    /**
     * 시간 범위에 따라 집계 간격 결정
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 집계 간격 (분 단위: 1, 5, 또는 30)
     * - 1시간 이내: 1분 집계
     * - 6시간 이내: 5분 집계
     * - 24시간: 30분 집계 (없으면 5분 집계로 fallback)
     */
    private int determineIntervalMinutes(LocalDateTime startTime, LocalDateTime endTime) {
        long hours = java.time.Duration.between(startTime, endTime).toHours();
        if (hours <= 1) {
            return 1; // 1시간 차트: 1분 집계
        } else if (hours <= 6) {
            return 5; // 6시간 차트: 5분 집계
        } else {
            return 30; // 24시간 차트: 30분 집계 (없으면 5분으로 fallback)
        }
    }
}