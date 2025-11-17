package com.dajanggan.domain.system.memory.service;

import com.dajanggan.domain.osmetric.dto.RedisOsMetricData;
import com.dajanggan.domain.osmetric.repository.OsMetricMapper;
import com.dajanggan.domain.osmetric.service.OsMetricRedisService;
import com.dajanggan.domain.system.memory.dto.MemoryDto;
import com.dajanggan.domain.system.memory.repository.MemoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Memory 모니터링 서비스
 *
 * 데이터 소스:
 * 1. Redis (실시간 위젯) - OS 메트릭
 * 2. memory_agg (1분) - PostgreSQL 메트릭, 위젯, 1시간 차트
 * 3. memory_agg_5m (5분) - 6시간 차트
 * 4. memory_agg_30m (30분) - 24시간 차트
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    private final MemoryMapper memoryMapper;
    private final OsMetricMapper osMetricMapper;
    private final OsMetricRedisService osMetricRedisService;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    // ========================================
    // 대시보드 전체 데이터 조회
    // ========================================

    /**
     * Memory 대시보드 전체 데이터 조회
     */
    public MemoryDto.DashboardResponse getMemoryDashboard(Long instanceId) {
        if (instanceId == null) {
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }

        log.info("========== Memory 대시보드 데이터 조회 시작: instanceId={} ==========", instanceId);

        try {
            // 실시간 위젯 (5개)
            MemoryDto.OsMemoryUsageWidget osMemoryUsage = getOsMemoryUsageWidget(instanceId);
            MemoryDto.SwapUsageWidget swapUsage = getSwapUsageWidget(instanceId);
            MemoryDto.SharedBufferHitWidget sharedBufferHit = getSharedBufferHitWidget(instanceId);
            MemoryDto.BufferUsageWidget bufferUsage = getBufferUsageWidget(instanceId);
            MemoryDto.TempFileUsageWidget tempFileUsage = getTempFileUsageWidget(instanceId);

            // 1시간 차트 (3개)
            MemoryDto.OsMemoryUsageChart1h osMemoryChart1h = getOsMemoryUsageChart1h(instanceId);
            MemoryDto.BufferCacheHitChart1h bufferCacheChart1h = getBufferCacheHitChart1h(instanceId);
            MemoryDto.BufferUtilizationChart1h bufferUtilChart1h = getBufferUtilizationChart1h(instanceId);

            // 6시간 차트 (2개)
            MemoryDto.TempFileChart6h tempFileChart6h = getTempFileChart6h(instanceId);
            MemoryDto.IoWaitTimeChart6h ioWaitTimeChart6h = getIoWaitTimeChart6h(instanceId);

            // 24시간 차트 (4개)
            MemoryDto.OsMemoryTrendChart24h osMemoryTrend24h = getOsMemoryTrendChart24h(instanceId);
            MemoryDto.SwapUsageTrendChart24h swapTrend24h = getSwapUsageTrendChart24h(instanceId);
            MemoryDto.BufferReuseScoreChart24h bufferReuseChart24h = getBufferReuseScoreChart24h(instanceId);
            MemoryDto.TopTablesByBufferChart24h topTablesChart24h = getTopTablesByBufferChart24h(instanceId);

            return MemoryDto.DashboardResponse.builder()
                    .osMemoryUsage(osMemoryUsage)
                    .swapUsage(swapUsage)
                    .sharedBufferHit(sharedBufferHit)
                    .bufferUsage(bufferUsage)
                    .tempFileUsage(tempFileUsage)
                    .osMemoryChart1h(osMemoryChart1h)
                    .bufferCacheChart1h(bufferCacheChart1h)
                    .bufferUtilChart1h(bufferUtilChart1h)
                    .tempFileChart6h(tempFileChart6h)
                    .ioWaitTimeChart6h(ioWaitTimeChart6h)
                    .osMemoryTrend24h(osMemoryTrend24h)
                    .swapTrend24h(swapTrend24h)
                    .bufferReuseChart24h(bufferReuseChart24h)
                    .topTablesChart24h(topTablesChart24h)
                    .build();

        } catch (Exception e) {
            log.error("Memory 대시보드 데이터 조회 실패: instanceId={}", instanceId, e);
            throw new RuntimeException("대시보드 데이터 조회 중 오류 발생", e);
        }
    }

    // ========================================
    // 실시간 위젯 (5개)
    // ========================================

    /**
     * 위젯 1: OS Memory Usage (Redis)
     */
    private MemoryDto.OsMemoryUsageWidget getOsMemoryUsageWidget(Long instanceId) {
        try {
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusMinutes(1);

            List<RedisOsMetricData> metrics = osMetricRedisService.getRecentMetricsByType(
                    instanceId, "MEMORY", startTime, endTime);

            if (metrics.isEmpty()) {
                return buildEmptyOsMemoryUsageWidget();
            }

            RedisOsMetricData latest = metrics.get(metrics.size() - 1);
            Map<String, Object> details = latest.getDetails();

            double usagePercent = getDouble(details, "usagePercent");
            long totalGB = getLong(details, "total") / (1024 * 1024 * 1024);
            long usedGB = getLong(details, "used") / (1024 * 1024 * 1024);
            long availableGB = getLong(details, "available") / (1024 * 1024 * 1024);
            long cacheGB = getLong(details, "cache") / (1024 * 1024 * 1024);

            // 추세 계산
            String trend = "stable";
            if (metrics.size() > 1) {
                RedisOsMetricData prev = metrics.get(0);
                double prevUsage = getDouble(prev.getDetails(), "usagePercent");
                if (usagePercent > prevUsage + 1.0) trend = "up";
                else if (usagePercent < prevUsage - 1.0) trend = "down";
            }

            // 상태 판정
            String status = usagePercent > 90 ? "danger" : usagePercent > 80 ? "warning" : "normal";

            return MemoryDto.OsMemoryUsageWidget.builder()
                    .usagePercent(usagePercent)
                    .trend(trend)
                    .status(status)
                    .totalGB(totalGB)
                    .usedGB(usedGB)
                    .availableGB(availableGB)
                    .cacheGB(cacheGB)
                    .build();

        } catch (Exception e) {
            log.error("OS Memory Usage 위젯 조회 실패", e);
            return buildEmptyOsMemoryUsageWidget();
        }
    }

    /**
     * 위젯 2: Swap Usage (Redis)
     */
    private MemoryDto.SwapUsageWidget getSwapUsageWidget(Long instanceId) {
        try {
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusMinutes(1);

            List<RedisOsMetricData> metrics = osMetricRedisService.getRecentMetricsByType(
                    instanceId, "SWAP", startTime, endTime);

            if (metrics.isEmpty()) {
                return buildEmptySwapUsageWidget();
            }

            RedisOsMetricData latest = metrics.get(metrics.size() - 1);
            Map<String, Object> details = latest.getDetails();

            double swapUsagePercent = getDouble(details, "swapUsagePercent");
            long totalSwapGB = getLong(details, "total") / (1024 * 1024 * 1024);
            long usedSwapGB = getLong(details, "used") / (1024 * 1024 * 1024);
            long swapInPerSec = getLong(details, "swapIn");
            long swapOutPerSec = getLong(details, "swapOut");

            // 상태 판정: Swap 사용 시 warning, Swap I/O 발생 시 danger
            String status = "normal";
            if (swapInPerSec > 0 || swapOutPerSec > 0) {
                status = "danger";
            } else if (swapUsagePercent > 10) {
                status = "warning";
            }

            return MemoryDto.SwapUsageWidget.builder()
                    .swapUsagePercent(swapUsagePercent)
                    .status(status)
                    .totalSwapGB(totalSwapGB)
                    .usedSwapGB(usedSwapGB)
                    .swapInPerSec(swapInPerSec)
                    .swapOutPerSec(swapOutPerSec)
                    .build();

        } catch (Exception e) {
            log.error("Swap Usage 위젯 조회 실패", e);
            return buildEmptySwapUsageWidget();
        }
    }

    /**
     * 위젯 3: Shared Buffer Hit Ratio (PostgreSQL)
     */
    private MemoryDto.SharedBufferHitWidget getSharedBufferHitWidget(Long instanceId) {
        try {
            Map<String, Object> result = memoryMapper.selectSharedBufferHitWidget(instanceId);

            if (result == null || result.isEmpty()) {
                return buildEmptySharedBufferHitWidget();
            }

            double hitRatio = getDoubleValue(result, "cache_hit_ratio");
            long cacheHits = getLongValue(result, "total_cache_hits");
            long physicalReads = getLongValue(result, "total_physical_reads");

            // 상태 판정: >95% 정상, 85-95% 주의, <85% 위험
            String status = hitRatio > 95 ? "normal" : hitRatio > 85 ? "warning" : "danger";

            return MemoryDto.SharedBufferHitWidget.builder()
                    .hitRatio(hitRatio)
                    .status(status)
                    .cacheHits(cacheHits)
                    .physicalReads(physicalReads)
                    .build();

        } catch (Exception e) {
            log.error("Shared Buffer Hit 위젯 조회 실패", e);
            return buildEmptySharedBufferHitWidget();
        }
    }

    /**
     * 위젯 4: Buffer Usage (PostgreSQL)
     */
    private MemoryDto.BufferUsageWidget getBufferUsageWidget(Long instanceId) {
        try {
            Map<String, Object> result = memoryMapper.selectBufferUsageWidget(instanceId);

            if (result == null || result.isEmpty()) {
                return buildEmptyBufferUsageWidget();
            }

            double bufferUsagePercent = getDoubleValue(result, "buffer_usage_percent");
            double dirtyRatio = getDoubleValue(result, "dirty_ratio");
            double pinnedRatio = getDoubleValue(result, "pinned_ratio");
            long usedBuffers = getLongValue(result, "used_buffers");
            long totalBuffers = getLongValue(result, "total_buffers");

            // 상태 판정: Dirty > 30% 주의, Dirty > 50% 위험
            String status = dirtyRatio > 50 ? "danger" : dirtyRatio > 30 ? "warning" : "normal";

            return MemoryDto.BufferUsageWidget.builder()
                    .bufferUsagePercent(bufferUsagePercent)
                    .dirtyRatio(dirtyRatio)
                    .pinnedRatio(pinnedRatio)
                    .status(status)
                    .usedBuffers(usedBuffers)
                    .totalBuffers(totalBuffers)
                    .build();

        } catch (Exception e) {
            log.error("Buffer Usage 위젯 조회 실패", e);
            return buildEmptyBufferUsageWidget();
        }
    }

    /**
     * 위젯 5: Temp File Usage (PostgreSQL)
     */
    private MemoryDto.TempFileUsageWidget getTempFileUsageWidget(Long instanceId) {
        try {
            Map<String, Object> result = memoryMapper.selectTempFileUsageWidget(instanceId);

            if (result == null || result.isEmpty()) {
                return buildEmptyTempFileUsageWidget();
            }

            double tempFileRate = getDoubleValue(result, "temp_file_rate");
            long totalTempFiles = getLongValue(result, "total_temp_files");
            long totalTempMB = getLongValue(result, "total_temp_bytes") / (1024 * 1024);

            // 상태 판정: work_mem 부족 징후
            String status = tempFileRate > 10 ? "danger" : tempFileRate > 1 ? "warning" : "normal";
            String message = tempFileRate > 10 ? "work_mem 증가 필요" : tempFileRate > 1 ? "work_mem 모니터링" : "정상";

            return MemoryDto.TempFileUsageWidget.builder()
                    .tempFileRate(tempFileRate)
                    .status(status)
                    .totalTempFiles(totalTempFiles)
                    .totalTempMB(totalTempMB)
                    .message(message)
                    .build();

        } catch (Exception e) {
            log.error("Temp File Usage 위젯 조회 실패", e);
            return buildEmptyTempFileUsageWidget();
        }
    }

    // ========================================
    // 1시간 차트 (3개)
    // ========================================

    /**
     * 차트 1: OS Memory Usage (1시간)
     */
    private MemoryDto.OsMemoryUsageChart1h getOsMemoryUsageChart1h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime startTime = endTime.minusHours(1);

            List<Map<String, Object>> metrics = osMetricMapper.selectAggregatedMetrics(
                    instanceId, "MEMORY_USAGE", startTime, endTime);

            if (metrics.isEmpty()) {
                return buildEmptyOsMemoryUsageChart1h();
            }

            List<String> categories = metrics.stream()
                    .map(m -> formatTime(m.get("collected_at")))
                    .collect(Collectors.toList());

            List<Double> usedGB = metrics.stream()
                    .map(m -> getDoubleValue(m, "used") / (1024.0 * 1024.0 * 1024.0))
                    .collect(Collectors.toList());

            List<Double> cacheGB = metrics.stream()
                    .map(m -> getDoubleValue(m, "cache") / (1024.0 * 1024.0 * 1024.0))
                    .collect(Collectors.toList());

            List<Double> bufferGB = metrics.stream()
                    .map(m -> getDoubleValue(m, "buffer") / (1024.0 * 1024.0 * 1024.0))
                    .collect(Collectors.toList());

            return MemoryDto.OsMemoryUsageChart1h.builder()
                    .categories(categories)
                    .usedGB(usedGB)
                    .cacheGB(cacheGB)
                    .bufferGB(bufferGB)
                    .build();

        } catch (Exception e) {
            log.error("OS Memory Usage Chart 1h 조회 실패", e);
            return buildEmptyOsMemoryUsageChart1h();
        }
    }

    /**
     * 차트 2: Buffer Cache Hit Ratio (1시간)
     */
    private MemoryDto.BufferCacheHitChart1h getBufferCacheHitChart1h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime startTime = endTime.minusHours(1);

            List<Map<String, Object>> results = memoryMapper.selectBufferCacheHitChart1h(
                    instanceId, startTime, endTime);

            if (results.isEmpty()) {
                return buildEmptyBufferCacheHitChart1h();
            }

            List<String> categories = results.stream()
                    .map(r -> formatTime(r.get("time_label")))
                    .collect(Collectors.toList());

            List<Double> hitRatio = results.stream()
                    .map(r -> getDoubleValue(r, "cache_hit_ratio"))
                    .collect(Collectors.toList());

            return MemoryDto.BufferCacheHitChart1h.builder()
                    .categories(categories)
                    .hitRatio(hitRatio)
                    .warningThreshold(85.0)
                    .normalThreshold(95.0)
                    .build();

        } catch (Exception e) {
            log.error("Buffer Cache Hit Chart 1h 조회 실패", e);
            return buildEmptyBufferCacheHitChart1h();
        }
    }

    /**
     * 차트 3: Buffer Utilization (1시간)
     */
    private MemoryDto.BufferUtilizationChart1h getBufferUtilizationChart1h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime startTime = endTime.minusHours(1);

            List<Map<String, Object>> results = memoryMapper.selectBufferUtilizationChart1h(
                    instanceId, startTime, endTime);

            if (results.isEmpty()) {
                return buildEmptyBufferUtilizationChart1h();
            }

            List<String> categories = results.stream()
                    .map(r -> formatTime(r.get("time_label")))
                    .collect(Collectors.toList());

            List<Long> dirtyBuffers = results.stream()
                    .map(r -> getLongValue(r, "dirty_buffers"))
                    .collect(Collectors.toList());

            List<Long> pinnedBuffers = results.stream()
                    .map(r -> getLongValue(r, "pinned_buffers"))
                    .collect(Collectors.toList());

            return MemoryDto.BufferUtilizationChart1h.builder()
                    .categories(categories)
                    .dirtyBuffers(dirtyBuffers)
                    .pinnedBuffers(pinnedBuffers)
                    .build();

        } catch (Exception e) {
            log.error("Buffer Utilization Chart 1h 조회 실패", e);
            return buildEmptyBufferUtilizationChart1h();
        }
    }

    // ========================================
    // 6시간 차트 (2개) - memory_agg_5m
    // ========================================

    /**
     * 차트 4: Temp File Generation (6시간)
     */
    private MemoryDto.TempFileChart6h getTempFileChart6h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime startTime = endTime.minusHours(6);

            List<Map<String, Object>> results = memoryMapper.selectTempFileChart6h(
                    instanceId, startTime, endTime);

            if (results.isEmpty()) {
                return buildEmptyTempFileChart6h();
            }

            List<String> categories = results.stream()
                    .map(r -> formatTime(r.get("time_bucket")))
                    .collect(Collectors.toList());

            List<Long> tempFileCount = results.stream()
                    .map(r -> getLongValue(r, "total_temp_files"))
                    .collect(Collectors.toList());

            List<Double> tempFileSizeMB = results.stream()
                    .map(r -> getDoubleValue(r, "total_temp_bytes") / (1024.0 * 1024.0))
                    .collect(Collectors.toList());

            return MemoryDto.TempFileChart6h.builder()
                    .categories(categories)
                    .tempFileCount(tempFileCount)
                    .tempFileSizeMB(tempFileSizeMB)
                    .build();

        } catch (Exception e) {
            log.error("Temp File Chart 6h 조회 실패", e);
            return buildEmptyTempFileChart6h();
        }
    }

    /**
     * 차트 5: I/O Wait Time (6시간)
     */
    private MemoryDto.IoWaitTimeChart6h getIoWaitTimeChart6h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime startTime = endTime.minusHours(6);

            List<Map<String, Object>> results = memoryMapper.selectIoWaitTimeChart6h(
                    instanceId, startTime, endTime);

            if (results.isEmpty()) {
                return buildEmptyIoWaitTimeChart6h();
            }

            List<String> categories = results.stream()
                    .map(r -> formatTime(r.get("time_bucket")))
                    .collect(Collectors.toList());

            List<Double> readWaitMs = results.stream()
                    .map(r -> getDoubleValue(r, "total_blk_read_time"))
                    .collect(Collectors.toList());

            List<Double> writeWaitMs = results.stream()
                    .map(r -> getDoubleValue(r, "total_blk_write_time"))
                    .collect(Collectors.toList());

            return MemoryDto.IoWaitTimeChart6h.builder()
                    .categories(categories)
                    .readWaitMs(readWaitMs)
                    .writeWaitMs(writeWaitMs)
                    .build();

        } catch (Exception e) {
            log.error("I/O Wait Time Chart 6h 조회 실패", e);
            return buildEmptyIoWaitTimeChart6h();
        }
    }

    // ========================================
    // 24시간 차트 (4개) - memory_agg_30m
    // ========================================

    /**
     * 차트 6: OS Memory Trend (24시간)
     */
    private MemoryDto.OsMemoryTrendChart24h getOsMemoryTrendChart24h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime startTime = endTime.minusHours(24);

            List<Map<String, Object>> metrics = osMetricMapper.selectAggregatedMetrics(
                    instanceId, "MEMORY_USAGE", startTime, endTime);

            if (metrics.isEmpty()) {
                return buildEmptyOsMemoryTrendChart24h();
            }

            List<String> categories = metrics.stream()
                    .map(m -> formatDateTime(m.get("collected_at")))
                    .collect(Collectors.toList());

            List<Double> usagePercent = metrics.stream()
                    .map(m -> getDoubleValue(m, "usage_percent"))
                    .collect(Collectors.toList());

            return MemoryDto.OsMemoryTrendChart24h.builder()
                    .categories(categories)
                    .usagePercent(usagePercent)
                    .warningThreshold(80.0)
                    .dangerThreshold(90.0)
                    .build();

        } catch (Exception e) {
            log.error("OS Memory Trend Chart 24h 조회 실패", e);
            return buildEmptyOsMemoryTrendChart24h();
        }
    }

    /**
     * 차트 7: Swap Usage Trend (24시간)
     */
    private MemoryDto.SwapUsageTrendChart24h getSwapUsageTrendChart24h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime startTime = endTime.minusHours(24);

            List<Map<String, Object>> metrics = osMetricMapper.selectAggregatedMetrics(
                    instanceId, "SWAP_USAGE", startTime, endTime);

            if (metrics.isEmpty()) {
                return buildEmptySwapUsageTrendChart24h();
            }

            List<String> categories = metrics.stream()
                    .map(m -> formatDateTime(m.get("collected_at")))
                    .collect(Collectors.toList());

            List<Double> swapUsagePercent = metrics.stream()
                    .map(m -> getDoubleValue(m, "swap_usage_percent"))
                    .collect(Collectors.toList());

            List<Long> swapInRate = metrics.stream()
                    .map(m -> getLongValue(m, "swap_in_rate"))
                    .collect(Collectors.toList());

            List<Long> swapOutRate = metrics.stream()
                    .map(m -> getLongValue(m, "swap_out_rate"))
                    .collect(Collectors.toList());

            return MemoryDto.SwapUsageTrendChart24h.builder()
                    .categories(categories)
                    .swapUsagePercent(swapUsagePercent)
                    .swapInRate(swapInRate)
                    .swapOutRate(swapOutRate)
                    .build();

        } catch (Exception e) {
            log.error("Swap Usage Trend Chart 24h 조회 실패", e);
            return buildEmptySwapUsageTrendChart24h();
        }
    }

    /**
     * 차트 8: Buffer Reuse Score (24시간)
     */
    private MemoryDto.BufferReuseScoreChart24h getBufferReuseScoreChart24h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime startTime = endTime.minusHours(24);

            List<Map<String, Object>> results = memoryMapper.selectBufferReuseScoreChart24h(
                    instanceId, startTime, endTime);

            if (results.isEmpty()) {
                return buildEmptyBufferReuseScoreChart24h();
            }

            List<String> categories = results.stream()
                    .map(r -> formatDateTime(r.get("time_bucket")))
                    .collect(Collectors.toList());

            List<Double> reuseScore = results.stream()
                    .map(r -> getDoubleValue(r, "avg_buffer_reuse_score"))
                    .collect(Collectors.toList());

            List<Double> avgUsagecount = results.stream()
                    .map(r -> getDoubleValue(r, "avg_usagecount"))
                    .collect(Collectors.toList());

            return MemoryDto.BufferReuseScoreChart24h.builder()
                    .categories(categories)
                    .reuseScore(reuseScore)
                    .avgUsagecount(avgUsagecount)
                    .build();

        } catch (Exception e) {
            log.error("Buffer Reuse Score Chart 24h 조회 실패", e);
            return buildEmptyBufferReuseScoreChart24h();
        }
    }

    /**
     * 차트 9: Top Tables by Buffer Usage (24시간)
     */
    private MemoryDto.TopTablesByBufferChart24h getTopTablesByBufferChart24h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime startTime = endTime.minusHours(24);

            List<Map<String, Object>> results = memoryMapper.selectTopTablesByBufferChart24h(
                    instanceId, startTime, endTime);

            if (results.isEmpty()) {
                return buildEmptyTopTablesByBufferChart24h();
            }

            List<String> tableNames = results.stream()
                    .map(r -> (String) r.get("relname"))
                    .collect(Collectors.toList());

            List<Long> bufferCounts = results.stream()
                    .map(r -> getLongValue(r, "avg_buffers"))
                    .collect(Collectors.toList());

            List<Double> usagePercent = results.stream()
                    .map(r -> getDoubleValue(r, "buffer_usage_percent"))
                    .collect(Collectors.toList());

            return MemoryDto.TopTablesByBufferChart24h.builder()
                    .tableNames(tableNames)
                    .bufferCounts(bufferCounts)
                    .usagePercent(usagePercent)
                    .build();

        } catch (Exception e) {
            log.error("Top Tables by Buffer Chart 24h 조회 실패", e);
            return buildEmptyTopTablesByBufferChart24h();
        }
    }

    // ========================================
    // 리스트 (2개 섹션)
    // ========================================

    /**
     * Memory 리스트 조회
     */
    public MemoryDto.ListResponse getMemoryList(Long instanceId, String timeRange, List<String> statusList) {
        if (instanceId == null) {
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }

        OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime startTime = calculateStartTime(endTime, timeRange);

        try {
            // 섹션 1: 높은 버퍼 사용 테이블 Top 20
            List<MemoryDto.HighBufferUsageItem> highBufferUsageList =
                    getHighBufferUsageList(instanceId, startTime, endTime, statusList);

            // 섹션 2: 낮은 캐시 히트율 테이블 Top 20
            List<MemoryDto.LowCacheHitItem> lowCacheHitList =
                    getLowCacheHitList(instanceId, startTime, endTime, statusList);

            long totalCount = (long) highBufferUsageList.size() + lowCacheHitList.size();

            return MemoryDto.ListResponse.builder()
                    .highBufferUsageList(highBufferUsageList)
                    .lowCacheHitList(lowCacheHitList)
                    .totalCount(totalCount)
                    .build();

        } catch (Exception e) {
            log.error("Memory 리스트 조회 실패", e);
            throw new RuntimeException("리스트 데이터 조회 중 오류 발생", e);
        }
    }

    /**
     * 섹션 1: 높은 버퍼 사용 테이블 Top 20
     */
    private List<MemoryDto.HighBufferUsageItem> getHighBufferUsageList(
            Long instanceId, OffsetDateTime startTime, OffsetDateTime endTime, List<String> statusList) {

        List<Map<String, Object>> results = memoryMapper.selectHighBufferUsageTop20(
                instanceId, startTime, endTime, statusList);

        return results.stream()
                .map(r -> MemoryDto.HighBufferUsageItem.builder()
                        .rankNum(getLongValue(r, "rank_num"))
                        .tableName((String) r.get("relname"))
                        .relkind((String) r.get("relkind"))
                        .bufferCount(getLongValue(r, "avg_buffers"))
                        .bufferUsagePercent(getDoubleValue(r, "buffer_usage_percent"))
                        .dirtyRatio(getDoubleValue(r, "dirty_ratio"))
                        .cacheHitRatio(getDoubleValue(r, "cache_hit_ratio"))
                        .status((String) r.get("status"))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 섹션 2: 낮은 캐시 히트율 테이블 Top 20
     */
    private List<MemoryDto.LowCacheHitItem> getLowCacheHitList(
            Long instanceId, OffsetDateTime startTime, OffsetDateTime endTime, List<String> statusList) {

        List<Map<String, Object>> results = memoryMapper.selectLowCacheHitTop20(
                instanceId, startTime, endTime, statusList);

        return results.stream()
                .map(r -> MemoryDto.LowCacheHitItem.builder()
                        .rankNum(getLongValue(r, "rank_num"))
                        .tableName((String) r.get("relname"))
                        .databaseName((String) r.get("database_name"))
                        .cacheHitRatio(getDoubleValue(r, "cache_hit_ratio"))
                        .physicalReads(getLongValue(r, "physical_reads"))
                        .cacheHits(getLongValue(r, "cache_hits"))
                        .status((String) r.get("status"))
                        .build())
                .collect(Collectors.toList());
    }

    // ========================================
    // Helper Methods
    // ========================================

    private OffsetDateTime calculateStartTime(OffsetDateTime endTime, String timeRange) {
        return switch (timeRange) {
            case "1h" -> endTime.minusHours(1);
            case "6h" -> endTime.minusHours(6);
            case "24h" -> endTime.minusHours(24);
            case "7d" -> endTime.minusDays(7);
            default -> endTime.minusDays(7);
        };
    }

    private String formatTime(Object timeObj) {
        if (timeObj instanceof OffsetDateTime) {
            return ((OffsetDateTime) timeObj).format(TIME_FORMATTER);
        }
        if (timeObj instanceof LocalDateTime) {
            return ((LocalDateTime) timeObj).format(TIME_FORMATTER);
        }
        return timeObj != null ? timeObj.toString() : "";
    }

    private String formatDateTime(Object timeObj) {
        if (timeObj instanceof OffsetDateTime) {
            return ((OffsetDateTime) timeObj).format(DATE_TIME_FORMATTER);
        }
        if (timeObj instanceof LocalDateTime) {
            return ((LocalDateTime) timeObj).format(DATE_TIME_FORMATTER);
        }
        return timeObj != null ? timeObj.toString() : "";
    }

    private Double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0.0;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }

    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0L;
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    private Double getDouble(Map<String, Object> map, String key) {
        return getDoubleValue(map, key);
    }

    private Long getLong(Map<String, Object> map, String key) {
        return getLongValue(map, key);
    }

    // ========================================
    // Empty Data Builders
    // ========================================

    private MemoryDto.OsMemoryUsageWidget buildEmptyOsMemoryUsageWidget() {
        return MemoryDto.OsMemoryUsageWidget.builder()
                .usagePercent(0.0).trend("stable").status("normal")
                .totalGB(0L).usedGB(0L).availableGB(0L).cacheGB(0L).build();
    }

    private MemoryDto.SwapUsageWidget buildEmptySwapUsageWidget() {
        return MemoryDto.SwapUsageWidget.builder()
                .swapUsagePercent(0.0).status("normal")
                .totalSwapGB(0L).usedSwapGB(0L)
                .swapInPerSec(0L).swapOutPerSec(0L).build();
    }

    private MemoryDto.SharedBufferHitWidget buildEmptySharedBufferHitWidget() {
        return MemoryDto.SharedBufferHitWidget.builder()
                .hitRatio(0.0).status("normal")
                .cacheHits(0L).physicalReads(0L).build();
    }

    private MemoryDto.BufferUsageWidget buildEmptyBufferUsageWidget() {
        return MemoryDto.BufferUsageWidget.builder()
                .bufferUsagePercent(0.0).dirtyRatio(0.0).pinnedRatio(0.0)
                .status("normal").usedBuffers(0L).totalBuffers(0L).build();
    }

    private MemoryDto.TempFileUsageWidget buildEmptyTempFileUsageWidget() {
        return MemoryDto.TempFileUsageWidget.builder()
                .tempFileRate(0.0).status("normal")
                .totalTempFiles(0L).totalTempMB(0L).message("정상").build();
    }

    private MemoryDto.OsMemoryUsageChart1h buildEmptyOsMemoryUsageChart1h() {
        return MemoryDto.OsMemoryUsageChart1h.builder()
                .categories(new ArrayList<>())
                .usedGB(new ArrayList<>())
                .cacheGB(new ArrayList<>())
                .bufferGB(new ArrayList<>()).build();
    }

    private MemoryDto.BufferCacheHitChart1h buildEmptyBufferCacheHitChart1h() {
        return MemoryDto.BufferCacheHitChart1h.builder()
                .categories(new ArrayList<>())
                .hitRatio(new ArrayList<>())
                .warningThreshold(85.0)
                .normalThreshold(95.0).build();
    }

    private MemoryDto.BufferUtilizationChart1h buildEmptyBufferUtilizationChart1h() {
        return MemoryDto.BufferUtilizationChart1h.builder()
                .categories(new ArrayList<>())
                .dirtyBuffers(new ArrayList<>())
                .pinnedBuffers(new ArrayList<>()).build();
    }

    private MemoryDto.TempFileChart6h buildEmptyTempFileChart6h() {
        return MemoryDto.TempFileChart6h.builder()
                .categories(new ArrayList<>())
                .tempFileCount(new ArrayList<>())
                .tempFileSizeMB(new ArrayList<>()).build();
    }

    private MemoryDto.IoWaitTimeChart6h buildEmptyIoWaitTimeChart6h() {
        return MemoryDto.IoWaitTimeChart6h.builder()
                .categories(new ArrayList<>())
                .readWaitMs(new ArrayList<>())
                .writeWaitMs(new ArrayList<>()).build();
    }

    private MemoryDto.OsMemoryTrendChart24h buildEmptyOsMemoryTrendChart24h() {
        return MemoryDto.OsMemoryTrendChart24h.builder()
                .categories(new ArrayList<>())
                .usagePercent(new ArrayList<>())
                .warningThreshold(80.0)
                .dangerThreshold(90.0).build();
    }

    private MemoryDto.SwapUsageTrendChart24h buildEmptySwapUsageTrendChart24h() {
        return MemoryDto.SwapUsageTrendChart24h.builder()
                .categories(new ArrayList<>())
                .swapUsagePercent(new ArrayList<>())
                .swapInRate(new ArrayList<>())
                .swapOutRate(new ArrayList<>()).build();
    }

    private MemoryDto.BufferReuseScoreChart24h buildEmptyBufferReuseScoreChart24h() {
        return MemoryDto.BufferReuseScoreChart24h.builder()
                .categories(new ArrayList<>())
                .reuseScore(new ArrayList<>())
                .avgUsagecount(new ArrayList<>()).build();
    }

    private MemoryDto.TopTablesByBufferChart24h buildEmptyTopTablesByBufferChart24h() {
        return MemoryDto.TopTablesByBufferChart24h.builder()
                .tableNames(new ArrayList<>())
                .bufferCounts(new ArrayList<>())
                .usagePercent(new ArrayList<>()).build();
    }
}