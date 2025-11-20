package com.dajanggan.domain.system.disk.service;

import com.dajanggan.domain.osmetric.dto.RedisOsMetricData;
import com.dajanggan.domain.osmetric.repository.OsMetricMapper;
import com.dajanggan.domain.osmetric.service.OsMetricRedisService;
import com.dajanggan.domain.system.disk.domain.DiskIoAgg;
import com.dajanggan.domain.system.disk.domain.DiskIoAgg5m;
import com.dajanggan.domain.system.disk.domain.DiskIoAgg30m;
import com.dajanggan.domain.system.disk.dto.*;
import com.dajanggan.domain.system.disk.repository.DiskIoMapper;
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
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    // ========================================
    // 대시보드 전체 데이터 조회
    // ========================================

    /**
     * Disk I/O 대시보드 전체 데이터 조회
     */
    public DiskIoDashboardResponse getDiskIoDashboard(Long instanceId) {
        if (instanceId == null) {
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }

        log.info("========== Disk I/O 대시보드 데이터 조회 시작: instanceId={} ==========", instanceId);
        long startTime = System.currentTimeMillis();

        try {
            // 모든 데이터를 병렬로 조회
            CompletableFuture<DiskIoDashboardResponse.OsDiskUsageWidget> osDiskUsageFuture = 
                    CompletableFuture.supplyAsync(() -> getOsDiskUsageWidget(instanceId));
            
            CompletableFuture<DiskIoDashboardResponse.DiskIoThroughputWidget> diskIoThroughputFuture = 
                    CompletableFuture.supplyAsync(() -> getDiskIoThroughputWidget(instanceId));
            
            CompletableFuture<DiskIoDashboardResponse.BufferCacheHitWidget> bufferCacheHitFuture = 
                    CompletableFuture.supplyAsync(() -> getBufferCacheHitWidget(instanceId));
            
            CompletableFuture<DiskIoDashboardResponse.BackendFsyncWidget> backendFsyncFuture = 
                    CompletableFuture.supplyAsync(() -> getBackendFsyncWidget(instanceId));
            
            CompletableFuture<DiskIoDashboardResponse.DiskLatencyWidget> diskLatencyFuture = 
                    CompletableFuture.supplyAsync(() -> getDiskLatencyWidget(instanceId));

            CompletableFuture<DiskIoDashboardResponse.OsDiskIoChart1h> osDiskIoChart1hFuture = 
                    CompletableFuture.supplyAsync(() -> getOsDiskIoChart1h(instanceId));
            
            CompletableFuture<DiskIoDashboardResponse.BufferCacheChart1h> bufferCacheChart1hFuture = 
                    CompletableFuture.supplyAsync(() -> getBufferCacheChart1h(instanceId));

            CompletableFuture<DiskIoDashboardResponse.IoLatencyChart6h> ioLatencyChart6hFuture = 
                    CompletableFuture.supplyAsync(() -> getIoLatencyChart6h(instanceId));

            CompletableFuture<DiskIoDashboardResponse.DiskUsageChart24h> diskUsageChart24hFuture = 
                    CompletableFuture.supplyAsync(() -> getDiskUsageChart24h(instanceId));
            
            CompletableFuture<DiskIoDashboardResponse.CheckpointVsBackendChart24h> checkpointChart24hFuture = 
                    CompletableFuture.supplyAsync(() -> getCheckpointVsBackendChart24h(instanceId));
            
            CompletableFuture<DiskIoDashboardResponse.BackendFsyncChart24h> backendFsyncChart24hFuture = 
                    CompletableFuture.supplyAsync(() -> getBackendFsyncChart24h(instanceId));
            
            CompletableFuture<DiskIoDashboardResponse.PhysicalVsCacheChart24h> physicalCacheChart24hFuture = 
                    CompletableFuture.supplyAsync(() -> getPhysicalVsCacheChart24h(instanceId));
            
            CompletableFuture<DiskIoDashboardResponse.ThroughputChart24h> throughputChart24hFuture = 
                    CompletableFuture.supplyAsync(() -> getThroughputChart24h(instanceId));

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
                    instanceId, (endTime - startTime));

            return response;

        } catch (Exception e) {
            log.error("Disk I/O 대시보드 데이터 조회 실패: instanceId={}", instanceId, e);
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
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusMinutes(1);

            List<RedisOsMetricData> metrics = osMetricRedisService.getRecentMetricsByType(
                    instanceId, "DISK", startTime, endTime);

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
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
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

            log.info("Buffer Cache Hit 위젯 조회: instanceId={}, result={}", instanceId, result);

            if (result == null || result.isEmpty()) {
                log.warn("Buffer Cache Hit 위젯 데이터 없음: instanceId={}", instanceId);
                return buildEmptyBufferCacheHitWidget();
            }

            // avg_cache_hit_ratio 또는 buffer_hit_ratio 사용
            double hitRatio = getDoubleValue(result, "cache_hit_ratio");
            if (hitRatio == 0.0) {
                hitRatio = getDoubleValue(result, "buffer_hit_ratio");
            }

            long cacheHits = getLongValue(result, "total_cache_hits");
            long physicalReads = getLongValue(result, "total_physical_reads");

            log.info("Buffer Cache Hit 위젯 데이터: instanceId={}, hitRatio={}, cacheHits={}, physicalReads={}", 
                    instanceId, hitRatio, cacheHits, physicalReads);

            // 상태 판정: >95% 정상, 85-95% 주의, <85% 위험
            // 단, hitRatio가 0이고 physicalReads도 0이면 데이터가 없는 것으로 간주하여 "normal" 처리
            String status;
            if (hitRatio == 0.0 && physicalReads == 0 && cacheHits == 0) {
                // 데이터가 없는 경우 (수집 안 됨 또는 실제로 I/O가 없는 경우)
                status = "normal";
            } else if (hitRatio > 95) {
                status = "normal";
            } else if (hitRatio > 85) {
                status = "warning";
            } else {
                status = "danger";
            }

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
     * 데이터: disk_io_agg_1m (최근 1분)
     */
    private DiskIoDashboardResponse.BackendFsyncWidget getBackendFsyncWidget(Long instanceId) {
        try {
            Map<String, Object> result = diskIoMapper.selectRecentStats(instanceId);

            log.info("Backend Fsync 위젯 조회: instanceId={}, result={}", instanceId, result);

            if (result == null || result.isEmpty()) {
                log.warn("Backend Fsync 위젯 데이터 없음: instanceId={}", instanceId);
                return buildEmptyBackendFsyncWidget();
            }

            double fsyncRate = getDoubleValue(result, "backend_fsync_rate");
            long totalFsyncs = getLongValue(result, "total_backend_fsyncs");

            log.info("Backend Fsync 위젯 데이터: instanceId={}, fsyncRate={}, totalFsyncs={}", 
                    instanceId, fsyncRate, totalFsyncs);

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
     * 데이터: disk_io_agg_1m (최근 1분)
     */
    private DiskIoDashboardResponse.DiskLatencyWidget getDiskLatencyWidget(Long instanceId) {
        try {
            Map<String, Object> result = diskIoMapper.selectRecentStats(instanceId);

            log.info("Disk Latency 위젯 조회: instanceId={}, result={}", instanceId, result);

            if (result == null || result.isEmpty()) {
                log.warn("Disk Latency 위젯 데이터 없음: instanceId={}", instanceId);
                return buildEmptyDiskLatencyWidget();
            }

            double avgReadLatency = getDoubleValue(result, "avg_read_latency");
            double avgWriteLatency = getDoubleValue(result, "avg_write_latency");
            double maxLatency = Math.max(avgReadLatency, avgWriteLatency);

            log.info("Disk Latency 위젯 데이터: instanceId={}, avgReadLatency={}, avgWriteLatency={}, maxLatency={}", 
                    instanceId, avgReadLatency, avgWriteLatency, maxLatency);

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

            // 시간순 정렬 (time_label은 이미 TO_CHAR로 포맷된 문자열이므로 문자열 정렬)
            results.sort(Comparator.comparing(r -> (String) r.get("time_label")));

            // time_label은 이미 HH24:MI 형식이므로 그대로 사용
            List<String> categories = results.stream()
                    .map(r -> (String) r.get("time_label"))
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
     * 차트 3: I/O Latency 추이 (6시간)
     * 데이터: disk_io_agg_5m (5분 집계)
     * 5분 집계 데이터 사용
     */
    private DiskIoDashboardResponse.IoLatencyChart6h getIoLatencyChart6h(Long instanceId) {
        try {
            // endTime을 약간 늦춰서 최신 데이터를 확실히 포함
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusHours(6);

            List<Map<String, Object>> results = diskIoMapper.selectIoLatency5mTimeSeries(
                    instanceId, startTime, endTime);

            if (results.isEmpty()) {
                return buildEmptyIoLatencyChart6h();
            }

            // 시간순 정렬 (타입 안전한 변환)
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

            List<Double> readLatency = results.stream()
                    .map(r -> getDoubleValue(r, "avg_read_latency"))
                    .collect(Collectors.toList());

            List<Double> writeLatency = results.stream()
                    .map(r -> getDoubleValue(r, "avg_write_latency"))
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
     * 차트 4: Disk 사용률 추이 (24시간)
     * 테이블: os_metric_agg (metricType='DISK_USAGE')
     */
    private DiskIoDashboardResponse.DiskUsageChart24h getDiskUsageChart24h(Long instanceId) {
        try {
            // endTime을 약간 늦춰서 최신 데이터를 확실히 포함
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusHours(24);

            // 30분 집계 데이터 조회
            List<Map<String, Object>> metrics = osMetricMapper.selectAggregatedMetrics(
                    instanceId, "DISK_USAGE", startTime, endTime);

            if (metrics.isEmpty()) {
                return buildEmptyDiskUsageChart24h();
            }

            List<String> categories = metrics.stream()
                    .map(m -> formatDateTime(m.get("collected_at")))
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
     * 차트 5: Checkpoint vs Backend Write (24시간)
     * 테이블: disk_io_agg_30m
     * 최근 48개 데이터 포인트로 제한
     */
    private DiskIoDashboardResponse.CheckpointVsBackendChart24h getCheckpointVsBackendChart24h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusHours(24);

            log.info("Checkpoint vs Backend Chart 24h 조회 시작: instanceId={}, startTime={}, endTime={}", 
                    instanceId, startTime, endTime);

            List<Map<String, Object>> results = diskIoMapper.selectCheckpointVsBackend30mTimeSeriesWithLimit(
                    instanceId, startTime, endTime, 48);

            log.info("Checkpoint vs Backend Chart 24h 조회 결과: instanceId={}, resultsSize={}", 
                    instanceId, results.size());

            if (results.isEmpty()) {
                log.warn("Checkpoint vs Backend Chart 24h 데이터 없음: instanceId={}", instanceId);
                return buildEmptyCheckpointVsBackendChart24h();
            }

            // 데이터 샘플 로깅
            if (!results.isEmpty()) {
                Map<String, Object> sample = results.get(0);
                log.info("Checkpoint vs Backend 샘플 데이터: collected_at={}, checkpoint_buffers={}, clean_buffers={}, backend_buffers={}", 
                        sample.get("collected_at"), sample.get("checkpoint_buffers"), 
                        sample.get("clean_buffers"), sample.get("backend_buffers"));
            }

            // DESC로 조회했으므로 역순으로 정렬
            Collections.reverse(results);

            List<String> categories = results.stream()
                    .map(r -> formatDateTime(r.get("collected_at")))
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

            // 데이터 합계 로깅
            long totalCheckpoint = checkpointBuffers.stream().mapToLong(Long::longValue).sum();
            long totalClean = cleanBuffers.stream().mapToLong(Long::longValue).sum();
            long totalBackend = backendBuffers.stream().mapToLong(Long::longValue).sum();
            log.info("Checkpoint vs Backend Chart 24h 합계: checkpoint={}, clean={}, backend={}", 
                    totalCheckpoint, totalClean, totalBackend);

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
     * 차트 6: Backend Fsync Rate 추이 (24시간)
     * 테이블: disk_io_agg_30m
     * 최근 48개 데이터 포인트로 제한
     */
    private DiskIoDashboardResponse.BackendFsyncChart24h getBackendFsyncChart24h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusHours(24);

            log.info("Backend Fsync Chart 24h 조회 시작: instanceId={}, startTime={}, endTime={}", 
                    instanceId, startTime, endTime);

            List<Map<String, Object>> results = diskIoMapper.selectBackendFsync30mTimeSeriesWithLimit(
                    instanceId, startTime, endTime, 48);

            log.info("Backend Fsync Chart 24h 데이터 조회: instanceId={}, results={}", instanceId, results.size());

            if (results.isEmpty()) {
                log.warn("Backend Fsync Chart 24h 데이터 없음: instanceId={}", instanceId);
                return buildEmptyBackendFsyncChart24h();
            }

            // 데이터 샘플 로깅
            if (!results.isEmpty()) {
                Map<String, Object> sample = results.get(0);
                log.debug("Backend Fsync 샘플 데이터: collected_at={}, avg_fsync_rate={}, fsync_rate={}, keys={}", 
                        sample.get("collected_at"), sample.get("avg_fsync_rate"), sample.get("fsync_rate"), sample.keySet());
            }

            // DESC로 조회했으므로 역순으로 정렬
            Collections.reverse(results);

            // collected_at 기준으로 정렬 (타입 안전한 변환)
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

            List<String> categories = results.stream()
                    .map(r -> formatDateTime(r.get("collected_at")))
                    .collect(Collectors.toList());

            List<Double> fsyncRate = results.stream()
                    .map(r -> {
                        // avg_fsync_rate가 없으면 fsync_rate 사용
                        Double avgFsyncRate = getDoubleValue(r, "avg_fsync_rate");
                        if (avgFsyncRate == 0.0) {
                            avgFsyncRate = getDoubleValue(r, "fsync_rate");
                        }
                        return avgFsyncRate;
                    })
                    .collect(Collectors.toList());

            log.info("Backend Fsync Chart 24h 데이터 생성 완료: instanceId={}, categories={}, fsyncRate={}", 
                    instanceId, categories.size(), fsyncRate.size());

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
     * 차트 7: Physical vs Cache Read (24시간)
     * 테이블: disk_io_agg_30m
     * 최근 48개 데이터 포인트로 제한
     */
    private DiskIoDashboardResponse.PhysicalVsCacheChart24h getPhysicalVsCacheChart24h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusHours(24);

            List<Map<String, Object>> results = diskIoMapper.selectPhysicalVsCache30mTimeSeriesWithLimit(
                    instanceId, startTime, endTime, 48);

            if (results.isEmpty()) {
                return buildEmptyPhysicalVsCacheChart24h();
            }

            // DESC로 조회했으므로 역순으로 정렬
            Collections.reverse(results);

            List<String> categories = results.stream()
                    .map(r -> formatDateTime(r.get("collected_at")))
                    .collect(Collectors.toList());

            List<Long> physicalReads = results.stream()
                    .map(r -> getLongValue(r, "physical_reads"))
                    .collect(Collectors.toList());

            List<Long> cacheHits = results.stream()
                    .map(r -> getLongValue(r, "cache_hits"))
                    .collect(Collectors.toList());

            // 디버깅: 물리 읽기와 캐시 히트 값 확인
            log.info("Physical vs Cache Chart 24h 데이터: instanceId={}, 데이터 포인트 수={}, physicalReads 샘플={}, cacheHits 샘플={}", 
                    instanceId, results.size(), 
                    physicalReads.isEmpty() ? "없음" : physicalReads.subList(0, Math.min(5, physicalReads.size())),
                    cacheHits.isEmpty() ? "없음" : cacheHits.subList(0, Math.min(5, cacheHits.size())));

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
     * 차트 8: Disk I/O Throughput (24시간)
     * 테이블: os_metric_agg_1m (metricType='DISK_READ', 'DISK_WRITE')
     */
    private DiskIoDashboardResponse.ThroughputChart24h getThroughputChart24h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
            OffsetDateTime startTime = endTime.minusHours(24);

            log.info("Throughput Chart 24h 조회 시작: instanceId={}, startTime={}, endTime={}", 
                    instanceId, startTime, endTime);

            List<Map<String, Object>> readMetrics = osMetricMapper.selectAggregatedMetrics(
                    instanceId, "DISK_READ", startTime, endTime);
            List<Map<String, Object>> writeMetrics = osMetricMapper.selectAggregatedMetrics(
                    instanceId, "DISK_WRITE", startTime, endTime);

            log.info("Throughput Chart 24h 데이터 조회: instanceId={}, readMetrics={}, writeMetrics={}", 
                    instanceId, readMetrics.size(), writeMetrics.size());

            if (readMetrics.isEmpty() && writeMetrics.isEmpty()) {
                log.warn("Throughput Chart 24h 데이터 없음: instanceId={}", instanceId);
                return buildEmptyThroughputChart24h();
            }

            // 데이터 샘플 로깅
            if (!readMetrics.isEmpty()) {
                Map<String, Object> sample = readMetrics.get(0);
                log.info("DISK_READ 샘플 데이터: collected_at={}, avg_value={}, keys={}", 
                        sample.get("collected_at"), sample.get("avg_value"), sample.keySet());
            }
            if (!writeMetrics.isEmpty()) {
                Map<String, Object> sample = writeMetrics.get(0);
                log.info("DISK_WRITE 샘플 데이터: collected_at={}, avg_value={}, keys={}", 
                        sample.get("collected_at"), sample.get("avg_value"), sample.keySet());
            }

            // 모든 메트릭을 collected_at 기준으로 정렬 (시간 순서 보장)
            List<Map<String, Object>> allMetrics = new ArrayList<>();
            allMetrics.addAll(readMetrics);
            allMetrics.addAll(writeMetrics);
            
            // collected_at을 OffsetDateTime으로 변환하여 정렬
            allMetrics.sort(Comparator.comparing(m -> {
                Object collectedAt = m.get("collected_at");
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

            // 시간 순서대로 categories 생성 (중복 제거)
            LinkedHashSet<String> timeSet = new LinkedHashSet<>();
            for (Map<String, Object> metric : allMetrics) {
                String timeLabel = formatDateTime(metric.get("collected_at"));
                if (timeLabel != null && !timeLabel.isEmpty()) {
                    timeSet.add(timeLabel);
                }
            }
            List<String> categories = new ArrayList<>(timeSet);

            // 읽기/쓰기 데이터 매핑
            Map<String, Double> readMap = readMetrics.stream()
                    .collect(Collectors.toMap(
                            m -> formatDateTime(m.get("collected_at")),
                            m -> {
                                // avg_value가 null이면 0.0 반환
                                Double avgValue = getDoubleValue(m, "avg_value");
                                // 바이트를 MB로 변환 (1024^2)
                                return avgValue / (1024.0 * 1024.0);
                            },
                            (a, b) -> a
                    ));

            Map<String, Double> writeMap = writeMetrics.stream()
                    .collect(Collectors.toMap(
                            m -> formatDateTime(m.get("collected_at")),
                            m -> {
                                // avg_value가 null이면 0.0 반환
                                Double avgValue = getDoubleValue(m, "avg_value");
                                // 바이트를 MB로 변환 (1024^2)
                                return avgValue / (1024.0 * 1024.0);
                            },
                            (a, b) -> a
                    ));

            List<Double> readMBps = categories.stream()
                    .map(time -> readMap.getOrDefault(time, 0.0))
                    .collect(Collectors.toList());

            List<Double> writeMBps = categories.stream()
                    .map(time -> writeMap.getOrDefault(time, 0.0))
                    .collect(Collectors.toList());

            log.info("Throughput Chart 24h 데이터 생성 완료: instanceId={}, categories={}, readMBps={}, writeMBps={}", 
                    instanceId, categories.size(), readMBps.size(), writeMBps.size());

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
    public DiskIoListResponse getDiskIoList(Long instanceId, String timeRange, List<String> statusList) {
        if (instanceId == null) {
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }

        OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime startTime = calculateStartTime(endTime, timeRange);

        try {
            // 낮은 Cache Hit Ratio 시간대 (Top 20)
            List<DiskIoListResponse.LowCacheHitItem> lowCacheHitList = getLowCacheHitListInternal(instanceId, startTime, endTime, statusList);

            long totalCount = (long) lowCacheHitList.size();

            return DiskIoListResponse.builder()
                    .lowCacheHitList(lowCacheHitList)
                    .totalCount(totalCount)
                    .build();

        } catch (Exception e) {
            log.error("Disk I/O 리스트 조회 실패", e);
            throw new RuntimeException("리스트 데이터 조회 중 오류 발생", e);
        }
    }

    /**
     * 낮은 Cache Hit Ratio 시간대 리스트 조회
     */
    public List<DiskIoListResponse.LowCacheHitItem> getLowCacheHitList(Long instanceId, String timeRange, List<String> statusList) {
        if (instanceId == null) {
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }

        OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime startTime = calculateStartTime(endTime, timeRange);
        return getLowCacheHitListInternal(instanceId, startTime, endTime, statusList);
    }

    /**
     * 낮은 Cache Hit Ratio 시간대 (Top 20)
     */
    private List<DiskIoListResponse.LowCacheHitItem> getLowCacheHitListInternal(Long instanceId, OffsetDateTime startTime,
                                                               OffsetDateTime endTime, List<String> statusList) {
        try {
            List<Map<String, Object>> results = diskIoMapper.selectLowCacheHitTop20(
                    instanceId, startTime, endTime, statusList);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(KOREA_ZONE);
            
            return results.stream()
                    .map(r -> {
                        Object collectedAtObj = r.get("collected_at");
                        String collectedAtStr = "";
                        if (collectedAtObj instanceof OffsetDateTime) {
                            collectedAtStr = ((OffsetDateTime) collectedAtObj)
                                    .atZoneSameInstant(KOREA_ZONE)
                                    .format(formatter);
                        } else if (collectedAtObj != null) {
                            collectedAtStr = collectedAtObj.toString();
                        }
                        
                        return DiskIoListResponse.LowCacheHitItem.builder()
                                .collectedAt(collectedAtStr)
                                .bufferHitRatio(getDoubleValue(r, "buffer_hit_ratio"))
                                .physicalReads(getLongValue(r, "physical_reads"))
                                .cacheHits(getLongValue(r, "cache_hits"))
                                .status((String) r.get("status"))
                                .backendType((String) r.get("backend_type"))
                                .databaseName((String) r.get("database_name"))
                                .build();
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("낮은 Cache Hit 리스트 조회 실패", e);
            return new ArrayList<>();
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

    private String formatTime(Object timeObj) {
        if (timeObj instanceof OffsetDateTime) {
            return ((OffsetDateTime) timeObj).atZoneSameInstant(KOREA_ZONE).format(TIME_FORMATTER);
        }
        return timeObj != null ? timeObj.toString() : "";
    }

    private String formatDateTime(Object timeObj) {
        // HH:mm 형식으로 통일 (타입 안전한 변환)
        if (timeObj instanceof OffsetDateTime) {
            return ((OffsetDateTime) timeObj).atZoneSameInstant(KOREA_ZONE)
                    .format(DateTimeFormatter.ofPattern("HH:mm"));
        } else if (timeObj instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) timeObj).toInstant()
                    .atOffset(ZoneOffset.UTC)
                    .atZoneSameInstant(KOREA_ZONE)
                    .format(DateTimeFormatter.ofPattern("HH:mm"));
        } else if (timeObj instanceof java.time.LocalDateTime) {
            return ((java.time.LocalDateTime) timeObj)
                    .atOffset(ZoneOffset.UTC)
                    .atZoneSameInstant(KOREA_ZONE)
                    .format(DateTimeFormatter.ofPattern("HH:mm"));
        }
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