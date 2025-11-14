package com.dajanggan.domain.engine.bgwriter.service;

import com.dajanggan.domain.engine.bgwriter.dto.BgWriterDto;
import com.dajanggan.domain.engine.bgwriter.repository.BgWriterMapper;
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
public class BgWriterService {

    private final BgWriterMapper bgWriterMapper;

    /**
     * BGWriter 대시보드 데이터 조회
     *
     * @param instanceId PostgreSQL 인스턴스 ID
     * @return BGWriter 대시보드 데이터
     */
    public BgWriterDto.DashboardResponse getBgWriterDashboard(Long instanceId) {
        log.debug("BGWriter 대시보드 데이터 조회 시작 - instanceId: {}", instanceId);

        // instanceId가 null이면 예외 발생
        if (instanceId == null) {
            log.error("instanceId가 필수입니다");
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }

        // 조회 시간 범위 설정 (최근 24시간)
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(24);

        log.info("데이터 조회 범위: {} ~ {}", startTime, endTime);

        try {
            // 1. Backend Flush 비율 조회
            BgWriterDto.BackendFlushRatio backendFlushRatio = getBackendFlushRatio(instanceId);

            // 2. Clean Rate 시계열 데이터 조회
            BgWriterDto.CleanRate cleanRate = getCleanRate(instanceId, startTime, endTime);

            // 3. Buffer Flush 비율 비교 데이터 조회
            BgWriterDto.BufferFlushRatio bufferFlushRatio = getBufferFlushRatio(instanceId, startTime, endTime);

            // 4. Maxwritten Clean 데이터 조회
            BgWriterDto.MaxwrittenClean maxwrittenClean = getMaxwrittenClean(instanceId, startTime, endTime);

            // 5. BGWriter vs Checkpoint 비교 데이터 조회
            BgWriterDto.BgwriterVsCheckpoint bgwriterVsCheckpoint = getBgwriterVsCheckpoint(instanceId, startTime, endTime);

            // 6. Buffer 재사용률 데이터 조회
            BgWriterDto.BufferReuseRate bufferReuseRate = getBufferReuseRate(instanceId, startTime, endTime);

            // 7. 최근 통계 조회
            BgWriterDto.RecentStats recentStats = getRecentStats(instanceId);

            return BgWriterDto.DashboardResponse.builder()
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
    private BgWriterDto.BackendFlushRatio getBackendFlushRatio(Long instanceId) {
        Map<String, Object> data = bgWriterMapper.selectBackendFlushRatio(instanceId);

        if (data == null || data.isEmpty()) {
            log.warn("Backend Flush 비율 데이터 없음 - instanceId: {}", instanceId);
            return BgWriterDto.BackendFlushRatio.builder()
                    .value(0.0)
                    .buffersClean(0L)
                    .buffersBackend(0L)
                    .build();
        }

        return BgWriterDto.BackendFlushRatio.builder()
                .value(getDoubleValue(data, "value"))
                .buffersClean(getLongValue(data, "buffersclean"))
                .buffersBackend(getLongValue(data, "buffersbackend"))
                .build();
    }

    /**
     * Clean Rate 시계열 데이터 조회
     */
    private BgWriterDto.CleanRate getCleanRate(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> dataList = bgWriterMapper.selectCleanRateTimeSeries(instanceId, startTime, endTime);

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

        return BgWriterDto.CleanRate.builder()
                .categories(categories)
                .data(values)
                .average(average)
                .max(max)
                .min(min)
                .build();
    }

    /**
     * Buffer Flush 비율 시계열 데이터 조회
     */
    private BgWriterDto.BufferFlushRatio getBufferFlushRatio(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> dataList = bgWriterMapper.selectBufferFlushRatioTimeSeries(instanceId, startTime, endTime);

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

        return BgWriterDto.BufferFlushRatio.builder()
                .categories(categories)
                .backend(backend)
                .clean(clean)
                .backendTotal(backendTotal)
                .cleanTotal(cleanTotal)
                .build();
    }

    /**
     * Maxwritten Clean 시계열 데이터 조회
     */
    private BgWriterDto.MaxwrittenClean getMaxwrittenClean(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> dataList = bgWriterMapper.selectMaxwrittenCleanTimeSeries(instanceId, startTime, endTime);

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

        return BgWriterDto.MaxwrittenClean.builder()
                .categories(categories)
                .data(values)
                .average(average)
                .total(total)
                .build();
    }

    /**
     * BGWriter vs Checkpoint 비교 데이터 조회
     */
    private BgWriterDto.BgwriterVsCheckpoint getBgwriterVsCheckpoint(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> dataList = bgWriterMapper.selectBgwriterVsCheckpointTimeSeries(instanceId, startTime, endTime);

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

        return BgWriterDto.BgwriterVsCheckpoint.builder()
                .categories(categories)
                .bgwriter(bgwriter)
                .checkpoint(checkpoint)
                .bgwriterTotal(bgwriterTotal)
                .checkpointTotal(checkpointTotal)
                .build();
    }

    /**
     * Buffer 재사용률 시계열 데이터 조회
     */
    private BgWriterDto.BufferReuseRate getBufferReuseRate(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> dataList = bgWriterMapper.selectBufferReuseRateTimeSeries(instanceId, startTime, endTime);

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

        return BgWriterDto.BufferReuseRate.builder()
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
    private BgWriterDto.RecentStats getRecentStats(Long instanceId) {
        Map<String, Object> data = bgWriterMapper.selectRecentStats(instanceId);

        if (data == null || data.isEmpty()) {
            log.warn("최근 통계 데이터 없음 - instanceId: {}", instanceId);
            return createEmptyRecentStats();
        }

        return BgWriterDto.RecentStats.builder()
                .bgwriterActivityRate(getDoubleValue(data, "bgwriteractivityrate"))
                .cleanBufferReuseRate(getDoubleValue(data, "cleanbufferreuserate"))
                .backendFsyncCount(getLongValue(data, "backendfsyncount"))
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
    public BgWriterDto.ListResponse getBgWriterList(Long instanceId, String timeRange, List<String> statusList) {
        log.debug("BGWriter 리스트 데이터 조회 시작 - instanceId: {}, timeRange: {}, statusList: {}",
                instanceId, timeRange, statusList);

        // instanceId가 null이면 예외 발생
        if (instanceId == null) {
            log.error("instanceId가 필수입니다");
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }

        // 시간 범위 계산
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = calculateStartTime(endTime, timeRange);

        log.info("리스트 데이터 조회 범위: {} ~ {}", startTime, endTime);

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
                return BgWriterDto.ListResponse.builder()
                        .data(new ArrayList<>())
                        .total(0L)
                        .build();
            }

            // DTO 변환
            List<BgWriterDto.ListItem> items = dataList.stream()
                    .map(this::convertToListItem)
                    .collect(Collectors.toList());

            return BgWriterDto.ListResponse.builder()
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
     *
     * @param data Map 데이터
     * @return ListItem DTO
     */
    private BgWriterDto.ListItem convertToListItem(Map<String, Object> data) {
        return BgWriterDto.ListItem.builder()
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

    private BgWriterDto.CleanRate createEmptyCleanRate() {
        return BgWriterDto.CleanRate.builder()
                .categories(new ArrayList<>())
                .data(new ArrayList<>())
                .average(0.0)
                .max(0.0)
                .min(0.0)
                .build();
    }

    private BgWriterDto.BufferFlushRatio createEmptyBufferFlushRatio() {
        return BgWriterDto.BufferFlushRatio.builder()
                .categories(new ArrayList<>())
                .backend(new ArrayList<>())
                .clean(new ArrayList<>())
                .backendTotal(0L)
                .cleanTotal(0L)
                .build();
    }

    private BgWriterDto.MaxwrittenClean createEmptyMaxwrittenClean() {
        return BgWriterDto.MaxwrittenClean.builder()
                .categories(new ArrayList<>())
                .data(new ArrayList<>())
                .average(0L)
                .total(0L)
                .build();
    }

    private BgWriterDto.BgwriterVsCheckpoint createEmptyBgwriterVsCheckpoint() {
        return BgWriterDto.BgwriterVsCheckpoint.builder()
                .categories(new ArrayList<>())
                .bgwriter(new ArrayList<>())
                .checkpoint(new ArrayList<>())
                .bgwriterTotal(0L)
                .checkpointTotal(0L)
                .build();
    }

    private BgWriterDto.BufferReuseRate createEmptyBufferReuseRate() {
        return BgWriterDto.BufferReuseRate.builder()
                .categories(new ArrayList<>())
                .data(new ArrayList<>())
                .average(0.0)
                .max(0.0)
                .min(0.0)
                .build();
    }

    private BgWriterDto.RecentStats createEmptyRecentStats() {
        return BgWriterDto.RecentStats.builder()
                .bgwriterActivityRate(0.0)
                .cleanBufferReuseRate(0.0)
                .backendFsyncCount(0L)
                .bufferPoolUsageRate(0.0)
                .checkpointInterruptionCount(0L)
                .dirtyBufferAccumulationRate(0.0)
                .build();
    }
}
