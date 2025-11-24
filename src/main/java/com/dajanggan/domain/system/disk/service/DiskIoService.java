package com.dajanggan.domain.system.disk.service;

import com.dajanggan.domain.osmetric.dto.RedisOsMetricData;
import com.dajanggan.domain.osmetric.repository.OsMetricMapper;
import com.dajanggan.domain.osmetric.service.OsMetricRedisService;
import com.dajanggan.domain.system.disk.domain.DiskIoAgg;
import com.dajanggan.domain.system.disk.domain.DiskIoAgg5m;
import com.dajanggan.domain.system.disk.dto.*;
import com.dajanggan.domain.system.disk.repository.DiskIoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * Disk I/O 모니터링 서비스
 *
 * 데이터 소스:
 * 1. Redis (실시간 위젯) - OS 메트릭
 * 2. disk_io_agg_1m (1분) - PostgreSQL 메트릭
 * 3. disk_io_agg_5m (5분) - 6시간 차트
 * 4. disk_io_agg_30m (30분) - 24시간 차트
 * 5. os_metric_agg - OS 메트릭 집계
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiskIoService {

    private final DiskIoMapper diskIoMapper;
    private final OsMetricMapper osMetricMapper;
    private final OsMetricRedisService osMetricRedisService;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * instanceId가 없을 때 기본 인스턴스 ID를 반환하는 helper 메서드
     */
    private Long getDefaultInstanceIdIfNull(Long instanceId) {
        if (instanceId == null) {
            List<Long> activeInstanceIds = diskIoMapper.selectActiveInstanceIds();
            if (activeInstanceIds.isEmpty()) {
                throw new IllegalArgumentException("활성 인스턴스가 없습니다");
            }
            instanceId = activeInstanceIds.get(0);
            log.debug("instanceId가 제공되지 않아 기본 인스턴스 사용: instanceId={}", instanceId);
        }
        return instanceId;
    }

    // ========================================
    // 대시보드 전체 데이터 조회
    // ========================================

    /**
     * Disk I/O 대시보드 전체 데이터 조회
     */
    public DiskIoDashboardResponse getDiskIoDashboard(Long instanceId) {
        // instanceId가 없으면 첫 번째 활성 인스턴스 사용
        final Long finalInstanceId = getDefaultInstanceIdIfNull(instanceId);

        log.info("========== Disk I/O 대시보드 데이터 조회 시작: instanceId={} ==========", finalInstanceId);
        long startTime = System.currentTimeMillis();

        try {
            // 모든 데이터를 병렬로 조회
            CompletableFuture<DiskIoDashboardResponse.OsDiskUsageWidget> osDiskUsageFuture = 
                    CompletableFuture.supplyAsync(() -> getOsDiskUsageWidget(finalInstanceId));
            
            CompletableFuture<DiskIoDashboardResponse.DiskIoThroughputWidget> diskIoThroughputFuture = 
                    CompletableFuture.supplyAsync(() -> getDiskIoThroughputWidget(finalInstanceId));
            
            CompletableFuture<DiskIoDashboardResponse.BufferCacheHitWidget> bufferCacheHitFuture = 
                    CompletableFuture.supplyAsync(() -> getBufferCacheHitWidget(finalInstanceId));
            
            CompletableFuture<DiskIoDashboardResponse.BackendFsyncWidget> backendFsyncFuture = 
                    CompletableFuture.supplyAsync(() -> getBackendFsyncWidget(finalInstanceId));
            
            CompletableFuture<DiskIoDashboardResponse.DiskLatencyWidget> diskLatencyFuture = 
                    CompletableFuture.supplyAsync(() -> getDiskLatencyWidget(finalInstanceId));

            CompletableFuture<DiskIoDashboardResponse.OsDiskIoChart1h> osDiskIoChart1hFuture = 
                    CompletableFuture.supplyAsync(() -> getOsDiskIoChart1h(finalInstanceId));
            
            CompletableFuture<DiskIoDashboardResponse.BufferCacheChart1h> bufferCacheChart1hFuture = 
                    CompletableFuture.supplyAsync(() -> getBufferCacheChart1h(finalInstanceId));

            CompletableFuture<DiskIoDashboardResponse.IoLatencyChart6h> ioLatencyChart6hFuture = 
                    CompletableFuture.supplyAsync(() -> getIoLatencyChart6h(finalInstanceId));

            CompletableFuture<DiskIoDashboardResponse.DiskUsageChart24h> diskUsageChart24hFuture = 
                    CompletableFuture.supplyAsync(() -> getDiskUsageChart24h(finalInstanceId));
            
            CompletableFuture<DiskIoDashboardResponse.CheckpointVsBackendChart24h> checkpointChart24hFuture = 
                    CompletableFuture.supplyAsync(() -> getCheckpointVsBackendChart24h(finalInstanceId));
            
            CompletableFuture<DiskIoDashboardResponse.BackendFsyncChart24h> backendFsyncChart24hFuture = 
                    CompletableFuture.supplyAsync(() -> getBackendFsyncChart24h(finalInstanceId));
            
            CompletableFuture<DiskIoDashboardResponse.PhysicalVsCacheChart24h> physicalCacheChart24hFuture = 
                    CompletableFuture.supplyAsync(() -> getPhysicalVsCacheChart24h(finalInstanceId));
            
            CompletableFuture<DiskIoDashboardResponse.ThroughputChart24h> throughputChart24hFuture = 
                    CompletableFuture.supplyAsync(() -> getThroughputChart24h(finalInstanceId));

            // 모든 작업이 완료될 때까지 대기
            CompletableFuture.allOf(
                    osDiskUsageFuture, diskIoThroughputFuture, bufferCacheHitFuture,
                    backendFsyncFuture, diskLatencyFuture, osDiskIoChart1hFuture,
                    bufferCacheChart1hFuture, ioLatencyChart6hFuture, diskUsageChart24hFuture,
                    checkpointChart24hFuture, backendFsyncChart24hFuture, physicalCacheChart24hFuture,
                    throughputChart24hFuture
            ).join();

            // 결과 조합
            DiskIoDashboardResponse response = DiskIoDashboardResponse.builder()
                    .osDiskUsage(osDiskUsageFuture.join())
                    .diskIoThroughput(diskIoThroughputFuture.join())
                    .bufferCacheHit(bufferCacheHitFuture.join())
                    .backendFsync(backendFsyncFuture.join())
                    .diskLatency(diskLatencyFuture.join())
                    .osDiskIoChart1h(osDiskIoChart1hFuture.join())
                    .bufferCacheChart1h(bufferCacheChart1hFuture.join())
                    .ioLatencyChart6h(ioLatencyChart6hFuture.join())
                    .diskUsageChart24h(diskUsageChart24hFuture.join())
                    .checkpointChart24h(checkpointChart24hFuture.join())
                    .backendFsyncChart24h(backendFsyncChart24hFuture.join())
                    .physicalCacheChart24h(physicalCacheChart24hFuture.join())
                    .throughputChart24h(throughputChart24hFuture.join())
                    .build();

            long endTime = System.currentTimeMillis();
            log.info("========== Disk I/O 대시보드 데이터 조회 완료: instanceId={}, 소요시간={}ms ==========", 
                    finalInstanceId, (endTime - startTime));

            return response;

        } catch (Exception e) {
            log.error("Disk I/O 대시보드 데이터 조회 실패: instanceId={}", finalInstanceId, e);
            throw new RuntimeException("대시보드 데이터 조회 중 오류 발생", e);
        }
    }

    // ========================================
    // 실시간 위젯 (Redis 데이터)
    // ========================================

    /**
     * 위젯 1: OS Disk 사용률
     * 데이터: Redis 실시간 (5초)
     */
    private DiskIoDashboardResponse.OsDiskUsageWidget getOsDiskUsageWidget(Long instanceId) {
        try {
            // endTime을 약간 늦춰서 최신 데이터를 확실히 포함
            LocalDateTime endTime = LocalDateTime.now().plusMinutes(1);
            LocalDateTime startTime = endTime.minusMinutes(1);

            List<RedisOsMetricData> metrics = osMetricRedisService.getRecentMetricsByType(
                    instanceId, "DISK", startTime.atOffset(ZoneOffset.UTC), endTime.atOffset(ZoneOffset.UTC));

            if (metrics.isEmpty()) {
                return buildEmptyOsDiskUsageWidget();
            }

            // 가장 최근 데이터
            RedisOsMetricData latest = metrics.get(metrics.size() - 1);
            Map<String, Object> details = latest.getDetails();

            double usagePercent = getDouble(details, "usagePercent");
            long totalGB = getLong(details, "total") / (1024 * 1024 * 1024);
            long usedGB = getLong(details, "used") / (1024 * 1024 * 1024);
            long availableGB = getLong(details, "available") / (1024 * 1024 * 1024);

            // 추세 계산 (1분 전과 비교)
            String trend = "stable";
            if (metrics.size() > 1) {
                RedisOsMetricData prev = metrics.get(0);
                double prevUsage = getDouble(prev.getDetails(), "usagePercent");
                if (usagePercent > prevUsage + 1.0) trend = "up";
                else if (usagePercent < prevUsage - 1.0) trend = "down";
            }

            // 상태 판정
            String status = usagePercent > 90 ? "danger" : usagePercent > 80 ? "warning" : "normal";

            return DiskIoDashboardResponse.OsDiskUsageWidget.builder()
                    .usagePercent(usagePercent)
                    .trend(trend)
                    .status(status)
                    .totalGB(totalGB)
                    .usedGB(usedGB)
                    .availableGB(availableGB)
                    .build();

        } catch (Exception e) {
            log.error("OS Disk 사용률 위젯 조회 실패", e);
            return buildEmptyOsDiskUsageWidget();
        }
    }

    /**
     * 위젯 2: Disk I/O 처리량 (Throughput)
     * 데이터: Redis 실시간 (5초)
     */
    private DiskIoDashboardResponse.DiskIoThroughputWidget getDiskIoThroughputWidget(Long instanceId) {
        try {
            // endTime을 약간 늦춰서 최신 데이터를 확실히 포함
            // 시스템 시간대를 명시적으로 사용하여 UTC로 변환
            ZonedDateTime nowZoned = ZonedDateTime.now(ZoneId.systemDefault());
            OffsetDateTime endTime = nowZoned.plusMinutes(1).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
            OffsetDateTime startTime = endTime.minusMinutes(1);

            List<RedisOsMetricData> metrics = osMetricRedisService.getRecentMetricsByType(
                    instanceId, "DISK", startTime, endTime);

            if (metrics.isEmpty()) {
                return buildEmptyDiskIoThroughputWidget();
            }

            RedisOsMetricData latest = metrics.get(metrics.size() - 1);
            Map<String, Object> details = latest.getDetails();

            double readMBps = getDouble(details, "readSpeedMBps");
            double writeMBps = getDouble(details, "writeSpeedMBps");
            double totalMBps = readMBps + writeMBps;

            // 1분 전 대비 변화율 계산
            String readTrend = "stable";
            String writeTrend = "stable";
            double readChangePct = 0.0;
            double writeChangePct = 0.0;

            if (metrics.size() > 1) {
                RedisOsMetricData prev = metrics.get(0);
                Map<String, Object> prevDetails = prev.getDetails();

                double prevReadMBps = getDouble(prevDetails, "readSpeedMBps");
                double prevWriteMBps = getDouble(prevDetails, "writeSpeedMBps");

                if (prevReadMBps > 0) {
                    readChangePct = ((readMBps - prevReadMBps) / prevReadMBps) * 100;
                    readTrend = readChangePct > 10 ? "up" : readChangePct < -10 ? "down" : "stable";
                }

                if (prevWriteMBps > 0) {
                    writeChangePct = ((writeMBps - prevWriteMBps) / prevWriteMBps) * 100;
                    writeTrend = writeChangePct > 10 ? "up" : writeChangePct < -10 ? "down" : "stable";
                }
            }

            return DiskIoDashboardResponse.DiskIoThroughputWidget.builder()
                    .readMBps(readMBps)
                    .writeMBps(writeMBps)
                    .totalMBps(totalMBps)
                    .readTrend(readTrend)
                    .writeTrend(writeTrend)
                    .readChangePct(readChangePct)
                    .writeChangePct(writeChangePct)
                    .build();

        } catch (Exception e) {
            log.error("Disk I/O 처리량 위젯 조회 실패", e);
            return buildEmptyDiskIoThroughputWidget();
        }
    }

    /**
     * 위젯 3: Buffer Cache Hit Ratio
     * 데이터: disk_io_agg_1m (최근 1분)
     */
    private DiskIoDashboardResponse.BufferCacheHitWidget getBufferCacheHitWidget(Long instanceId) {
        try {
            Map<String, Object> result = diskIoMapper.selectRecentStats(instanceId);

            if (result == null || result.isEmpty()) {
                return buildEmptyBufferCacheHitWidget();
            }

            // avg_cache_hit_ratio 또는 buffer_hit_ratio 사용
            double hitRatio = getDoubleValue(result, "cache_hit_ratio");
            if (hitRatio == 0.0) {
                hitRatio = getDoubleValue(result, "buffer_hit_ratio");
            }

            long cacheHits = getLongValue(result, "total_cache_hits");
            long physicalReads = getLongValue(result, "total_physical_reads");

            // 상태 판정: >95% 정상, 85-95% 주의, <85% 위험
            String status = hitRatio > 95 ? "normal" : hitRatio > 85 ? "warning" : "danger";

            return DiskIoDashboardResponse.BufferCacheHitWidget.builder()
                    .hitRatio(hitRatio)
                    .status(status)
                    .cacheHits(cacheHits)
                    .physicalReads(physicalReads)
                    .build();

        } catch (Exception e) {
            log.error("Buffer Cache Hit 위젯 조회 실패", e);
            return buildEmptyBufferCacheHitWidget();
        }
    }

    /**
     * 위젯 4: Backend Fsync Rate
     * 데이터: disk_io_agg_1m (최근 15분)
     */
    private DiskIoDashboardResponse.BackendFsyncWidget getBackendFsyncWidget(Long instanceId) {
        try {
            // 최근 15분 데이터 조회
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusMinutes(15);
            
            Map<String, Object> result = diskIoMapper.selectBackendFsyncWidget15m(instanceId, startTime, endTime);

            if (result == null || result.isEmpty()) {
                return buildEmptyBackendFsyncWidget();
            }

            double fsyncRate = getDoubleValue(result, "backend_fsync_rate");
            long totalFsyncs = getLongValue(result, "total_backend_fsyncs");

            // 상태 판정: >100/s 주의 (병목 징후)
            String status = fsyncRate > 100 ? "warning" : "normal";
            String message = fsyncRate > 100 ? "병목 징후 감지" : "정상";

            return DiskIoDashboardResponse.BackendFsyncWidget.builder()
                    .fsyncRate(fsyncRate)
                    .status(status)
                    .totalFsyncs(totalFsyncs)
                    .message(message)
                    .build();

        } catch (Exception e) {
            log.error("Backend Fsync 위젯 조회 실패", e);
            return buildEmptyBackendFsyncWidget();
        }
    }

    /**
     * 위젯 5: Disk Latency
     * 데이터: disk_io_agg_1m (최근 15분)
     */
    private DiskIoDashboardResponse.DiskLatencyWidget getDiskLatencyWidget(Long instanceId) {
        try {
            // 최근 15분 데이터 조회
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusMinutes(15);
            
            Map<String, Object> result = diskIoMapper.selectDiskLatencyWidget15m(instanceId, startTime, endTime);

            if (result == null || result.isEmpty()) {
                return buildEmptyDiskLatencyWidget();
            }

            double avgReadLatency = getDoubleValue(result, "avg_read_latency");
            double avgWriteLatency = getDoubleValue(result, "avg_write_latency");
            double maxLatency = Math.max(avgReadLatency, avgWriteLatency);

            // 상태 판정: >10ms 주의, >50ms 위험
            String status = maxLatency > 50 ? "danger" : maxLatency > 10 ? "warning" : "normal";

            return DiskIoDashboardResponse.DiskLatencyWidget.builder()
                    .avgReadLatency(avgReadLatency)
                    .avgWriteLatency(avgWriteLatency)
                    .status(status)
                    .maxLatency(maxLatency)
                    .build();

        } catch (Exception e) {
            log.error("Disk Latency 위젯 조회 실패", e);
            return buildEmptyDiskLatencyWidget();
        }
    }

    // ========================================
    // 1시간 차트 (1분 집계)
    // ========================================

    /**
     * 차트 1: OS Disk I/O 추이 (최근 10분)
     * 데이터: os_metric_agg (1분) - DISK_READ, DISK_WRITE
     * 1분 집계 데이터 사용
     */
    private DiskIoDashboardResponse.OsDiskIoChart1h getOsDiskIoChart1h(Long instanceId) {
        try {
            // endTime을 약간 늦춰서 최신 데이터를 확실히 포함
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusMinutes(10);

            // OS Metric Agg에서 DISK_READ, DISK_WRITE 조회
            List<Map<String, Object>> readMetrics = osMetricMapper.selectAggregatedMetrics(
                    instanceId, "DISK_READ", startTime, endTime);
            List<Map<String, Object>> writeMetrics = osMetricMapper.selectAggregatedMetrics(
                    instanceId, "DISK_WRITE", startTime, endTime);

            if (readMetrics.isEmpty() && writeMetrics.isEmpty()) {
                return buildEmptyOsDiskIoChart1h();
            }

            // 시간 라벨 생성
            Set<String> timeSet = new TreeSet<>();
            readMetrics.forEach(m -> timeSet.add(formatTime(m.get("collected_at"))));
            writeMetrics.forEach(m -> timeSet.add(formatTime(m.get("collected_at"))));

            List<String> categories = new ArrayList<>(timeSet);

            // 읽기/쓰기 데이터 매핑
            Map<String, Double> readMap = readMetrics.stream()
                    .collect(Collectors.toMap(
                            m -> formatTime(m.get("collected_at")),
                            m -> getDoubleValue(m, "avg_value") / (1024.0 * 1024.0), // MB/s로 변환
                            (a, b) -> a
                    ));

            Map<String, Double> writeMap = writeMetrics.stream()
                    .collect(Collectors.toMap(
                            m -> formatTime(m.get("collected_at")),
                            m -> getDoubleValue(m, "avg_value") / (1024.0 * 1024.0),
                            (a, b) -> a
                    ));

            List<Double> readMBps = categories.stream()
                    .map(time -> readMap.getOrDefault(time, 0.0))
                    .collect(Collectors.toList());

            List<Double> writeMBps = categories.stream()
                    .map(time -> writeMap.getOrDefault(time, 0.0))
                    .collect(Collectors.toList());

            return DiskIoDashboardResponse.OsDiskIoChart1h.builder()
                    .categories(categories)
                    .readMBps(readMBps)
                    .writeMBps(writeMBps)
                    .build();

        } catch (Exception e) {
            log.error("OS Disk I/O Chart 1h 조회 실패", e);
            return buildEmptyOsDiskIoChart1h();
        }
    }

    /**
     * 차트 2: Buffer Cache Hit Ratio 추이 (최근 15분)
     * 데이터: disk_io_agg_1m (1분)
     * 1분 집계 데이터 사용
     */
    private DiskIoDashboardResponse.BufferCacheChart1h getBufferCacheChart1h(Long instanceId) {
        try {
            // endTime을 약간 늦춰서 최신 데이터를 확실히 포함
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusMinutes(15);

            List<Map<String, Object>> results = diskIoMapper.selectIoLatencyTimeSeries(
                    instanceId, startTime, endTime);

            if (results.isEmpty()) {
                return buildEmptyBufferCacheChart1h();
            }

            // 시간순 정렬
            results.sort(Comparator.comparing(r -> (String) r.get("time_label")));

            List<String> categories = results.stream()
                    .map(r -> formatTime(r.get("time_label")))
                    .collect(Collectors.toList());

            List<Double> hitRatio = results.stream()
                    .map(r -> getDoubleValue(r, "buffer_hit_ratio"))
                    .collect(Collectors.toList());

            return DiskIoDashboardResponse.BufferCacheChart1h.builder()
                    .categories(categories)
                    .hitRatio(hitRatio)
                    .warningThreshold(85.0)
                    .normalThreshold(95.0)
                    .build();

        } catch (Exception e) {
            log.error("Buffer Cache Chart 1h 조회 실패", e);
            return buildEmptyBufferCacheChart1h();
        }
    }

    // ========================================
    // 6시간 차트 (5분 집계)
    // ========================================

    /**
     * 차트 3: I/O Latency 추이 (최근 15분)
     * 데이터: disk_io_agg_1m (1분 집계)
     */
    private DiskIoDashboardResponse.IoLatencyChart6h getIoLatencyChart6h(Long instanceId) {
        try {
            // endTime을 약간 늦춰서 최신 데이터를 확실히 포함
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusMinutes(15);

            List<Map<String, Object>> results = diskIoMapper.selectIoLatencyTimeSeries(
                    instanceId, startTime, endTime);

            if (results.isEmpty()) {
                return buildEmptyIoLatencyChart6h();
            }

            // 시간순 정렬
            results.sort(Comparator.comparing(r -> (String) r.get("time_label")));

            List<String> categories = results.stream()
                    .map(r -> formatTime(r.get("time_label")))
                    .collect(Collectors.toList());

            List<Double> readLatency = results.stream()
                    .map(r -> getDoubleValue(r, "read_latency"))
                    .collect(Collectors.toList());

            List<Double> writeLatency = results.stream()
                    .map(r -> getDoubleValue(r, "write_latency"))
                    .collect(Collectors.toList());

            return DiskIoDashboardResponse.IoLatencyChart6h.builder()
                    .categories(categories)
                    .readLatency(readLatency)
                    .writeLatency(writeLatency)
                    .build();

        } catch (Exception e) {
            log.error("I/O Latency Chart 6h 조회 실패", e);
            return buildEmptyIoLatencyChart6h();
        }
    }

    // ========================================
    // 24시간 차트 (30분 집계)
    // ========================================

    /**
     * 차트 4: Disk 사용률 추이 (최근 15분)
     * 데이터: disk_io_agg_1m (1분 집계)
     * 주의: disk_io_agg_1m에는 디스크 사용률 데이터가 없으므로 os_metric_agg에서 조회하되 15분으로 제한
     */
    private DiskIoDashboardResponse.DiskUsageChart24h getDiskUsageChart24h(Long instanceId) {
        try {
            // endTime을 약간 늦춰서 최신 데이터를 확실히 포함
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusMinutes(15);

            // os_metric_agg에서 15분 데이터 조회
            List<Map<String, Object>> metrics = osMetricMapper.selectAggregatedMetrics(
                    instanceId, "DISK_USAGE", startTime, endTime);

            if (metrics.isEmpty()) {
                return buildEmptyDiskUsageChart24h();
            }

            List<String> categories = metrics.stream()
                    .map(m -> formatTime(m.get("collected_at")))
                    .collect(Collectors.toList());

            List<Double> usagePercent = metrics.stream()
                    .map(m -> getDoubleValue(m, "avg_value"))
                    .collect(Collectors.toList());

            return DiskIoDashboardResponse.DiskUsageChart24h.builder()
                    .categories(categories)
                    .usagePercent(usagePercent)
                    .warningThreshold(80.0)
                    .dangerThreshold(90.0)
                    .build();

        } catch (Exception e) {
            log.error("Disk Usage Chart 24h 조회 실패", e);
            return buildEmptyDiskUsageChart24h();
        }
    }

    /**
     * 차트 5: Checkpoint vs Backend Write (최근 15분)
     * 데이터: disk_io_agg_1m (1분 집계)
     */
    private DiskIoDashboardResponse.CheckpointVsBackendChart24h getCheckpointVsBackendChart24h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusMinutes(15);

            List<Map<String, Object>> results = diskIoMapper.selectCheckpointVsBackend1mTimeSeries(
                    instanceId, startTime, endTime);

            if (results.isEmpty()) {
                return buildEmptyCheckpointVsBackendChart24h();
            }

            // 시간순 정렬
            results.sort(Comparator.comparing(r -> (OffsetDateTime) r.get("collected_at")));

            List<String> categories = results.stream()
                    .map(r -> formatTime(r.get("collected_at")))
                    .collect(Collectors.toList());

            List<Long> checkpointBuffers = results.stream()
                    .map(r -> getLongValue(r, "checkpoint_buffers"))
                    .collect(Collectors.toList());

            List<Long> cleanBuffers = results.stream()
                    .map(r -> getLongValue(r, "clean_buffers"))
                    .collect(Collectors.toList());

            List<Long> backendBuffers = results.stream()
                    .map(r -> getLongValue(r, "backend_buffers"))
                    .collect(Collectors.toList());

            return DiskIoDashboardResponse.CheckpointVsBackendChart24h.builder()
                    .categories(categories)
                    .checkpointBuffers(checkpointBuffers)
                    .cleanBuffers(cleanBuffers)
                    .backendBuffers(backendBuffers)
                    .build();

        } catch (Exception e) {
            log.error("Checkpoint vs Backend Chart 24h 조회 실패", e);
            return buildEmptyCheckpointVsBackendChart24h();
        }
    }

    /**
     * 차트 6: Backend Fsync Rate 추이 (최근 15분)
     * 데이터: disk_io_agg_1m (1분 집계)
     */
    private DiskIoDashboardResponse.BackendFsyncChart24h getBackendFsyncChart24h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusMinutes(15);

            List<Map<String, Object>> results = diskIoMapper.selectBackendFsync1mTimeSeries(
                    instanceId, startTime, endTime);

            if (results.isEmpty()) {
                return buildEmptyBackendFsyncChart24h();
            }

            // 시간순 정렬
            results.sort(Comparator.comparing(r -> (OffsetDateTime) r.get("collected_at")));

            List<String> categories = results.stream()
                    .map(r -> formatTime(r.get("collected_at")))
                    .collect(Collectors.toList());

            List<Double> fsyncRate = results.stream()
                    .map(r -> getDoubleValue(r, "fsync_rate"))
                    .collect(Collectors.toList());

            return DiskIoDashboardResponse.BackendFsyncChart24h.builder()
                    .categories(categories)
                    .fsyncRate(fsyncRate)
                    .warningThreshold(100.0)
                    .build();

        } catch (Exception e) {
            log.error("Backend Fsync Chart 24h 조회 실패", e);
            return buildEmptyBackendFsyncChart24h();
        }
    }

    /**
     * 차트 7: Physical vs Cache Read (최근 15분)
     * 데이터: disk_io_agg_1m (1분 집계)
     */
    private DiskIoDashboardResponse.PhysicalVsCacheChart24h getPhysicalVsCacheChart24h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusMinutes(15);

            List<Map<String, Object>> results = diskIoMapper.selectPhysicalVsCache1mTimeSeries(
                    instanceId, startTime, endTime);

            if (results.isEmpty()) {
                return buildEmptyPhysicalVsCacheChart24h();
            }

            // 시간순 정렬
            results.sort(Comparator.comparing(r -> (OffsetDateTime) r.get("collected_at")));

            List<String> categories = results.stream()
                    .map(r -> formatTime(r.get("collected_at")))
                    .collect(Collectors.toList());

            List<Long> physicalReads = results.stream()
                    .map(r -> getLongValue(r, "physical_reads"))
                    .collect(Collectors.toList());

            List<Long> cacheHits = results.stream()
                    .map(r -> getLongValue(r, "cache_hits"))
                    .collect(Collectors.toList());

            return DiskIoDashboardResponse.PhysicalVsCacheChart24h.builder()
                    .categories(categories)
                    .physicalReads(physicalReads)
                    .cacheHits(cacheHits)
                    .build();

        } catch (Exception e) {
            log.error("Physical vs Cache Chart 24h 조회 실패", e);
            return buildEmptyPhysicalVsCacheChart24h();
        }
    }

    /**
     * 차트 8: Disk I/O Throughput (최근 15분)
     * 데이터: disk_io_agg_1m (1분 집계)
     */
    private DiskIoDashboardResponse.ThroughputChart24h getThroughputChart24h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusMinutes(15);

            List<Map<String, Object>> results = diskIoMapper.selectThroughputTimeSeries(
                    instanceId, startTime, endTime);

            if (results.isEmpty()) {
                return buildEmptyThroughputChart24h();
            }

            // 시간순 정렬
            results.sort(Comparator.comparing(r -> (String) r.get("time_label")));

            List<String> categories = results.stream()
                    .map(r -> formatTime(r.get("time_label")))
                    .collect(Collectors.toList());

            // Throughput 계산: delta_reads와 delta_writes를 MB/s로 변환
            // 블록 크기는 8KB이므로: (delta_reads + delta_writes) * 8 / 1024 = MB/s
            List<Double> readMBps = results.stream()
                    .map(r -> {
                        long reads = getLongValue(r, "reads");
                        // 1분당 읽기 수를 초당으로 변환하고 MB/s로 변환 (블록 크기 8KB)
                        return (reads / 60.0) * 8.0 / 1024.0;
                    })
                    .collect(Collectors.toList());

            List<Double> writeMBps = results.stream()
                    .map(r -> {
                        long writes = getLongValue(r, "writes");
                        // 1분당 쓰기 수를 초당으로 변환하고 MB/s로 변환 (블록 크기 8KB)
                        return (writes / 60.0) * 8.0 / 1024.0;
                    })
                    .collect(Collectors.toList());

            return DiskIoDashboardResponse.ThroughputChart24h.builder()
                    .categories(categories)
                    .readMBps(readMBps)
                    .writeMBps(writeMBps)
                    .build();

        } catch (Exception e) {
            log.error("Throughput Chart 24h 조회 실패", e);
            return buildEmptyThroughputChart24h();
        }
    }

    // ========================================
    // 리스트 페이지
    // ========================================

    /**
     * 리스트 데이터 조회
     */
    public DiskIoListResponse getDiskIoList(Long instanceId, String timeRange, List<String> statusList, Integer page, Integer size) {
        instanceId = getDefaultInstanceIdIfNull(instanceId);

        // 페이징 파라미터 설정 (기본값: page=0, size=20)
        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : 20;
        if (pageNum < 0) pageNum = 0;
        if (pageSize < 1) pageSize = 20;
        if (pageSize > 100) pageSize = 100; // 최대 100개로 제한

        OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime startTime = calculateStartTime(endTime, timeRange);

        try {
            // 섹션 1: 높은 Fsync 발생 시간대 (페이징)
            List<DiskIoListResponse.HighFsyncItem> highFsyncList = getHighFsyncList(instanceId, startTime, endTime, statusList, pageNum, pageSize);

            // 섹션 2: 낮은 Cache Hit Ratio 시간대 (페이징)
            List<DiskIoListResponse.LowCacheHitItem> lowCacheHitList = getLowCacheHitList(instanceId, startTime, endTime, statusList, pageNum, pageSize);

            // 총 개수 조회
            long highFsyncTotal = countHighFsyncList(instanceId, startTime, endTime, statusList);
            long lowCacheHitTotal = countLowCacheHitList(instanceId, startTime, endTime, statusList);
            long totalCount = highFsyncTotal + lowCacheHitTotal;

            // 총 페이지 수 계산
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);

            return DiskIoListResponse.builder()
                    .highFsyncList(highFsyncList)
                    .lowCacheHitList(lowCacheHitList)
                    .totalCount(totalCount)
                    .page(pageNum)
                    .size(pageSize)
                    .totalPages(totalPages)
                    .build();

        } catch (Exception e) {
            log.error("Disk I/O 리스트 조회 실패", e);
            throw new RuntimeException("리스트 데이터 조회 중 오류 발생", e);
        }
    }

    /**
     * 섹션 1: 높은 Fsync 발생 시간대 (페이징)
     */
    private List<DiskIoListResponse.HighFsyncItem> getHighFsyncList(Long instanceId, OffsetDateTime startTime,
                                                           OffsetDateTime endTime, List<String> statusList, int page, int size) {
        try {
            int offset = page * size;
            List<Map<String, Object>> results = diskIoMapper.selectHighFsyncWithPaging(
                    instanceId, startTime, endTime, statusList, offset, size);

            return results.stream()
                    .map(r -> DiskIoListResponse.HighFsyncItem.builder()
                            .collectedAt(convertToOffsetDateTime(r.get("collected_at")))
                            .fsyncRate(getDoubleValue(r, "fsync_rate"))
                            .bufferHitRatio(getDoubleValue(r, "buffer_hit_ratio"))
                            .avgLatency(getDoubleValue(r, "avg_write_latency"))
                            .status((String) r.get("status"))
                            .backendType((String) r.get("backend_type"))
                            .build())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("높은 Fsync 리스트 조회 실패", e);
            return new ArrayList<>();
        }
    }

    /**
     * 섹션 2: 낮은 Cache Hit Ratio 시간대 (페이징)
     */
    private List<DiskIoListResponse.LowCacheHitItem> getLowCacheHitList(Long instanceId, OffsetDateTime startTime,
                                                               OffsetDateTime endTime, List<String> statusList, int page, int size) {
        try {
            int offset = page * size;
            List<Map<String, Object>> results = diskIoMapper.selectLowCacheHitWithPaging(
                    instanceId, startTime, endTime, statusList, offset, size);

            return results.stream()
                    .map(r -> DiskIoListResponse.LowCacheHitItem.builder()
                            .collectedAt(convertToOffsetDateTime(r.get("collected_at")))
                            .bufferHitRatio(getDoubleValue(r, "buffer_hit_ratio"))
                            .physicalReads(getLongValue(r, "physical_reads"))
                            .cacheHits(getLongValue(r, "cache_hits"))
                            .status((String) r.get("status"))
                            .backendType((String) r.get("backend_type"))
                            .databaseName((String) r.get("database_name"))
                            .build())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("낮은 Cache Hit 리스트 조회 실패", e);
            return new ArrayList<>();
        }
    }

    /**
     * 섹션 1 총 개수 조회
     */
    private long countHighFsyncList(Long instanceId, OffsetDateTime startTime, OffsetDateTime endTime, List<String> statusList) {
        try {
            return diskIoMapper.countHighFsyncList(instanceId, startTime, endTime, statusList);
        } catch (Exception e) {
            log.error("높은 Fsync 총 개수 조회 실패", e);
            return 0L;
        }
    }

    /**
     * 섹션 2 총 개수 조회
     */
    private long countLowCacheHitList(Long instanceId, OffsetDateTime startTime, OffsetDateTime endTime, List<String> statusList) {
        try {
            return diskIoMapper.countLowCacheHitList(instanceId, startTime, endTime, statusList);
        } catch (Exception e) {
            log.error("낮은 Cache Hit 총 개수 조회 실패", e);
            return 0L;
        }
    }

    /**
     * 낮은 Cache Hit 리스트만 조회 (페이징 없음, 전체 조회)
     */
    public List<DiskIoListResponse.LowCacheHitItem> getLowCacheHitListOnly(
            Long instanceId, String timeRange, List<String> statusList) {
        
        if (instanceId == null) {
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }

        OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
        OffsetDateTime startTime = calculateStartTime(endTime, timeRange);

        try {
            // 페이징 없이 전체 조회 (프론트엔드에서 클라이언트 사이드 페이징)
            List<Map<String, Object>> results = diskIoMapper.selectLowCacheHitWithPaging(
                    instanceId, startTime, endTime, statusList, 0, 10000); // 충분히 큰 limit

            return results.stream()
                    .map(r -> DiskIoListResponse.LowCacheHitItem.builder()
                            .collectedAt(convertToOffsetDateTime(r.get("collected_at")))
                            .bufferHitRatio(getDoubleValue(r, "buffer_hit_ratio"))
                            .physicalReads(getLongValue(r, "physical_reads"))
                            .cacheHits(getLongValue(r, "cache_hits"))
                            .status((String) r.get("status"))
                            .backendType((String) r.get("backend_type"))
                            .databaseName((String) r.get("database_name"))
                            .build())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("낮은 Cache Hit 리스트 조회 실패", e);
            throw new RuntimeException("리스트 데이터 조회 중 오류 발생", e);
        }
    }

    /**
     * 낮은 Cache Hit 리스트만 조회 (페이징 포함)
     */
    public DiskIoListResponse getLowCacheHitListWithPaging(
            Long instanceId, String timeRange, List<String> statusList, Integer page, Integer size) {
        
        if (instanceId == null) {
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }

        // 페이징 파라미터 설정 (기본값: page=0, size=10)
        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : 10;
        if (pageNum < 0) pageNum = 0;
        if (pageSize < 1) pageSize = 10;
        if (pageSize > 100) pageSize = 100; // 최대 100개로 제한

        OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
        OffsetDateTime startTime = calculateStartTime(endTime, timeRange);

        try {
            // 낮은 Cache Hit 리스트만 조회 (페이징)
            List<DiskIoListResponse.LowCacheHitItem> lowCacheHitList = getLowCacheHitList(
                    instanceId, startTime, endTime, statusList, pageNum, pageSize);

            // 총 개수 조회
            long lowCacheHitTotal = countLowCacheHitList(instanceId, startTime, endTime, statusList);

            // 총 페이지 수 계산
            int totalPages = (int) Math.ceil((double) lowCacheHitTotal / pageSize);

            return DiskIoListResponse.builder()
                    .highFsyncList(new ArrayList<>()) // 빈 리스트
                    .lowCacheHitList(lowCacheHitList)
                    .totalCount(lowCacheHitTotal)
                    .page(pageNum)
                    .size(pageSize)
                    .totalPages(totalPages)
                    .build();

        } catch (Exception e) {
            log.error("낮은 Cache Hit 리스트 조회 실패", e);
            throw new RuntimeException("리스트 데이터 조회 중 오류 발생", e);
        }
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

    /**
     * Object를 OffsetDateTime으로 변환 (Timestamp, LocalDateTime, OffsetDateTime 지원)
     */
    private OffsetDateTime convertToOffsetDateTime(Object timeObj) {
        if (timeObj == null) {
            return OffsetDateTime.MIN;
        }
        if (timeObj instanceof OffsetDateTime) {
            return (OffsetDateTime) timeObj;
        }
        if (timeObj instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) timeObj).toInstant().atOffset(ZoneOffset.UTC);
        }
        if (timeObj instanceof LocalDateTime) {
            return ((LocalDateTime) timeObj).atOffset(ZoneOffset.UTC);
        }
        return OffsetDateTime.MIN;
    }

    private String formatTime(Object timeObj) {
        if (timeObj instanceof OffsetDateTime) {
            return ((OffsetDateTime) timeObj).format(TIME_FORMATTER);
        }
        if (timeObj instanceof LocalDateTime) {
            return ((LocalDateTime) timeObj).format(TIME_FORMATTER);
        }
        if (timeObj instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) timeObj).toInstant().atOffset(ZoneOffset.UTC).format(TIME_FORMATTER);
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

    private DiskIoDashboardResponse.OsDiskUsageWidget buildEmptyOsDiskUsageWidget() {
        return DiskIoDashboardResponse.OsDiskUsageWidget.builder()
                .usagePercent(0.0).trend("stable").status("normal")
                .totalGB(0L).usedGB(0L).availableGB(0L).build();
    }

    private DiskIoDashboardResponse.DiskIoThroughputWidget buildEmptyDiskIoThroughputWidget() {
        return DiskIoDashboardResponse.DiskIoThroughputWidget.builder()
                .readMBps(0.0).writeMBps(0.0).totalMBps(0.0)
                .readTrend("stable").writeTrend("stable")
                .readChangePct(0.0).writeChangePct(0.0).build();
    }

    private DiskIoDashboardResponse.BufferCacheHitWidget buildEmptyBufferCacheHitWidget() {
        return DiskIoDashboardResponse.BufferCacheHitWidget.builder()
                .hitRatio(0.0).status("normal")
                .cacheHits(0L).physicalReads(0L).build();
    }

    private DiskIoDashboardResponse.BackendFsyncWidget buildEmptyBackendFsyncWidget() {
        return DiskIoDashboardResponse.BackendFsyncWidget.builder()
                .fsyncRate(0.0).status("normal")
                .totalFsyncs(0L).message("정상").build();
    }

    private DiskIoDashboardResponse.DiskLatencyWidget buildEmptyDiskLatencyWidget() {
        return DiskIoDashboardResponse.DiskLatencyWidget.builder()
                .avgReadLatency(0.0).avgWriteLatency(0.0)
                .status("normal").maxLatency(0.0).build();
    }

    private DiskIoDashboardResponse.OsDiskIoChart1h buildEmptyOsDiskIoChart1h() {
        return DiskIoDashboardResponse.OsDiskIoChart1h.builder()
                .categories(new ArrayList<>())
                .readMBps(new ArrayList<>())
                .writeMBps(new ArrayList<>()).build();
    }

    private DiskIoDashboardResponse.BufferCacheChart1h buildEmptyBufferCacheChart1h() {
        return DiskIoDashboardResponse.BufferCacheChart1h.builder()
                .categories(new ArrayList<>())
                .hitRatio(new ArrayList<>())
                .warningThreshold(85.0)
                .normalThreshold(95.0).build();
    }

    private DiskIoDashboardResponse.IoLatencyChart6h buildEmptyIoLatencyChart6h() {
        return DiskIoDashboardResponse.IoLatencyChart6h.builder()
                .categories(new ArrayList<>())
                .readLatency(new ArrayList<>())
                .writeLatency(new ArrayList<>()).build();
    }

    private DiskIoDashboardResponse.DiskUsageChart24h buildEmptyDiskUsageChart24h() {
        return DiskIoDashboardResponse.DiskUsageChart24h.builder()
                .categories(new ArrayList<>())
                .usagePercent(new ArrayList<>())
                .warningThreshold(80.0)
                .dangerThreshold(90.0).build();
    }

    private DiskIoDashboardResponse.CheckpointVsBackendChart24h buildEmptyCheckpointVsBackendChart24h() {
        return DiskIoDashboardResponse.CheckpointVsBackendChart24h.builder()
                .categories(new ArrayList<>())
                .checkpointBuffers(new ArrayList<>())
                .cleanBuffers(new ArrayList<>())
                .backendBuffers(new ArrayList<>()).build();
    }

    private DiskIoDashboardResponse.BackendFsyncChart24h buildEmptyBackendFsyncChart24h() {
        return DiskIoDashboardResponse.BackendFsyncChart24h.builder()
                .categories(new ArrayList<>())
                .fsyncRate(new ArrayList<>())
                .warningThreshold(100.0).build();
    }

    private DiskIoDashboardResponse.PhysicalVsCacheChart24h buildEmptyPhysicalVsCacheChart24h() {
        return DiskIoDashboardResponse.PhysicalVsCacheChart24h.builder()
                .categories(new ArrayList<>())
                .physicalReads(new ArrayList<>())
                .cacheHits(new ArrayList<>()).build();
    }

    private DiskIoDashboardResponse.ThroughputChart24h buildEmptyThroughputChart24h() {
        return DiskIoDashboardResponse.ThroughputChart24h.builder()
                .categories(new ArrayList<>())
                .readMBps(new ArrayList<>())
                .writeMBps(new ArrayList<>()).build();
    }
}