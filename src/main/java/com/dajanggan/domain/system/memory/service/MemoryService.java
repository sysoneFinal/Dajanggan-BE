package com.dajanggan.domain.system.memory.service;

import com.dajanggan.domain.osmetric.dto.RedisOsMetricData;
import com.dajanggan.domain.osmetric.repository.OsMetricMapper;
import com.dajanggan.domain.osmetric.service.OsMetricRedisService;
import com.dajanggan.domain.system.memory.dto.*;
import com.dajanggan.domain.system.memory.repository.MemoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

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

            CompletableFuture<MemoryDashboardResponse.OsMemoryUsageChart1h> osMemoryChart1hFuture =
                    CompletableFuture.supplyAsync(() -> getOsMemoryUsageChart1h(instanceId));
            CompletableFuture<MemoryDashboardResponse.BufferCacheHitChart1h> bufferCacheChart1hFuture =
                    CompletableFuture.supplyAsync(() -> getBufferCacheHitChart1h(instanceId));

            CompletableFuture<MemoryDashboardResponse.TempFileChart6h> tempFileChart6hFuture =
                    CompletableFuture.supplyAsync(() -> getTempFileChart6h(instanceId));
            CompletableFuture<MemoryDashboardResponse.IoWaitTimeChart6h> ioWaitTimeChart6hFuture =
                    CompletableFuture.supplyAsync(() -> getIoWaitTimeChart6h(instanceId));

            CompletableFuture<MemoryDashboardResponse.OsMemoryTrendChart24h> osMemoryTrend24hFuture =
                    CompletableFuture.supplyAsync(() -> getOsMemoryTrendChart24h(instanceId));
            CompletableFuture<MemoryDashboardResponse.SwapUsageTrendChart24h> swapTrend24hFuture =
                    CompletableFuture.supplyAsync(() -> getSwapUsageTrendChart24h(instanceId));

            // 모든 작업이 완료될 때까지 대기
            CompletableFuture.allOf(
                    osMemoryUsageFuture, swapUsageFuture, sharedBufferHitFuture,
                    tempFileUsageFuture, osMemoryChart1hFuture, bufferCacheChart1hFuture,
                    tempFileChart6hFuture,
                    ioWaitTimeChart6hFuture, osMemoryTrend24hFuture, swapTrend24hFuture
            ).join();

            // 결과 조합
            MemoryDashboardResponse.OsMemoryUsageWidget osMemoryUsage = osMemoryUsageFuture.join();
            MemoryDashboardResponse.SwapUsageWidget swapUsage = swapUsageFuture.join();
            MemoryDashboardResponse.SharedBufferHitWidget sharedBufferHit = sharedBufferHitFuture.join();
            MemoryDashboardResponse.TempFileUsageWidget tempFileUsage = tempFileUsageFuture.join();
            MemoryDashboardResponse.OsMemoryUsageChart1h osMemoryChart1h = osMemoryChart1hFuture.join();
            MemoryDashboardResponse.BufferCacheHitChart1h bufferCacheChart1h = bufferCacheChart1hFuture.join();
            MemoryDashboardResponse.TempFileChart6h tempFileChart6h = tempFileChart6hFuture.join();
            MemoryDashboardResponse.IoWaitTimeChart6h ioWaitTimeChart6h = ioWaitTimeChart6hFuture.join();
            MemoryDashboardResponse.OsMemoryTrendChart24h osMemoryTrend24h = osMemoryTrend24hFuture.join();
            MemoryDashboardResponse.SwapUsageTrendChart24h swapTrend24h = swapTrend24hFuture.join();

            // 디버깅: 각 위젯/차트 데이터 확인
            log.debug("위젯 데이터 - OS Memory: usagePercent={}, totalGB={}", 
                    osMemoryUsage.getUsagePercent(), osMemoryUsage.getTotalGB());
            log.debug("위젯 데이터 - Shared Buffer Hit: hitRatio={}, cacheHits={}", 
                    sharedBufferHit.getHitRatio(), sharedBufferHit.getCacheHits());
            log.debug("차트 데이터 - OS Memory Chart 1h: categories={}, usedGB size={}", 
                    osMemoryChart1h.getCategories() != null ? osMemoryChart1h.getCategories().size() : 0,
                    osMemoryChart1h.getUsedGB() != null ? osMemoryChart1h.getUsedGB().size() : 0);

            MemoryDashboardResponse response = MemoryDashboardResponse.builder()
                    .osMemoryUsage(osMemoryUsage)
                    .swapUsage(swapUsage)
                    .sharedBufferHit(sharedBufferHit)
                    .tempFileUsage(tempFileUsage)
                    .osMemoryChart1h(osMemoryChart1h)
                    .bufferCacheChart1h(bufferCacheChart1h)
                    .tempFileChart6h(tempFileChart6h)
                    .ioWaitTimeChart6h(ioWaitTimeChart6h)
                    .osMemoryTrend24h(osMemoryTrend24h)
                    .swapTrend24h(swapTrend24h)
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
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
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
     * 위젯 2: Swap Usage (SSE로 실시간 데이터 사용)
     */
    public MemoryDashboardResponse.SwapUsageWidget getSwapUsageWidget(Long instanceId) {
        // SSE로 실시간 데이터를 받아오므로 백엔드에서는 빈 데이터 반환
        log.debug("Swap Usage 위젯: SSE로 실시간 데이터 사용");
        return buildEmptySwapUsageWidget();
    }

    /**
     * 위젯 3: Shared Buffer Hit Ratio (PostgreSQL)
     */
    public MemoryDashboardResponse.SharedBufferHitWidget getSharedBufferHitWidget(Long instanceId) {
        try {
            // 차트와 동일하게 최근 1시간 데이터 조회
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusHours(1);

            log.info("Shared Buffer Hit 위젯 조회 시작: instanceId={}, startTime={}, endTime={}", 
                    instanceId, startTime, endTime);

            Map<String, Object> result = memoryMapper.selectSharedBufferHitWidget(
                    instanceId, startTime, endTime);

            log.info("Shared Buffer Hit 위젯 조회 결과: instanceId={}, result={}", 
                    instanceId, result);

            if (result == null || result.isEmpty()) {
                log.warn("Shared Buffer Hit 위젯 데이터 없음: instanceId={}, startTime={}, endTime={}", 
                        instanceId, startTime, endTime);
                return buildEmptySharedBufferHitWidget();
            }

            double hitRatio = getDoubleValue(result, "cache_hit_ratio");
            long cacheHits = getLongValue(result, "total_cache_hits");
            long physicalReads = getLongValue(result, "total_physical_reads");
            
            log.info("Shared Buffer Hit 위젯 데이터: instanceId={}, hitRatio={}, cacheHits={}, physicalReads={}", 
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
            log.error("Shared Buffer Hit 위젯 조회 실패", e);
            return buildEmptySharedBufferHitWidget();
        }
    }

    /**
     * 위젯 4: Temp File Usage (PostgreSQL)
     */
    public MemoryDashboardResponse.TempFileUsageWidget getTempFileUsageWidget(Long instanceId) {
        try {
            Map<String, Object> result = memoryMapper.selectTempFileUsageWidget(instanceId);

            if (result == null || result.isEmpty()) {
                log.warn("Temp File Usage 위젯 데이터 없음: instanceId={}", instanceId);
                return buildEmptyTempFileUsageWidget();
            }

            double tempFileRate = getDoubleValue(result, "temp_file_rate");
            long totalTempFiles = getLongValue(result, "total_temp_files");
            long totalTempMB = getLongValue(result, "total_temp_bytes") / (1024 * 1024);
            
            log.debug("Temp File Usage 위젯 데이터: instanceId={}, tempFileRate={}, totalTempFiles={}, totalTempMB={}", 
                    instanceId, tempFileRate, totalTempFiles, totalTempMB);

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
     * 차트 1: OS Memory Usage (최근 5분)
     * SSE로 실시간 데이터를 받아오므로 빈 데이터 반환
     */
    private MemoryDashboardResponse.OsMemoryUsageChart1h getOsMemoryUsageChart1h(Long instanceId) {
        // SSE로 실시간 데이터를 받아오므로 백엔드에서는 빈 데이터 반환
        log.debug("OS Memory Usage Chart 5m: SSE로 실시간 데이터 사용");
        return buildEmptyOsMemoryUsageChart1h();
    }

    /**
     * 차트 2: Buffer Cache Hit Ratio (최근 1시간)
     * memory_agg_1m 테이블에서 최근 1시간 데이터 조회
     */
    private MemoryDashboardResponse.BufferCacheHitChart1h getBufferCacheHitChart1h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusHours(1);

            log.info("Buffer Cache Hit Chart 1h 조회 시작: instanceId={}, startTime={}, endTime={}", 
                    instanceId, startTime, endTime);

            List<Map<String, Object>> results = memoryMapper.selectBufferCacheHitChart1h(
                    instanceId, startTime, endTime);

            log.info("Buffer Cache Hit Chart 1h 조회 결과: instanceId={}, 결과 수={}", 
                    instanceId, results.size());

            if (results.isEmpty()) {
                log.warn("Buffer Cache Hit Chart 1h 데이터 없음: instanceId={}, startTime={}, endTime={}. " +
                        "memory_agg_1m 테이블에 relname IS NULL인 데이터가 있는지 확인하세요.", 
                        instanceId, startTime, endTime);
                return buildEmptyBufferCacheHitChart1h();
            }

            // 샘플 데이터 로깅
            if (!results.isEmpty()) {
                Map<String, Object> sample = results.get(0);
                log.info("Buffer Cache Hit Chart 1h 샘플 데이터: collected_at={}, cache_hit_ratio={}", 
                        sample.get("collected_at"), sample.get("cache_hit_ratio"));
            }

            // collected_at을 OffsetDateTime으로 변환하여 정렬
            results.sort(Comparator.comparing(r -> {
                Object collectedAt = r.get("collected_at");
                if (collectedAt instanceof OffsetDateTime) {
                    return (OffsetDateTime) collectedAt;
                } else if (collectedAt instanceof java.sql.Timestamp) {
                    return ((java.sql.Timestamp) collectedAt).toInstant()
                            .atOffset(ZoneOffset.UTC);
                } else if (collectedAt instanceof java.time.LocalDateTime) {
                    return ((java.time.LocalDateTime) collectedAt).atOffset(ZoneOffset.UTC);
                } else {
                    return OffsetDateTime.now(ZoneOffset.UTC);
                }
            }));

            // 모든 데이터는 유지하되, 시간 형식은 HH:mm으로 통일
            List<String> categories = results.stream()
                    .map(r -> {
                        Object collectedAt = r.get("collected_at");
                        if (collectedAt instanceof OffsetDateTime) {
                            return ((OffsetDateTime) collectedAt).atZoneSameInstant(KOREA_ZONE)
                                    .format(DateTimeFormatter.ofPattern("HH:mm"));
                        } else if (collectedAt instanceof java.sql.Timestamp) {
                            return ((java.sql.Timestamp) collectedAt).toInstant()
                                    .atOffset(ZoneOffset.UTC)
                                    .atZoneSameInstant(KOREA_ZONE)
                                    .format(DateTimeFormatter.ofPattern("HH:mm"));
                        } else if (collectedAt instanceof java.time.LocalDateTime) {
                            return ((java.time.LocalDateTime) collectedAt)
                                    .atOffset(ZoneOffset.UTC)
                                    .atZoneSameInstant(KOREA_ZONE)
                                    .format(DateTimeFormatter.ofPattern("HH:mm"));
                        }
                        return formatTime(collectedAt);
                    })
                    .collect(Collectors.toList());

            List<Double> hitRatio = results.stream()
                    .map(r -> getDoubleValue(r, "cache_hit_ratio"))
                    .collect(Collectors.toList());

            return MemoryDashboardResponse.BufferCacheHitChart1h.builder()
                    .categories(categories)
                    .hitRatio(hitRatio)
                    .warningThreshold(90.0)
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
     * 차트 4: Temp File Generation (24시간)
     * 5분 집계 데이터 사용 (30분 간격으로 샘플링)
     */
    private MemoryDashboardResponse.TempFileChart6h getTempFileChart6h(Long instanceId) {
        try {
            // endTime을 약간 늦춰서 최신 데이터를 확실히 포함
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusHours(24);

            List<Map<String, Object>> results = memoryMapper.selectTempFileChart6h(
                    instanceId, startTime, endTime);

            if (results.isEmpty()) {
                return buildEmptyTempFileChart6h();
            }

            // 시간순 정렬 (Timestamp를 OffsetDateTime으로 변환)
            results.sort(Comparator.comparing(r -> {
                Object timeBucket = r.get("time_bucket");
                if (timeBucket instanceof OffsetDateTime) {
                    return (OffsetDateTime) timeBucket;
                } else if (timeBucket instanceof java.sql.Timestamp) {
                    return ((java.sql.Timestamp) timeBucket).toInstant()
                            .atOffset(ZoneOffset.UTC);
                } else if (timeBucket instanceof java.time.LocalDateTime) {
                    return ((java.time.LocalDateTime) timeBucket).atOffset(ZoneOffset.UTC);
                } else {
                    return OffsetDateTime.now(ZoneOffset.UTC);
                }
            }));

            // 24시간 데이터이므로 30분 간격으로 샘플링 (최대 48개 포인트)
            Map<OffsetDateTime, Map<String, Object>> sampledData = new LinkedHashMap<>();
            for (Map<String, Object> result : results) {
                Object timeBucketObj = result.get("time_bucket");
                OffsetDateTime timeBucket = null;
                
                if (timeBucketObj instanceof OffsetDateTime) {
                    timeBucket = (OffsetDateTime) timeBucketObj;
                } else if (timeBucketObj instanceof java.sql.Timestamp) {
                    timeBucket = ((java.sql.Timestamp) timeBucketObj).toInstant()
                            .atOffset(ZoneOffset.UTC);
                } else if (timeBucketObj instanceof java.time.LocalDateTime) {
                    timeBucket = ((java.time.LocalDateTime) timeBucketObj).atOffset(ZoneOffset.UTC);
                }
                
                if (timeBucket != null) {
                    // 30분 단위로 반올림
                    int minute = timeBucket.getMinute();
                    int roundedMinute = (minute / 30) * 30;
                    OffsetDateTime roundedTimeBucket = timeBucket
                            .withMinute(roundedMinute)
                            .withSecond(0)
                            .withNano(0);
                    
                    // 같은 시간대의 데이터가 있으면 합산
                    if (sampledData.containsKey(roundedTimeBucket)) {
                        Map<String, Object> existing = sampledData.get(roundedTimeBucket);
                        long existingCount = getLongValue(existing, "total_temp_files");
                        long existingBytes = getLongValue(existing, "total_temp_bytes");
                        long newCount = getLongValue(result, "total_temp_files");
                        long newBytes = getLongValue(result, "total_temp_bytes");
                        
                        existing.put("total_temp_files", existingCount + newCount);
                        existing.put("total_temp_bytes", existingBytes + newBytes);
                    } else {
                        Map<String, Object> newData = new HashMap<>(result);
                        newData.put("time_bucket", roundedTimeBucket);
                        sampledData.put(roundedTimeBucket, newData);
                    }
                }
            }

            // 시간순으로 정렬된 리스트 생성
            List<Map<String, Object>> sortedSampledData = sampledData.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());

            List<String> categories = sortedSampledData.stream()
                    .map(r -> formatTime(r.get("time_bucket")))
                    .collect(Collectors.toList());

            List<Long> tempFileCount = sortedSampledData.stream()
                    .map(r -> getLongValue(r, "total_temp_files"))
                    .collect(Collectors.toList());

            List<Double> tempFileSizeMB = sortedSampledData.stream()
                    .map(r -> getDoubleValue(r, "total_temp_bytes") / (1024.0 * 1024.0))
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
     * 차트 5: I/O Wait Time (6시간)
     * 5분 집계 데이터 사용
     */
    private MemoryDashboardResponse.IoWaitTimeChart6h getIoWaitTimeChart6h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusHours(6);

            List<Map<String, Object>> results = memoryMapper.selectIoWaitTimeChart6h(
                    instanceId, startTime, endTime);

            log.debug("I/O Wait Time Chart 6h 조회: instanceId={}, results={}", instanceId, results.size());

            if (results.isEmpty()) {
                log.warn("I/O Wait Time Chart 6h 데이터 없음: instanceId={}, startTime={}, endTime={}", 
                        instanceId, startTime, endTime);
                return buildEmptyIoWaitTimeChart6h();
            }
            
            // 데이터 샘플 로깅 (디버깅용)
            if (!results.isEmpty()) {
                Map<String, Object> sample = results.get(0);
                log.debug("I/O Wait Time Chart 6h 샘플 데이터: time_bucket={}, total_blk_read_time={}, total_blk_write_time={}", 
                        sample.get("time_bucket"), 
                        sample.get("total_blk_read_time"), 
                        sample.get("total_blk_write_time"));
            }

            // 시간순 정렬 (Timestamp를 OffsetDateTime으로 변환)
            results.sort(Comparator.comparing(r -> {
                Object timeBucket = r.get("time_bucket");
                if (timeBucket instanceof OffsetDateTime) {
                    return (OffsetDateTime) timeBucket;
                } else if (timeBucket instanceof java.sql.Timestamp) {
                    return ((java.sql.Timestamp) timeBucket).toInstant()
                            .atOffset(ZoneOffset.UTC);
                } else if (timeBucket instanceof java.time.LocalDateTime) {
                    return ((java.time.LocalDateTime) timeBucket).atOffset(ZoneOffset.UTC);
                } else {
                    return OffsetDateTime.now(ZoneOffset.UTC);
                }
            }));

            // 모든 데이터는 유지하되, 시간 형식은 HH:mm으로 통일
            List<String> categories = results.stream()
                    .map(r -> {
                        Object timeBucket = r.get("time_bucket");
                        if (timeBucket instanceof OffsetDateTime) {
                            return ((OffsetDateTime) timeBucket).atZoneSameInstant(KOREA_ZONE)
                                    .format(DateTimeFormatter.ofPattern("HH:mm"));
                        } else if (timeBucket instanceof java.sql.Timestamp) {
                            return ((java.sql.Timestamp) timeBucket).toInstant()
                                    .atOffset(ZoneOffset.UTC)
                                    .atZoneSameInstant(KOREA_ZONE)
                                    .format(DateTimeFormatter.ofPattern("HH:mm"));
                        } else if (timeBucket instanceof java.time.LocalDateTime) {
                            return ((java.time.LocalDateTime) timeBucket)
                                    .atOffset(ZoneOffset.UTC)
                                    .atZoneSameInstant(KOREA_ZONE)
                                    .format(DateTimeFormatter.ofPattern("HH:mm"));
                        }
                        return formatTime(timeBucket);
                    })
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
     * 차트 6: OS Memory Trend (24시간)
     * os_metric_agg_1m에서 30분 간격으로 샘플링 (최대 48개 포인트)
     */
    private MemoryDashboardResponse.OsMemoryTrendChart24h getOsMemoryTrendChart24h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusHours(24);

            List<Map<String, Object>> metrics = osMetricMapper.selectAggregatedMetrics(
                    instanceId, "MEMORY", startTime, endTime);

            if (metrics.isEmpty()) {
                log.warn("OS Memory Trend Chart 24h 데이터 없음: instanceId={}, startTime={}, endTime={}", 
                        instanceId, startTime, endTime);
                return buildEmptyOsMemoryTrendChart24h();
            }

            // 30분 간격으로 샘플링 (최대 48개 포인트)
            Map<String, Map<String, Object>> sampledData = new LinkedHashMap<>();
            for (Map<String, Object> metric : metrics) {
                Object collectedAtObj = metric.get("collected_at");
                if (collectedAtObj instanceof OffsetDateTime) {
                    OffsetDateTime collectedAt = (OffsetDateTime) collectedAtObj;
                    // 30분 단위로 키 생성 (HH:MM, 30분 단위로 반올림)
                    int minute = collectedAt.getMinute();
                    int roundedMinute = (minute / 30) * 30;
                    String timeKey = String.format("%02d:%02d", 
                            collectedAt.getHour(), roundedMinute);
                    // 같은 시간대의 데이터가 있으면 최신 것으로 업데이트
                    if (!sampledData.containsKey(timeKey) || 
                        ((OffsetDateTime) sampledData.get(timeKey).get("collected_at"))
                                .isBefore(collectedAt)) {
                        sampledData.put(timeKey, metric);
                    }
                }
            }

            List<String> categories = new ArrayList<>();
            List<Double> usagePercent = new ArrayList<>();

            for (Map<String, Object> metric : sampledData.values()) {
                Object collectedAtObj = metric.get("collected_at");
                if (collectedAtObj instanceof OffsetDateTime) {
                    OffsetDateTime collectedAt = (OffsetDateTime) collectedAtObj;
                    String timeLabel = collectedAt.atZoneSameInstant(KOREA_ZONE)
                            .format(DateTimeFormatter.ofPattern("HH:mm"));
                    categories.add(timeLabel);
                    usagePercent.add(getDoubleValue(metric, "usage_percent"));
                }
            }

            return MemoryDashboardResponse.OsMemoryTrendChart24h.builder()
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
     * os_metric_agg_1m에서 MEMORY 타입 데이터의 swap 정보 추출 (30분 간격으로 샘플링)
     */
    private MemoryDashboardResponse.SwapUsageTrendChart24h getSwapUsageTrendChart24h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusHours(24);

            // Redis에서 MEMORY 타입 데이터 조회 (os_metric_raw 대신 Redis 사용)
            List<RedisOsMetricData> metrics = osMetricRedisService.getRecentMetricsByType(
                    instanceId, "MEMORY", startTime, endTime);

            if (metrics.isEmpty()) {
                log.warn("Swap Usage Trend Chart 24h 데이터 없음: instanceId={}, startTime={}, endTime={}", 
                        instanceId, startTime, endTime);
                return buildEmptySwapUsageTrendChart24h();
            }

            // 30분 간격으로 샘플링 (최대 48개 포인트)
            Map<String, RedisOsMetricData> sampledData = new LinkedHashMap<>();
            for (RedisOsMetricData metric : metrics) {
                java.time.LocalDateTime collectedAtLocal = metric.getCollectedAt();
                if (collectedAtLocal != null) {
                    // LocalDateTime을 OffsetDateTime으로 변환 (UTC로 가정)
                    OffsetDateTime collectedAt = collectedAtLocal.atOffset(ZoneOffset.UTC);
                    // 30분 단위로 키 생성 (HH:MM, 30분 단위로 반올림)
                    int minute = collectedAt.getMinute();
                    int roundedMinute = (minute / 30) * 30;
                    String timeKey = String.format("%02d:%02d", 
                            collectedAt.getHour(), roundedMinute);
                    // 같은 시간대의 데이터가 있으면 최신 것으로 업데이트
                    if (!sampledData.containsKey(timeKey)) {
                        sampledData.put(timeKey, metric);
                    } else {
                        java.time.LocalDateTime existingLocal = sampledData.get(timeKey).getCollectedAt();
                        if (existingLocal != null && collectedAtLocal.isAfter(existingLocal)) {
                            sampledData.put(timeKey, metric);
                        }
                    }
                }
            }

            List<String> categories = new ArrayList<>();
            List<Double> swapUsagePercent = new ArrayList<>();
            List<Long> swapInRate = new ArrayList<>();
            List<Long> swapOutRate = new ArrayList<>();

            for (RedisOsMetricData metric : sampledData.values()) {
                Map<String, Object> details = metric.getDetails();
                if (details != null) {
                    Map<String, Object> swap = getMap(details, "swap");
                    if (swap != null) {
                        java.time.LocalDateTime collectedAtLocal = metric.getCollectedAt();
                        String timeLabel = "";
                        if (collectedAtLocal != null) {
                            OffsetDateTime collectedAt = collectedAtLocal.atOffset(ZoneOffset.UTC);
                            timeLabel = collectedAt.atZoneSameInstant(KOREA_ZONE)
                                    .format(DateTimeFormatter.ofPattern("HH:mm"));
                        }
                        categories.add(timeLabel);

                        long swapTotal = getLongValue(swap, "total");
                        long swapUsed = getLongValue(swap, "used");
                        long swapIn = getLongValue(swap, "swapIn");
                        long swapOut = getLongValue(swap, "swapOut");

                        // Swap 사용률 계산 (%)
                        double usage = swapTotal > 0 ? (swapUsed * 100.0 / swapTotal) : 0.0;
                        swapUsagePercent.add(usage);
                        swapInRate.add(swapIn);
                        swapOutRate.add(swapOut);
                    }
                }
            }

            if (categories.isEmpty()) {
                log.warn("Swap Usage Trend Chart 24h: swap 데이터가 없음");
                return buildEmptySwapUsageTrendChart24h();
            }

            return MemoryDashboardResponse.SwapUsageTrendChart24h.builder()
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

    // ========================================
    // 리스트 (1개 섹션)
    // ========================================

    /**
     * Memory 리스트 조회
     */
    public MemoryListResponse getMemoryList(Long instanceId, String timeRange, List<String> statusList) {
        if (instanceId == null) {
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }

        OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime startTime = calculateStartTime(endTime, timeRange);

        try {
            // 섹션 1: 낮은 캐시 히트율 테이블 Top 20
            List<MemoryListResponse.LowCacheHitItem> lowCacheHitList =
                    getLowCacheHitListInternal(instanceId, startTime, endTime, statusList, null);

            long totalCount = (long) lowCacheHitList.size();

            return MemoryListResponse.builder()
                    .lowCacheHitList(lowCacheHitList)
                    .totalCount(totalCount)
                    .build();

        } catch (Exception e) {
            log.error("Memory 리스트 조회 실패", e);
            throw new RuntimeException("리스트 데이터 조회 중 오류 발생", e);
        }
    }

    /**
     * 낮은 캐시 히트율 테이블 리스트 조회
     */
    public List<MemoryListResponse.LowCacheHitItem> getLowCacheHitList(Long instanceId, String timeRange, List<String> statusList, List<String> typeList) {
        if (instanceId == null) {
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }

        OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime startTime = calculateStartTime(endTime, timeRange);
        return getLowCacheHitListInternal(instanceId, startTime, endTime, statusList, typeList);
    }

    /**
     * 섹션 1: 낮은 캐시 히트율 테이블 Top 20
     */
    private List<MemoryListResponse.LowCacheHitItem> getLowCacheHitListInternal(
            Long instanceId, OffsetDateTime startTime, OffsetDateTime endTime, List<String> statusList, List<String> typeList) {

        log.info("낮은 캐시 히트율 리스트 조회 시작: instanceId={}, startTime={}, endTime={}, statusList={}, typeList={}",
                instanceId, startTime, endTime, statusList, typeList);

        // 디버깅: memory_agg_1m 테이블에 데이터가 있는지 확인
        try {
            Long totalCount = memoryMapper.countMemoryAgg1mByInstance(instanceId, startTime, endTime);
            Long relnameNotNullCount = memoryMapper.countMemoryAgg1mByInstanceWithRelname(instanceId, startTime, endTime);
            log.info("memory_agg_1m 데이터 확인: instanceId={}, 전체 레코드 수={}, relname IS NOT NULL 레코드 수={}", 
                    instanceId, totalCount, relnameNotNullCount);
        } catch (Exception e) {
            log.warn("memory_agg_1m 데이터 확인 중 오류", e);
        }

        List<Map<String, Object>> results = memoryMapper.selectLowCacheHitTop20(
                instanceId, startTime, endTime, statusList, typeList);

        log.info("낮은 캐시 히트율 리스트 조회 결과: instanceId={}, resultsSize={}", instanceId, results.size());
        if (!results.isEmpty()) {
            log.info("낮은 캐시 히트율 리스트 샘플 데이터: rank_num={}, relname={}, database_name={}, cache_hit_ratio={}, status={}",
                    results.get(0).get("rank_num"), results.get(0).get("relname"), results.get(0).get("database_name"),
                    results.get(0).get("cache_hit_ratio"), results.get(0).get("status"));
        } else {
            log.warn("낮은 캐시 히트율 리스트가 비어있습니다. memory_agg_1m 테이블에 relname IS NOT NULL인 데이터가 있는지 확인하세요.");
        }

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
            return ((OffsetDateTime) timeObj).atZoneSameInstant(KOREA_ZONE).format(TIME_FORMATTER);
        }
        return timeObj != null ? timeObj.toString() : "";
    }

    private String formatDateTime(Object timeObj) {
        // HH:mm 형식으로 통일
        return formatTime(timeObj);
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
                .warningThreshold(90.0)
                .normalThreshold(95.0)
                .build();
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