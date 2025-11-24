package com.dajanggan.domain.system.memory.service;

import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.repository.InstanceRepository;
import com.dajanggan.domain.osmetric.dto.RedisOsMetricData;
import com.dajanggan.domain.osmetric.repository.OsMetricMapper;
import com.dajanggan.domain.osmetric.service.OsMetricRedisService;
import com.dajanggan.domain.system.memory.dto.*;
import com.dajanggan.domain.system.memory.repository.MemoryMapper;
import com.dajanggan.infrastructure.datasource.DataSourceFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Memory 모니터링 서비스
 *
 * 데이터 소스:
 * 1. Redis (실시간 위젯) - OS 메트릭
 * 2. memory_agg_1m (1분) - PostgreSQL 메트릭, 위젯, 1시간 차트
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
    private final InstanceRepository instanceRepository;
    private final DataSourceFactory dataSourceFactory;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    // ========================================
    // 대시보드 전체 데이터 조회
    // ========================================

    /**
     * Memory 대시보드 전체 데이터 조회
     */
    public MemoryDashboardResponse getMemoryDashboard(Long instanceId) {
        if (instanceId == null) {
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }

        log.info("========== Memory 대시보드 데이터 조회 시작: instanceId={} ==========", instanceId);
        long startTime = System.currentTimeMillis();

        try {
            // 모든 데이터를 병렬로 조회
            CompletableFuture<MemoryDashboardResponse.OsMemoryUsageWidget> osMemoryUsageFuture =
                    CompletableFuture.supplyAsync(() -> getOsMemoryUsageWidget(instanceId));
            CompletableFuture<MemoryDashboardResponse.SwapUsageWidget> swapUsageFuture =
                    CompletableFuture.supplyAsync(() -> getSwapUsageWidget(instanceId));
            CompletableFuture<MemoryDashboardResponse.SharedBufferHitWidget> sharedBufferHitFuture =
                    CompletableFuture.supplyAsync(() -> getSharedBufferHitWidget(instanceId));
            CompletableFuture<MemoryDashboardResponse.TempFileUsageWidget> tempFileUsageFuture =
                    CompletableFuture.supplyAsync(() -> getTempFileUsageWidget(instanceId));

            // OS Memory Usage Chart는 SSE로 실시간 데이터를 받아오므로 빈 데이터 반환
            CompletableFuture<MemoryDashboardResponse.OsMemoryUsageChart1h> osMemoryChart1hFuture =
                    CompletableFuture.supplyAsync(() -> buildEmptyOsMemoryUsageChart1h());
            CompletableFuture<MemoryDashboardResponse.BufferCacheHitChart1h> bufferCacheChart1hFuture =
                    CompletableFuture.supplyAsync(() -> getBufferCacheHitChart1h(instanceId));

            CompletableFuture<MemoryDashboardResponse.TempFileChart6h> tempFileChart6hFuture =
                    CompletableFuture.supplyAsync(() -> getTempFileChart6h(instanceId));
            CompletableFuture<MemoryDashboardResponse.IoWaitTimeChart6h> ioWaitTimeChart6hFuture =
                    CompletableFuture.supplyAsync(() -> getIoWaitTimeChart6h(instanceId));

            // OS Memory Trend Chart는 SSE로 실시간 데이터를 받아오므로 빈 데이터 반환
            CompletableFuture<MemoryDashboardResponse.OsMemoryTrendChart24h> osMemoryTrend24hFuture =
                    CompletableFuture.supplyAsync(() -> buildEmptyOsMemoryTrendChart24h());
            // Swap Usage Trend Chart는 SSE로 실시간 데이터를 받아오므로 빈 데이터 반환
            CompletableFuture<MemoryDashboardResponse.SwapUsageTrendChart24h> swapTrend24hFuture =
                    CompletableFuture.supplyAsync(() -> buildEmptySwapUsageTrendChart24h());
            CompletableFuture<MemoryDashboardResponse.TopTablesByBufferChart24h> topTablesChart24hFuture =
                    CompletableFuture.supplyAsync(() -> getTopTablesByBufferChart24h(instanceId));

            // 모든 작업이 완료될 때까지 대기
            CompletableFuture.allOf(
                    osMemoryUsageFuture, swapUsageFuture, sharedBufferHitFuture,
                    tempFileUsageFuture, osMemoryChart1hFuture,
                    bufferCacheChart1hFuture, tempFileChart6hFuture,
                    ioWaitTimeChart6hFuture, osMemoryTrend24hFuture, swapTrend24hFuture,
                    topTablesChart24hFuture
            ).join();

            // 결과 조합
            MemoryDashboardResponse response = MemoryDashboardResponse.builder()
                    .osMemoryUsage(osMemoryUsageFuture.join())
                    .swapUsage(swapUsageFuture.join())
                    .sharedBufferHit(sharedBufferHitFuture.join())
                    .tempFileUsage(tempFileUsageFuture.join())
                    .osMemoryChart1h(osMemoryChart1hFuture.join())
                    .bufferCacheChart1h(bufferCacheChart1hFuture.join())
                    .tempFileChart6h(tempFileChart6hFuture.join())
                    .ioWaitTimeChart6h(ioWaitTimeChart6hFuture.join())
                    .osMemoryTrend24h(osMemoryTrend24hFuture.join())
                    .swapTrend24h(swapTrend24hFuture.join())
                    .topTablesChart24h(topTablesChart24hFuture.join())
                    .build();

            long endTime = System.currentTimeMillis();
            log.info("========== Memory 대시보드 데이터 조회 완료: instanceId={}, 소요시간={}ms ==========",
                    instanceId, (endTime - startTime));

            return response;

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
    public MemoryDashboardResponse.OsMemoryUsageWidget getOsMemoryUsageWidget(Long instanceId) {
        try {
            // endTime을 약간 늦춰서 최신 데이터를 확실히 포함
            // 시스템 시간대를 명시적으로 사용하여 UTC로 변환
            ZonedDateTime nowZoned = ZonedDateTime.now(ZoneId.systemDefault());
            OffsetDateTime endTime = nowZoned.plusMinutes(1).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
            OffsetDateTime startTime = endTime.minusMinutes(1);

            List<RedisOsMetricData> metrics = osMetricRedisService.getRecentMetricsByType(
                    instanceId, "MEMORY", startTime, endTime);

            if (metrics.isEmpty()) {
                return buildEmptyOsMemoryUsageWidget();
            }

            RedisOsMetricData latest = metrics.get(metrics.size() - 1);
            Map<String, Object> details = latest.getDetails();

            double usagePercent = getDoubleValue(details, "usagePercent");
            long totalGB = getLongValue(details, "total") / (1024 * 1024 * 1024);
            long usedGB = getLongValue(details, "used") / (1024 * 1024 * 1024);
            long availableGB = getLongValue(details, "available") / (1024 * 1024 * 1024);
            long cacheGB = getLongValue(details, "cache") / (1024 * 1024 * 1024);

            // 추세 계산
            String trend = "stable";
            if (metrics.size() > 1) {
                RedisOsMetricData prev = metrics.get(0);
                double prevUsage = getDoubleValue(prev.getDetails(), "usagePercent");
                if (usagePercent > prevUsage + 1.0) trend = "up";
                else if (usagePercent < prevUsage - 1.0) trend = "down";
            }

            // 상태 판정
            String status = usagePercent > 90 ? "danger" : usagePercent > 80 ? "warning" : "normal";

            return MemoryDashboardResponse.OsMemoryUsageWidget.builder()
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
     * 위젯 2: Swap Usage (Redis - MEMORY 타입의 swap 데이터 사용)
     */
    public MemoryDashboardResponse.SwapUsageWidget getSwapUsageWidget(Long instanceId) {
        try {
            // endTime을 약간 늦춰서 최신 데이터를 확실히 포함
            // 시스템 시간대를 명시적으로 사용하여 UTC로 변환
            ZonedDateTime nowZoned = ZonedDateTime.now(ZoneId.systemDefault());
            OffsetDateTime endTime = nowZoned.plusMinutes(1).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
            OffsetDateTime startTime = endTime.minusMinutes(1);

            // MEMORY 타입에서 swap 정보 추출
            List<RedisOsMetricData> metrics = osMetricRedisService.getRecentMetricsByType(
                    instanceId, "MEMORY", startTime, endTime);

            if (metrics.isEmpty()) {
                return buildEmptySwapUsageWidget();
            }

            RedisOsMetricData latest = metrics.get(metrics.size() - 1);
            Map<String, Object> details = latest.getDetails();

            // swap 정보는 details 안의 swap 객체에 있음
            Map<String, Object> swapDetails = getMap(details, "swap");
            
            double swapUsagePercent = 0.0;
            long totalSwapGB = 0;
            long usedSwapGB = 0;
            long swapInPerSec = 0;
            long swapOutPerSec = 0;

            if (swapDetails != null && !swapDetails.isEmpty()) {
                totalSwapGB = getLong(swapDetails, "total") / (1024 * 1024 * 1024);
                usedSwapGB = getLong(swapDetails, "used") / (1024 * 1024 * 1024);
                swapInPerSec = getLong(swapDetails, "swapIn");
                swapOutPerSec = getLong(swapDetails, "swapOut");
                
                if (totalSwapGB > 0) {
                    swapUsagePercent = (double) usedSwapGB / totalSwapGB * 100.0;
                }
            }

            // 상태 판정: Swap 사용 시 warning, Swap I/O 발생 시 danger
            String status = "normal";
            if (swapInPerSec > 0 || swapOutPerSec > 0) {
                status = "danger";
            } else if (swapUsagePercent > 10) {
                status = "warning";
            }

            return MemoryDashboardResponse.SwapUsageWidget.builder()
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
     * 최근 15분 데이터 사용
     */
    public MemoryDashboardResponse.SharedBufferHitWidget getSharedBufferHitWidget(Long instanceId) {
        try {
            // 최근 15분 데이터 조회
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusMinutes(15);
            
            log.debug("Shared Buffer Hit 위젯 조회: instanceId={}, startTime={}, endTime={}", 
                    instanceId, startTime, endTime);
            
            Map<String, Object> result = memoryMapper.selectSharedBufferHitWidget(instanceId, startTime, endTime);

            if (result == null || result.isEmpty()) {
                log.warn("Shared Buffer Hit 위젯 데이터 없음: instanceId={}", instanceId);
                return buildEmptySharedBufferHitWidget();
            }

            double hitRatio = getDoubleValue(result, "cache_hit_ratio");
            long cacheHits = getLongValue(result, "total_cache_hits");
            long physicalReads = getLongValue(result, "total_physical_reads");

            log.debug("Shared Buffer Hit 위젯 데이터: instanceId={}, hitRatio={}, cacheHits={}, physicalReads={}", 
                    instanceId, hitRatio, cacheHits, physicalReads);

            // 상태 판정: >95% 정상, 85-95% 주의, <85% 위험
            String status = hitRatio > 95 ? "normal" : hitRatio > 85 ? "warning" : "danger";

            return MemoryDashboardResponse.SharedBufferHitWidget.builder()
                    .hitRatio(hitRatio)
                    .status(status)
                    .cacheHits(cacheHits)
                    .physicalReads(physicalReads)
                    .build();

        } catch (Exception e) {
            log.error("Shared Buffer Hit 위젯 조회 실패: instanceId={}", instanceId, e);
            return buildEmptySharedBufferHitWidget();
        }
    }


    /**
     * 위젯 5: Temp File Usage (PostgreSQL)
     * 최근 15분 데이터 사용
     */
    public MemoryDashboardResponse.TempFileUsageWidget getTempFileUsageWidget(Long instanceId) {
        try {
            // 최근 15분 데이터 조회
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusMinutes(15);
            
            Map<String, Object> result = memoryMapper.selectTempFileUsageWidget(instanceId, startTime, endTime);

            if (result == null || result.isEmpty()) {
                return buildEmptyTempFileUsageWidget();
            }

            double tempFileRate = getDoubleValue(result, "temp_file_rate");
            long totalTempFiles = getLongValue(result, "total_temp_files");
            long totalTempMB = getLongValue(result, "total_temp_bytes") / (1024 * 1024);

            // 상태 판정: work_mem 부족 징후
            String status = tempFileRate > 10 ? "danger" : tempFileRate > 1 ? "warning" : "normal";
            String message = tempFileRate > 10 ? "work_mem 증가 필요" : tempFileRate > 1 ? "work_mem 모니터링" : "정상";

            return MemoryDashboardResponse.TempFileUsageWidget.builder()
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
     * 차트 1: OS Memory Usage (실시간 SSE)
     * 
     * 주의: 이 메서드는 더 이상 사용되지 않습니다.
     * 프론트엔드에서 SSE로 실시간 데이터를 받아오므로 빈 데이터를 반환합니다.
     * 
     * @deprecated SSE로 대체됨 - buildEmptyOsMemoryUsageChart1h() 사용
     */
    @Deprecated
    private MemoryDashboardResponse.OsMemoryUsageChart1h getOsMemoryUsageChart1h(Long instanceId) {
        // SSE로 실시간 데이터를 받아오므로 빈 데이터 반환
        log.debug("OS Memory Usage Chart는 SSE로 실시간 데이터를 받아오므로 빈 데이터 반환: instanceId={}", instanceId);
        return buildEmptyOsMemoryUsageChart1h();
    }

    /**
     * 차트 2: Buffer Cache Hit Ratio (최근 15분)
     * 1분 집계 데이터 사용
     */
    private MemoryDashboardResponse.BufferCacheHitChart1h getBufferCacheHitChart1h(Long instanceId) {
        try {
            // endTime을 약간 늦춰서 최신 데이터를 확실히 포함
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusMinutes(15);

            log.debug("Buffer Cache Hit Chart 1h 조회: instanceId={}, startTime={}, endTime={}", 
                    instanceId, startTime, endTime);

            List<Map<String, Object>> results = memoryMapper.selectBufferCacheHitChart1h(
                    instanceId, startTime, endTime);

            if (results.isEmpty()) {
                log.warn("Buffer Cache Hit Chart 1h 데이터 없음: instanceId={}, startTime={}, endTime={}, results.size={}", 
                        instanceId, startTime, endTime, results.size());
                return buildEmptyBufferCacheHitChart1h();
            }

            log.debug("Buffer Cache Hit Chart 1h 데이터 조회 성공: instanceId={}, results.size={}", 
                    instanceId, results.size());

            // 쿼리에서 이미 시간순 정렬되므로 추가 정렬 불필요

            List<String> categories = results.stream()
                    .map(r -> formatTime(r.get("time_label")))
                    .collect(Collectors.toList());

            List<Double> hitRatio = results.stream()
                    .map(r -> getDoubleValue(r, "cache_hit_ratio"))
                    .collect(Collectors.toList());

            return MemoryDashboardResponse.BufferCacheHitChart1h.builder()
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


    // ========================================
    // 6시간 차트 (2개) - memory_agg_5m
    // ========================================

    /**
     * 차트 4: Temp File Generation (최근 15분)
     * 1분 집계 데이터 사용
     */
    private MemoryDashboardResponse.TempFileChart6h getTempFileChart6h(Long instanceId) {
        try {
            // endTime을 약간 늦춰서 최신 데이터를 확실히 포함
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusMinutes(15);

            List<Map<String, Object>> results = memoryMapper.selectTempFileChart6h(
                    instanceId, startTime, endTime);

            if (results.isEmpty()) {
                return buildEmptyTempFileChart6h();
            }

            // 쿼리에서 이미 시간순 정렬되므로 추가 정렬 불필요

            List<String> categories = results.stream()
                    .map(r -> formatTime(r.get("collected_at")))
                    .collect(Collectors.toList());

            List<Long> tempFileCount = results.stream()
                    .map(r -> getLongValue(r, "total_temp_files"))
                    .collect(Collectors.toList());

            List<Double> tempFileSizeMB = results.stream()
                    .map(r -> getLongValue(r, "total_temp_bytes") / (1024.0 * 1024.0))
                    .collect(Collectors.toList());

            return MemoryDashboardResponse.TempFileChart6h.builder()
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
     * 차트 5: I/O Wait Time (최근 15분)
     * 1분 집계 데이터 사용
     */
    private MemoryDashboardResponse.IoWaitTimeChart6h getIoWaitTimeChart6h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusMinutes(15);

            List<Map<String, Object>> results = memoryMapper.selectIoWaitTimeChart6h(
                    instanceId, startTime, endTime);

            if (results.isEmpty()) {
                return buildEmptyIoWaitTimeChart6h();
            }

            // 시간순 정렬
            results.sort(Comparator.comparing(r -> (OffsetDateTime) r.get("collected_at")));

            List<String> categories = results.stream()
                    .map(r -> formatTime(r.get("collected_at")))
                    .collect(Collectors.toList());

            List<Double> readWaitMs = results.stream()
                    .map(r -> getDoubleValue(r, "total_blk_read_time"))
                    .collect(Collectors.toList());

            List<Double> writeWaitMs = results.stream()
                    .map(r -> getDoubleValue(r, "total_blk_write_time"))
                    .collect(Collectors.toList());

            return MemoryDashboardResponse.IoWaitTimeChart6h.builder()
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
     * 차트 6: OS Memory Trend (실시간 SSE)
     * 
     * 주의: 이 메서드는 더 이상 사용되지 않습니다.
     * 프론트엔드에서 SSE로 실시간 데이터를 받아오므로 빈 데이터를 반환합니다.
     * 
     * @deprecated SSE로 대체됨 - buildEmptyOsMemoryTrendChart24h() 사용
     */
    @Deprecated
    private MemoryDashboardResponse.OsMemoryTrendChart24h getOsMemoryTrendChart24h(Long instanceId) {
        // SSE로 실시간 데이터를 받아오므로 빈 데이터 반환
        log.debug("OS Memory Trend Chart는 SSE로 실시간 데이터를 받아오므로 빈 데이터 반환: instanceId={}", instanceId);
        return buildEmptyOsMemoryTrendChart24h();
    }

    /**
     * 차트 7: Swap Usage Trend (24시간)
     * TODO: Agent에서 SWAP 데이터를 보내도록 수정 필요
     */
    /**
     * Swap Usage Trend Chart (실시간 SSE)
     * 
     * 주의: 이 메서드는 더 이상 사용되지 않습니다.
     * 프론트엔드에서 SSE로 실시간 데이터를 받아오므로 빈 데이터를 반환합니다.
     * 
     * @deprecated SSE로 대체됨 - buildEmptySwapUsageTrendChart24h() 사용
     */
    @Deprecated
    private MemoryDashboardResponse.SwapUsageTrendChart24h getSwapUsageTrendChart24h(Long instanceId) {
        // SSE로 실시간 데이터를 받아오므로 빈 데이터 반환
        log.debug("Swap Usage Trend Chart는 SSE로 실시간 데이터를 받아오므로 빈 데이터 반환: instanceId={}", instanceId);
        return buildEmptySwapUsageTrendChart24h();
    }


    /**
     * 차트 9: Top Tables by Buffer Usage (24시간)
     */
    private MemoryDashboardResponse.TopTablesByBufferChart24h getTopTablesByBufferChart24h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
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

            return MemoryDashboardResponse.TopTablesByBufferChart24h.builder()
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
     * Memory 리스트 조회 (페이징 지원)
     */
    public MemoryListResponse getMemoryList(Long instanceId, String timeRange, List<String> statusList, Integer page, Integer size) {
        if (instanceId == null) {
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }

        // 페이징 파라미터 설정 (기본값: page=0, size=20)
        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : 20;
        if (pageNum < 0) pageNum = 0;
        if (pageSize < 1) pageSize = 20;
        if (pageSize > 100) pageSize = 100; // 최대 100개로 제한

        OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime startTime = calculateStartTime(endTime, timeRange);

        try {
            // 섹션 2: 낮은 캐시 히트율 테이블 (페이징)
            List<MemoryListResponse.LowCacheHitItem> lowCacheHitList =
                    getLowCacheHitList(instanceId, startTime, endTime, statusList, pageNum, pageSize);

            // 총 개수 조회
            long lowCacheHitTotal = countLowCacheHitList(instanceId, startTime, endTime, statusList);
            long totalCount = lowCacheHitTotal;

            // 총 페이지 수 계산
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);

            return MemoryListResponse.builder()
                    .lowCacheHitList(lowCacheHitList)
                    .totalCount(totalCount)
                    .page(pageNum)
                    .size(pageSize)
                    .totalPages(totalPages)
                    .build();

        } catch (Exception e) {
            log.error("Memory 리스트 조회 실패", e);
            throw new RuntimeException("리스트 데이터 조회 중 오류 발생", e);
        }
    }


    /**
     * 섹션 2: 낮은 캐시 히트율 테이블 (페이징)
     */
    private List<MemoryListResponse.LowCacheHitItem> getLowCacheHitList(
            Long instanceId, OffsetDateTime startTime, OffsetDateTime endTime, List<String> statusList, int page, int size) {

        int offset = page * size;
        List<Map<String, Object>> results = memoryMapper.selectLowCacheHitWithPaging(
                instanceId, startTime, endTime, statusList, null, offset, size);

        return results.stream()
                .map(r -> MemoryListResponse.LowCacheHitItem.builder()
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

    /**
     * 낮은 캐시 히트율 리스트만 조회 (페이징 없음, 전체 조회)
     * 데이터베이스 레벨의 버퍼 캐시 히트율만 조회
     */
    public List<MemoryListResponse.LowCacheHitItem> getLowCacheHitListOnly(
            Long instanceId, String timeRange, List<String> statusList, List<String> typeList) {
        
        if (instanceId == null) {
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }

        OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
        OffsetDateTime startTime = calculateStartTime(endTime, timeRange);

        try {
            // 페이징 없이 전체 조회 (프론트엔드에서 클라이언트 사이드 페이징)
            // typeList는 무시 (데이터베이스 레벨이므로)
            List<Map<String, Object>> results = memoryMapper.selectLowCacheHitWithPaging(
                    instanceId, startTime, endTime, statusList, null, 0, 10000);

            return results.stream()
                    .map(r -> MemoryListResponse.LowCacheHitItem.builder()
                            .rankNum(getLongValue(r, "rank_num"))
                            .tableName(null) // 데이터베이스 레벨이므로 null
                            .databaseName((String) r.get("database_name"))
                            .cacheHitRatio(getDoubleValue(r, "cache_hit_ratio"))
                            .physicalReads(getLongValue(r, "physical_reads"))
                            .cacheHits(getLongValue(r, "cache_hits"))
                            .status((String) r.get("status"))
                            .build())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("낮은 캐시 히트율 리스트 조회 실패", e);
            throw new RuntimeException("리스트 데이터 조회 중 오류 발생", e);
        }
    }


    /**
     * 섹션 2 총 개수 조회
     */
    private long countLowCacheHitList(
            Long instanceId, OffsetDateTime startTime, OffsetDateTime endTime, List<String> statusList) {
        return memoryMapper.countLowCacheHitList(instanceId, startTime, endTime, statusList, null);
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
        // HH:mm 형식으로 통일
        if (timeObj instanceof OffsetDateTime) {
            return ((OffsetDateTime) timeObj).format(TIME_FORMATTER);
        }
        if (timeObj instanceof LocalDateTime) {
            return ((LocalDateTime) timeObj).format(TIME_FORMATTER);
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

    private MemoryDashboardResponse.OsMemoryUsageWidget buildEmptyOsMemoryUsageWidget() {
        return MemoryDashboardResponse.OsMemoryUsageWidget.builder()
                .usagePercent(0.0).trend("stable").status("normal")
                .totalGB(0L).usedGB(0L).availableGB(0L).cacheGB(0L).build();
    }

    private MemoryDashboardResponse.SwapUsageWidget buildEmptySwapUsageWidget() {
        return MemoryDashboardResponse.SwapUsageWidget.builder()
                .swapUsagePercent(0.0).status("normal")
                .totalSwapGB(0L).usedSwapGB(0L)
                .swapInPerSec(0L).swapOutPerSec(0L).build();
    }

    private MemoryDashboardResponse.SharedBufferHitWidget buildEmptySharedBufferHitWidget() {
        return MemoryDashboardResponse.SharedBufferHitWidget.builder()
                .hitRatio(0.0).status("normal")
                .cacheHits(0L).physicalReads(0L).build();
    }


    private MemoryDashboardResponse.TempFileUsageWidget buildEmptyTempFileUsageWidget() {
        return MemoryDashboardResponse.TempFileUsageWidget.builder()
                .tempFileRate(0.0).status("normal")
                .totalTempFiles(0L).totalTempMB(0L).message("정상").build();
    }

    private MemoryDashboardResponse.OsMemoryUsageChart1h buildEmptyOsMemoryUsageChart1h() {
        return MemoryDashboardResponse.OsMemoryUsageChart1h.builder()
                .categories(new ArrayList<>())
                .usedGB(new ArrayList<>())
                .cacheGB(new ArrayList<>())
                .bufferGB(new ArrayList<>()).build();
    }

    private MemoryDashboardResponse.BufferCacheHitChart1h buildEmptyBufferCacheHitChart1h() {
        return MemoryDashboardResponse.BufferCacheHitChart1h.builder()
                .categories(new ArrayList<>())
                .hitRatio(new ArrayList<>())
                .warningThreshold(85.0)
                .normalThreshold(95.0).build();
    }


    private MemoryDashboardResponse.TempFileChart6h buildEmptyTempFileChart6h() {
        return MemoryDashboardResponse.TempFileChart6h.builder()
                .categories(new ArrayList<>())
                .tempFileCount(new ArrayList<>())
                .tempFileSizeMB(new ArrayList<>()).build();
    }

    private MemoryDashboardResponse.IoWaitTimeChart6h buildEmptyIoWaitTimeChart6h() {
        return MemoryDashboardResponse.IoWaitTimeChart6h.builder()
                .categories(new ArrayList<>())
                .readWaitMs(new ArrayList<>())
                .writeWaitMs(new ArrayList<>()).build();
    }

    private MemoryDashboardResponse.OsMemoryTrendChart24h buildEmptyOsMemoryTrendChart24h() {
        return MemoryDashboardResponse.OsMemoryTrendChart24h.builder()
                .categories(new ArrayList<>())
                .usagePercent(new ArrayList<>())
                .warningThreshold(80.0)
                .dangerThreshold(90.0).build();
    }

    private MemoryDashboardResponse.SwapUsageTrendChart24h buildEmptySwapUsageTrendChart24h() {
        return MemoryDashboardResponse.SwapUsageTrendChart24h.builder()
                .categories(new ArrayList<>())
                .swapUsagePercent(new ArrayList<>())
                .swapInRate(new ArrayList<>())
                .swapOutRate(new ArrayList<>()).build();
    }


    private MemoryDashboardResponse.TopTablesByBufferChart24h buildEmptyTopTablesByBufferChart24h() {
        return MemoryDashboardResponse.TopTablesByBufferChart24h.builder()
                .tableNames(new ArrayList<>())
                .bufferCounts(new ArrayList<>())
                .usagePercent(new ArrayList<>()).build();
    }

    /**
     * Map에서 중첩된 Map 안전하게 추출
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return null;
        }
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }
}