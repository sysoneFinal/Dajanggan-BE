package com.dajanggan.domain.engine.bgwriter.service;

import com.dajanggan.domain.engine.bgwriter.dto.*;
import com.dajanggan.domain.engine.bgwriter.repository.BgWriterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BgWriterService {

    private final BgWriterMapper bgWriterMapper;

    /**
     * BGWriter 대시보드 데이터 조회
     *
     * @param instanceId PostgreSQL 인스턴스 ID
     * @return BGWriter 대시보드 데이터
     */
    public BgWriterDashboardResponse getBgWriterDashboard(Long instanceId) {
        log.debug("BGWriter 대시보드 데이터 조회 시작 - instanceId: {}", instanceId);

        // instanceId가 null이면 예외 발생
        if (instanceId == null) {
            log.error("instanceId가 필수입니다");
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }

        // endTime을 약간 늦춰서 최신 데이터를 확실히 포함
        OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
        
        log.info("데이터 조회 시작 - instanceId: {}", instanceId);

        try {
            // 1. Backend Flush 비율 조회
            BgWriterDashboardResponse.BackendFlushRatio backendFlushRatio = getBackendFlushRatio(instanceId);

            // 2. Clean Rate 시계열 데이터 조회 (최근 10분, 1분 집계)
            OffsetDateTime cleanRateStart = endTime.minusMinutes(10);
            BgWriterDashboardResponse.CleanRate cleanRate = getCleanRate(instanceId, cleanRateStart, endTime);

            // 3. Buffer Flush 비율 비교 데이터 조회 (1시간, 5분 집계)
            OffsetDateTime bufferFlushStart = endTime.minusHours(1);
            BgWriterDashboardResponse.BufferFlushRatio bufferFlushRatio = getBufferFlushRatio(instanceId, bufferFlushStart, endTime);

            // 4. Maxwritten Clean 데이터 조회 (1시간, 5분 집계)
            OffsetDateTime maxwrittenStart = endTime.minusHours(1);
            BgWriterDashboardResponse.MaxwrittenClean maxwrittenClean = getMaxwrittenClean(instanceId, maxwrittenStart, endTime);

            // 5. BGWriter vs Checkpoint 비교 데이터 조회 (24시간, 30분 또는 5분 집계)
            OffsetDateTime bgwriterVsCheckpointStart = endTime.minusHours(24);
            BgWriterDashboardResponse.BgwriterVsCheckpoint bgwriterVsCheckpoint = getBgwriterVsCheckpoint(instanceId, bgwriterVsCheckpointStart, endTime);

            // 6. Buffer 재사용률 데이터 조회 (24시간, 30분 또는 5분 집계)
            OffsetDateTime bufferReuseStart = endTime.minusHours(24);
            BgWriterDashboardResponse.BufferReuseRate bufferReuseRate = getBufferReuseRate(instanceId, bufferReuseStart, endTime);

            // 7. 최근 통계 조회
            BgWriterDashboardResponse.RecentStats recentStats = getRecentStats(instanceId);

            return BgWriterDashboardResponse.builder()
                    .backendFlushRatio(backendFlushRatio)
                    .cleanRate(cleanRate)
                    .bufferFlushRatio(bufferFlushRatio)
                    .maxwrittenClean(maxwrittenClean)
                    .bgwriterVsCheckpoint(bgwriterVsCheckpoint)
                    .bufferReuseRate(bufferReuseRate)
                    .recentStats(recentStats)
                    .build();

        } catch (Exception e) {
            log.error("BGWriter 대시보드 데이터 조회 중 오류 발생", e);
            throw new RuntimeException("BGWriter 데이터 조회 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Backend Flush 비율 조회
     */
    private BgWriterDashboardResponse.BackendFlushRatio getBackendFlushRatio(Long instanceId) {
        Map<String, Object> data = bgWriterMapper.selectBackendFlushRatio(instanceId);

        if (data == null || data.isEmpty()) {
            log.warn("Backend Flush 비율 데이터 없음 - instanceId: {}", instanceId);
            return BgWriterDashboardResponse.BackendFlushRatio.builder()
                    .value(0.0)
                    .buffersClean(0L)
                    .buffersBackend(0L)
                    .build();
        }

        return BgWriterDashboardResponse.BackendFlushRatio.builder()
                .value(getDoubleValue(data, "value"))
                .buffersClean(getLongValue(data, "buffersclean"))
                .buffersBackend(getLongValue(data, "buffersbackend"))
                .build();
    }

    /**
     * Clean Rate 시계열 데이터 조회
     * 24시간 차트: 30분 집계 사용 (없으면 5분)
     */
    private BgWriterDashboardResponse.CleanRate getCleanRate(Long instanceId, OffsetDateTime startTime, OffsetDateTime endTime) {
        int intervalMinutes = determineIntervalMinutes(startTime, endTime);
        List<Map<String, Object>> dataList = bgWriterMapper.selectCleanRateTimeSeries(instanceId, startTime, endTime, intervalMinutes);

        if (dataList == null || dataList.isEmpty()) {
            log.warn("Clean Rate 데이터 없음 - instanceId: {}", instanceId);
            return createEmptyCleanRate();
        }

        List<String> categories = dataList.stream()
                .map(data -> getString(data, "time_label"))
                .collect(Collectors.toList());

        List<Double> values = dataList.stream()
                .map(data -> getDoubleValue(data, "clean_rate"))
                .collect(Collectors.toList());

        double average = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);

        return BgWriterDashboardResponse.CleanRate.builder()
                .categories(categories)
                .data(values)
                .average(average)
                .max(max)
                .min(min)
                .build();
    }

    /**
     * Buffer Flush 비율 시계열 데이터 조회
     * 24시간 차트: 30분 집계 사용 (없으면 5분)
     */
    private BgWriterDashboardResponse.BufferFlushRatio getBufferFlushRatio(Long instanceId, OffsetDateTime startTime, OffsetDateTime endTime) {
        int intervalMinutes = determineIntervalMinutes(startTime, endTime);
        List<Map<String, Object>> dataList = bgWriterMapper.selectBufferFlushRatioTimeSeries(instanceId, startTime, endTime, intervalMinutes);

        if (dataList == null || dataList.isEmpty()) {
            log.warn("Buffer Flush 비율 데이터 없음 - instanceId: {}", instanceId);
            return createEmptyBufferFlushRatio();
        }

        List<String> categories = dataList.stream()
                .map(data -> getString(data, "time_label"))
                .collect(Collectors.toList());

        List<Long> backend = dataList.stream()
                .map(data -> getLongValue(data, "backend"))
                .collect(Collectors.toList());

        List<Long> clean = dataList.stream()
                .map(data -> getLongValue(data, "clean"))
                .collect(Collectors.toList());

        long backendTotal = backend.stream().mapToLong(Long::longValue).sum();
        long cleanTotal = clean.stream().mapToLong(Long::longValue).sum();

        return BgWriterDashboardResponse.BufferFlushRatio.builder()
                .categories(categories)
                .backend(backend)
                .clean(clean)
                .backendTotal(backendTotal)
                .cleanTotal(cleanTotal)
                .build();
    }

    /**
     * Maxwritten Clean 시계열 데이터 조회
     * 24시간 차트: 30분 집계 사용 (없으면 5분)
     */
    private BgWriterDashboardResponse.MaxwrittenClean getMaxwrittenClean(Long instanceId, OffsetDateTime startTime, OffsetDateTime endTime) {
        int intervalMinutes = determineIntervalMinutes(startTime, endTime);
        List<Map<String, Object>> dataList = bgWriterMapper.selectMaxwrittenCleanTimeSeries(instanceId, startTime, endTime, intervalMinutes);

        if (dataList == null || dataList.isEmpty()) {
            log.warn("Maxwritten Clean 데이터 없음 - instanceId: {}", instanceId);
            return createEmptyMaxwrittenClean();
        }

        List<String> categories = dataList.stream()
                .map(data -> getString(data, "time_label"))
                .collect(Collectors.toList());

        List<Long> values = dataList.stream()
                .map(data -> getLongValue(data, "maxwritten_clean"))
                .collect(Collectors.toList());

        long total = values.stream().mapToLong(Long::longValue).sum();
        long average = (long) values.stream().mapToLong(Long::longValue).average().orElse(0.0);

        return BgWriterDashboardResponse.MaxwrittenClean.builder()
                .categories(categories)
                .data(values)
                .average(average)
                .total(total)
                .build();
    }

    /**
     * BGWriter vs Checkpoint 비교 데이터 조회
     * 24시간 차트: 30분 집계 사용 (없으면 5분)
     */
    private BgWriterDashboardResponse.BgwriterVsCheckpoint getBgwriterVsCheckpoint(Long instanceId, OffsetDateTime startTime, OffsetDateTime endTime) {
        int intervalMinutes = determineIntervalMinutes(startTime, endTime);
        List<Map<String, Object>> dataList = bgWriterMapper.selectBgwriterVsCheckpointTimeSeries(instanceId, startTime, endTime, intervalMinutes);

        if (dataList == null || dataList.isEmpty()) {
            log.warn("BGWriter vs Checkpoint 데이터 없음 - instanceId: {}", instanceId);
            return createEmptyBgwriterVsCheckpoint();
        }

        List<String> categories = dataList.stream()
                .map(data -> getString(data, "time_label"))
                .collect(Collectors.toList());

        List<Long> bgwriter = dataList.stream()
                .map(data -> getLongValue(data, "bgwriter"))
                .collect(Collectors.toList());

        List<Long> checkpoint = dataList.stream()
                .map(data -> getLongValue(data, "checkpoint"))
                .collect(Collectors.toList());

        long bgwriterTotal = bgwriter.stream().mapToLong(Long::longValue).sum();
        long checkpointTotal = checkpoint.stream().mapToLong(Long::longValue).sum();

        return BgWriterDashboardResponse.BgwriterVsCheckpoint.builder()
                .categories(categories)
                .bgwriter(bgwriter)
                .checkpoint(checkpoint)
                .bgwriterTotal(bgwriterTotal)
                .checkpointTotal(checkpointTotal)
                .build();
    }

    /**
     * Buffer 재사용률 시계열 데이터 조회
     * 24시간 차트: 30분 집계 사용 (없으면 5분)
     */
    private BgWriterDashboardResponse.BufferReuseRate getBufferReuseRate(Long instanceId, OffsetDateTime startTime, OffsetDateTime endTime) {
        int intervalMinutes = determineIntervalMinutes(startTime, endTime);
        List<Map<String, Object>> dataList = bgWriterMapper.selectBufferReuseRateTimeSeries(instanceId, startTime, endTime, intervalMinutes);

        if (dataList == null || dataList.isEmpty()) {
            log.warn("Buffer 재사용률 데이터 없음 - instanceId: {}", instanceId);
            return createEmptyBufferReuseRate();
        }

        List<String> categories = dataList.stream()
                .map(data -> getString(data, "time_label"))
                .collect(Collectors.toList());

        List<Double> values = dataList.stream()
                .map(data -> getDoubleValue(data, "reuse_rate"))
                .collect(Collectors.toList());

        double average = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);

        return BgWriterDashboardResponse.BufferReuseRate.builder()
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
    private BgWriterDashboardResponse.RecentStats getRecentStats(Long instanceId) {
        Map<String, Object> data = bgWriterMapper.selectRecentStats(instanceId);

        if (data == null || data.isEmpty()) {
            log.warn("최근 통계 데이터 없음 - instanceId: {}", instanceId);
            return createEmptyRecentStats();
        }

        return BgWriterDashboardResponse.RecentStats.builder()
                .bgwriterActivityRate(getDoubleValue(data, "bgwriteractivityrate"))
                .cleanBufferReuseRate(getDoubleValue(data, "cleanbufferreuserate"))
                .backendFsyncCount(getLongValue(data, "backendfsynccount"))
                .bufferPoolUsageRate(getDoubleValue(data, "bufferpoolusagerate"))
                .checkpointInterruptionCount(getLongValue(data, "checkpointinterruptioncount"))
                .dirtyBufferAccumulationRate(getDoubleValue(data, "dirtybufferaccumulationrate"))
                .build();
    }

    /**
     * BGWriter 리스트 데이터 조회
     *
     * @param instanceId PostgreSQL 인스턴스 ID
     * @param timeRange  시간 범위 (1h, 6h, 24h, 7d)
     * @param statusList 상태 필터 리스트
     * @return BGWriter 리스트 데이터
     */
    public BgWriterListResponse getBgWriterList(Long instanceId, String timeRange, List<String> statusList) {
        log.debug("BGWriter 리스트 데이터 조회 시작 - instanceId: {}, timeRange: {}, statusList: {}",
                instanceId, timeRange, statusList);

        // instanceId가 null이면 예외 발생
        if (instanceId == null) {
            log.error("instanceId가 필수입니다");
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }

        // 시간 범위 계산
        // endTime을 약간 늦춰서 최신 데이터를 확실히 포함
        OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
        OffsetDateTime startTime = calculateStartTime(endTime, timeRange);
        
        log.info("리스트 데이터 조회 범위: {} ~ {} (최신 데이터 포함)", startTime, endTime);

        try {
            // 리스트 데이터 조회
            List<Map<String, Object>> dataList = bgWriterMapper.selectBgWriterList(
                    instanceId,
                    startTime,
                    endTime,
                    statusList
            );

            if (dataList == null || dataList.isEmpty()) {
                log.warn("BGWriter 리스트 데이터 없음 - instanceId: {}, timeRange: {}", instanceId, timeRange);
                return BgWriterListResponse.builder()
                        .data(new ArrayList<>())
                        .total(0L)
                        .build();
            }

            // DTO 변환
            List<BgWriterListItem> items = dataList.stream()
                    .map(this::convertToListItem)
                    .collect(Collectors.toList());

            return BgWriterListResponse.builder()
                    .data(items)
                    .total((long) items.size())
                    .build();

        } catch (Exception e) {
            log.error("BGWriter 리스트 데이터 조회 중 오류 발생", e);
            throw new RuntimeException("BGWriter 리스트 데이터 조회 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 시간 범위 문자열을 기준으로 시작 시간 계산
     *
     * @param endTime   종료 시간
     * @param timeRange 시간 범위 (1h, 6h, 24h, 7d)
     * @return 시작 시간
     */
    private OffsetDateTime calculateStartTime(OffsetDateTime endTime, String timeRange) {
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
     *
     * @param data Map 데이터
     * @return ListItem DTO
     */
    private BgWriterListItem convertToListItem(Map<String, Object> data) {
        return BgWriterListItem.builder()
                .id(getString(data, "id"))
                .timestamp(getString(data, "timestamp"))
                .buffersAlloc(getLongValue(data, "buffersalloc"))
                .cleanRate(getDoubleValue(data, "cleanrate"))
                .backendRate(getDoubleValue(data, "backendrate"))
                .checkpointBuffers(0L)  // checkpoint 데이터는 일단 0으로 처리
                .backendRatio(getDoubleValue(data, "backendratio"))
                .fsyncRate(getDoubleValue(data, "fsyncrate"))
                .maxWrittenRate(getDoubleValue(data, "maxwrittenrate"))
                .avgCycleTime(getDoubleValue(data, "avgcycletime"))
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

    private BgWriterDashboardResponse.CleanRate createEmptyCleanRate() {
        return BgWriterDashboardResponse.CleanRate.builder()
                .categories(new ArrayList<>())
                .data(new ArrayList<>())
                .average(0.0)
                .max(0.0)
                .min(0.0)
                .build();
    }

    private BgWriterDashboardResponse.BufferFlushRatio createEmptyBufferFlushRatio() {
        return BgWriterDashboardResponse.BufferFlushRatio.builder()
                .categories(new ArrayList<>())
                .backend(new ArrayList<>())
                .clean(new ArrayList<>())
                .backendTotal(0L)
                .cleanTotal(0L)
                .build();
    }

    private BgWriterDashboardResponse.MaxwrittenClean createEmptyMaxwrittenClean() {
        return BgWriterDashboardResponse.MaxwrittenClean.builder()
                .categories(new ArrayList<>())
                .data(new ArrayList<>())
                .average(0L)
                .total(0L)
                .build();
    }

    private BgWriterDashboardResponse.BgwriterVsCheckpoint createEmptyBgwriterVsCheckpoint() {
        return BgWriterDashboardResponse.BgwriterVsCheckpoint.builder()
                .categories(new ArrayList<>())
                .bgwriter(new ArrayList<>())
                .checkpoint(new ArrayList<>())
                .bgwriterTotal(0L)
                .checkpointTotal(0L)
                .build();
    }

    private BgWriterDashboardResponse.BufferReuseRate createEmptyBufferReuseRate() {
        return BgWriterDashboardResponse.BufferReuseRate.builder()
                .categories(new ArrayList<>())
                .data(new ArrayList<>())
                .average(0.0)
                .max(0.0)
                .min(0.0)
                .build();
    }

    private BgWriterDashboardResponse.RecentStats createEmptyRecentStats() {
        return BgWriterDashboardResponse.RecentStats.builder()
                .bgwriterActivityRate(0.0)
                .cleanBufferReuseRate(0.0)
                .backendFsyncCount(0L)
                .bufferPoolUsageRate(0.0)
                .checkpointInterruptionCount(0L)
                .dirtyBufferAccumulationRate(0.0)
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
    private int determineIntervalMinutes(OffsetDateTime startTime, OffsetDateTime endTime) {
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
