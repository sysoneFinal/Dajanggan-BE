package com.dajanggan.domain.system.disk.service;

import com.dajanggan.domain.osmetric.dto.RedisOsMetricData;
import com.dajanggan.domain.osmetric.repository.OsMetricMapper;
import com.dajanggan.domain.osmetric.service.OsMetricRedisService;
import com.dajanggan.domain.system.disk.domain.DiskIoAgg;
import com.dajanggan.domain.system.disk.domain.DiskIoAgg5m;
import com.dajanggan.domain.system.disk.domain.DiskIoAgg30m;
import com.dajanggan.domain.system.disk.dto.DiskIoDto;
import com.dajanggan.domain.system.disk.repository.DiskIoMapper;
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
 * Disk I/O 모니터링 서비스
 *
 * 데이터 소스:
 * 1. Redis (실시간 위젯) - OS 메트릭
 * 2. disk_io_agg (1분) - PostgreSQL 메트릭
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
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    // ========================================
    // 대시보드 전체 데이터 조회
    // ========================================

    /**
     * Disk I/O 대시보드 전체 데이터 조회
     */
    public DiskIoDto.DashboardResponse getDiskIoDashboard(Long instanceId) {
        if (instanceId == null) {
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }

        log.info("========== Disk I/O 대시보드 데이터 조회 시작: instanceId={} ==========", instanceId);

        try {
            // 실시간 위젯 (Redis 데이터)
            DiskIoDto.OsDiskUsageWidget osDiskUsage = getOsDiskUsageWidget(instanceId);
            DiskIoDto.DiskIoThroughputWidget diskIoThroughput = getDiskIoThroughputWidget(instanceId);
            DiskIoDto.BufferCacheHitWidget bufferCacheHit = getBufferCacheHitWidget(instanceId);
            DiskIoDto.BackendFsyncWidget backendFsync = getBackendFsyncWidget(instanceId);
            DiskIoDto.DiskLatencyWidget diskLatency = getDiskLatencyWidget(instanceId);

            // 1시간 차트 (1분 집계)
            DiskIoDto.OsDiskIoChart1h osDiskIoChart1h = getOsDiskIoChart1h(instanceId);
            DiskIoDto.BufferCacheChart1h bufferCacheChart1h = getBufferCacheChart1h(instanceId);

            // 6시간 차트 (5분 집계)
            DiskIoDto.IoLatencyChart6h ioLatencyChart6h = getIoLatencyChart6h(instanceId);

            // 24시간 차트 (30분 집계)
            DiskIoDto.DiskUsageChart24h diskUsageChart24h = getDiskUsageChart24h(instanceId);
            DiskIoDto.CheckpointVsBackendChart24h checkpointChart24h = getCheckpointVsBackendChart24h(instanceId);
            DiskIoDto.BackendFsyncChart24h backendFsyncChart24h = getBackendFsyncChart24h(instanceId);
            DiskIoDto.PhysicalVsCacheChart24h physicalCacheChart24h = getPhysicalVsCacheChart24h(instanceId);
            DiskIoDto.ThroughputChart24h throughputChart24h = getThroughputChart24h(instanceId);

            return DiskIoDto.DashboardResponse.builder()
                    .osDiskUsage(osDiskUsage)
                    .diskIoThroughput(diskIoThroughput)
                    .bufferCacheHit(bufferCacheHit)
                    .backendFsync(backendFsync)
                    .diskLatency(diskLatency)
                    .osDiskIoChart1h(osDiskIoChart1h)
                    .bufferCacheChart1h(bufferCacheChart1h)
                    .ioLatencyChart6h(ioLatencyChart6h)
                    .diskUsageChart24h(diskUsageChart24h)
                    .checkpointChart24h(checkpointChart24h)
                    .backendFsyncChart24h(backendFsyncChart24h)
                    .physicalCacheChart24h(physicalCacheChart24h)
                    .throughputChart24h(throughputChart24h)
                    .build();

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
    private DiskIoDto.OsDiskUsageWidget getOsDiskUsageWidget(Long instanceId) {
        try {
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusMinutes(1);

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

            return DiskIoDto.OsDiskUsageWidget.builder()
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
    private DiskIoDto.DiskIoThroughputWidget getDiskIoThroughputWidget(Long instanceId) {
        try {
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusMinutes(1);

            List<RedisOsMetricData> metrics = osMetricRedisService.getRecentMetricsByType(
                    instanceId, "DISK", startTime, endTime);

            if (metrics.isEmpty()) {
                return buildEmptyDiskIoThroughputWidget();
            }

            RedisOsMetricData latest = metrics.get(metrics.size() - 1);
            Map<String, Object> details = latest.getDetails();

            double readMBps = getDouble(details, "readBytes") / (1024.0 * 1024.0);
            double writeMBps = getDouble(details, "writeBytes") / (1024.0 * 1024.0);
            double totalMBps = readMBps + writeMBps;

            // 1분 전 대비 변화율 계산
            String readTrend = "stable";
            String writeTrend = "stable";
            double readChangePct = 0.0;
            double writeChangePct = 0.0;

            if (metrics.size() > 1) {
                RedisOsMetricData prev = metrics.get(0);
                Map<String, Object> prevDetails = prev.getDetails();

                double prevReadMBps = getDouble(prevDetails, "readBytes") / (1024.0 * 1024.0);
                double prevWriteMBps = getDouble(prevDetails, "writeBytes") / (1024.0 * 1024.0);

                if (prevReadMBps > 0) {
                    readChangePct = ((readMBps - prevReadMBps) / prevReadMBps) * 100;
                    readTrend = readChangePct > 10 ? "up" : readChangePct < -10 ? "down" : "stable";
                }

                if (prevWriteMBps > 0) {
                    writeChangePct = ((writeMBps - prevWriteMBps) / prevWriteMBps) * 100;
                    writeTrend = writeChangePct > 10 ? "up" : writeChangePct < -10 ? "down" : "stable";
                }
            }

            return DiskIoDto.DiskIoThroughputWidget.builder()
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
     * 데이터: disk_io_agg (최근 1분)
     */
    private DiskIoDto.BufferCacheHitWidget getBufferCacheHitWidget(Long instanceId) {
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

            return DiskIoDto.BufferCacheHitWidget.builder()
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
     * 데이터: disk_io_agg (최근 1분)
     */
    private DiskIoDto.BackendFsyncWidget getBackendFsyncWidget(Long instanceId) {
        try {
            Map<String, Object> result = diskIoMapper.selectRecentStats(instanceId);

            if (result == null || result.isEmpty()) {
                return buildEmptyBackendFsyncWidget();
            }

            double fsyncRate = getDoubleValue(result, "backend_fsync_rate");
            long totalFsyncs = getLongValue(result, "total_backend_fsyncs");

            // 상태 판정: >100/s 주의 (병목 징후)
            String status = fsyncRate > 100 ? "warning" : "normal";
            String message = fsyncRate > 100 ? "병목 징후 감지" : "정상";

            return DiskIoDto.BackendFsyncWidget.builder()
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
     * 데이터: disk_io_agg (최근 1분)
     */
    private DiskIoDto.DiskLatencyWidget getDiskLatencyWidget(Long instanceId) {
        try {
            Map<String, Object> result = diskIoMapper.selectRecentStats(instanceId);

            if (result == null || result.isEmpty()) {
                return buildEmptyDiskLatencyWidget();
            }

            double avgReadLatency = getDoubleValue(result, "avg_read_latency");
            double avgWriteLatency = getDoubleValue(result, "avg_write_latency");
            double maxLatency = Math.max(avgReadLatency, avgWriteLatency);

            // 상태 판정: >10ms 주의, >50ms 위험
            String status = maxLatency > 50 ? "danger" : maxLatency > 10 ? "warning" : "normal";

            return DiskIoDto.DiskLatencyWidget.builder()
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
     * 차트 1: OS Disk I/O 추이 (1시간)
     * 데이터: os_metric_agg (1분) - DISK_READ, DISK_WRITE
     */
    private DiskIoDto.OsDiskIoChart1h getOsDiskIoChart1h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime startTime = endTime.minusHours(1);

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

            return DiskIoDto.OsDiskIoChart1h.builder()
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
     * 차트 2: Buffer Cache Hit Ratio 추이 (1시간)
     * 데이터: disk_io_agg (1분)
     */
    private DiskIoDto.BufferCacheChart1h getBufferCacheChart1h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime startTime = endTime.minusHours(1);

            List<Map<String, Object>> results = diskIoMapper.selectIoLatencyTimeSeries(
                    instanceId, startTime, endTime);

            if (results.isEmpty()) {
                return buildEmptyBufferCacheChart1h();
            }

            List<String> categories = results.stream()
                    .map(r -> formatTime(r.get("time_label")))
                    .collect(Collectors.toList());

            List<Double> hitRatio = results.stream()
                    .map(r -> getDoubleValue(r, "buffer_hit_ratio"))
                    .collect(Collectors.toList());

            return DiskIoDto.BufferCacheChart1h.builder()
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
     */
    private DiskIoDto.IoLatencyChart6h getIoLatencyChart6h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime startTime = endTime.minusHours(6);

            // TODO: DiskIoMapper에 disk_io_agg_5m 조회 메서드 추가 필요
            // 임시로 빈 데이터 반환
            return buildEmptyIoLatencyChart6h();

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
    private DiskIoDto.DiskUsageChart24h getDiskUsageChart24h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
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

            return DiskIoDto.DiskUsageChart24h.builder()
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
     */
    private DiskIoDto.CheckpointVsBackendChart24h getCheckpointVsBackendChart24h(Long instanceId) {
        try {
            // TODO: DiskIoMapper에 disk_io_agg_30m 조회 메서드 추가 필요
            return buildEmptyCheckpointVsBackendChart24h();

        } catch (Exception e) {
            log.error("Checkpoint vs Backend Chart 24h 조회 실패", e);
            return buildEmptyCheckpointVsBackendChart24h();
        }
    }

    /**
     * 차트 6: Backend Fsync Rate 추이 (24시간)
     * 테이블: disk_io_agg_30m
     */
    private DiskIoDto.BackendFsyncChart24h getBackendFsyncChart24h(Long instanceId) {
        try {
            // TODO: DiskIoMapper에 disk_io_agg_30m 조회 메서드 추가 필요
            return buildEmptyBackendFsyncChart24h();

        } catch (Exception e) {
            log.error("Backend Fsync Chart 24h 조회 실패", e);
            return buildEmptyBackendFsyncChart24h();
        }
    }

    /**
     * 차트 7: Physical vs Cache Read (24시간)
     * 테이블: disk_io_agg_30m
     */
    private DiskIoDto.PhysicalVsCacheChart24h getPhysicalVsCacheChart24h(Long instanceId) {
        try {
            // TODO: DiskIoMapper에 disk_io_agg_30m 조회 메서드 추가 필요
            return buildEmptyPhysicalVsCacheChart24h();

        } catch (Exception e) {
            log.error("Physical vs Cache Chart 24h 조회 실패", e);
            return buildEmptyPhysicalVsCacheChart24h();
        }
    }

    /**
     * 차트 8: Disk I/O Throughput (24시간)
     * 테이블: os_metric_agg (metricType='DISK_READ', 'DISK_WRITE')
     */
    private DiskIoDto.ThroughputChart24h getThroughputChart24h(Long instanceId) {
        try {
            OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime startTime = endTime.minusHours(24);

            List<Map<String, Object>> readMetrics = osMetricMapper.selectAggregatedMetrics(
                    instanceId, "DISK_READ", startTime, endTime);
            List<Map<String, Object>> writeMetrics = osMetricMapper.selectAggregatedMetrics(
                    instanceId, "DISK_WRITE", startTime, endTime);

            if (readMetrics.isEmpty() && writeMetrics.isEmpty()) {
                return buildEmptyThroughputChart24h();
            }

            Set<String> timeSet = new TreeSet<>();
            readMetrics.forEach(m -> timeSet.add(formatDateTime(m.get("collected_at"))));
            writeMetrics.forEach(m -> timeSet.add(formatDateTime(m.get("collected_at"))));

            List<String> categories = new ArrayList<>(timeSet);

            Map<String, Double> readMap = readMetrics.stream()
                    .collect(Collectors.toMap(
                            m -> formatDateTime(m.get("collected_at")),
                            m -> getDoubleValue(m, "avg_value") / (1024.0 * 1024.0),
                            (a, b) -> a
                    ));

            Map<String, Double> writeMap = writeMetrics.stream()
                    .collect(Collectors.toMap(
                            m -> formatDateTime(m.get("collected_at")),
                            m -> getDoubleValue(m, "avg_value") / (1024.0 * 1024.0),
                            (a, b) -> a
                    ));

            List<Double> readMBps = categories.stream()
                    .map(time -> readMap.getOrDefault(time, 0.0))
                    .collect(Collectors.toList());

            List<Double> writeMBps = categories.stream()
                    .map(time -> writeMap.getOrDefault(time, 0.0))
                    .collect(Collectors.toList());

            return DiskIoDto.ThroughputChart24h.builder()
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
    public DiskIoDto.ListResponse getDiskIoList(Long instanceId, String timeRange, List<String> statusList) {
        if (instanceId == null) {
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }

        OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime startTime = calculateStartTime(endTime, timeRange);

        try {
            // 섹션 1: 높은 Fsync 발생 시간대 (Top 20)
            List<DiskIoDto.HighFsyncItem> highFsyncList = getHighFsyncList(instanceId, startTime, endTime, statusList);

            // 섹션 2: 낮은 Cache Hit Ratio 시간대 (Top 20)
            List<DiskIoDto.LowCacheHitItem> lowCacheHitList = getLowCacheHitList(instanceId, startTime, endTime, statusList);

            long totalCount = (long) highFsyncList.size() + lowCacheHitList.size();

            return DiskIoDto.ListResponse.builder()
                    .highFsyncList(highFsyncList)
                    .lowCacheHitList(lowCacheHitList)
                    .totalCount(totalCount)
                    .build();

        } catch (Exception e) {
            log.error("Disk I/O 리스트 조회 실패", e);
            throw new RuntimeException("리스트 데이터 조회 중 오류 발생", e);
        }
    }

    /**
     * 섹션 1: 높은 Fsync 발생 시간대 (Top 20)
     */
    private List<DiskIoDto.HighFsyncItem> getHighFsyncList(Long instanceId, OffsetDateTime startTime,
                                                           OffsetDateTime endTime, List<String> statusList) {
        // TODO: DiskIoMapper에 high fsync 조회 메서드 추가 필요
        return new ArrayList<>();
    }

    /**
     * 섹션 2: 낮은 Cache Hit Ratio 시간대 (Top 20)
     */
    private List<DiskIoDto.LowCacheHitItem> getLowCacheHitList(Long instanceId, OffsetDateTime startTime,
                                                               OffsetDateTime endTime, List<String> statusList) {
        // TODO: DiskIoMapper에 low cache hit 조회 메서드 추가 필요
        return new ArrayList<>();
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

    private DiskIoDto.OsDiskUsageWidget buildEmptyOsDiskUsageWidget() {
        return DiskIoDto.OsDiskUsageWidget.builder()
                .usagePercent(0.0).trend("stable").status("normal")
                .totalGB(0L).usedGB(0L).availableGB(0L).build();
    }

    private DiskIoDto.DiskIoThroughputWidget buildEmptyDiskIoThroughputWidget() {
        return DiskIoDto.DiskIoThroughputWidget.builder()
                .readMBps(0.0).writeMBps(0.0).totalMBps(0.0)
                .readTrend("stable").writeTrend("stable")
                .readChangePct(0.0).writeChangePct(0.0).build();
    }

    private DiskIoDto.BufferCacheHitWidget buildEmptyBufferCacheHitWidget() {
        return DiskIoDto.BufferCacheHitWidget.builder()
                .hitRatio(0.0).status("normal")
                .cacheHits(0L).physicalReads(0L).build();
    }

    private DiskIoDto.BackendFsyncWidget buildEmptyBackendFsyncWidget() {
        return DiskIoDto.BackendFsyncWidget.builder()
                .fsyncRate(0.0).status("normal")
                .totalFsyncs(0L).message("정상").build();
    }

    private DiskIoDto.DiskLatencyWidget buildEmptyDiskLatencyWidget() {
        return DiskIoDto.DiskLatencyWidget.builder()
                .avgReadLatency(0.0).avgWriteLatency(0.0)
                .status("normal").maxLatency(0.0).build();
    }

    private DiskIoDto.OsDiskIoChart1h buildEmptyOsDiskIoChart1h() {
        return DiskIoDto.OsDiskIoChart1h.builder()
                .categories(new ArrayList<>())
                .readMBps(new ArrayList<>())
                .writeMBps(new ArrayList<>()).build();
    }

    private DiskIoDto.BufferCacheChart1h buildEmptyBufferCacheChart1h() {
        return DiskIoDto.BufferCacheChart1h.builder()
                .categories(new ArrayList<>())
                .hitRatio(new ArrayList<>())
                .warningThreshold(85.0)
                .normalThreshold(95.0).build();
    }

    private DiskIoDto.IoLatencyChart6h buildEmptyIoLatencyChart6h() {
        return DiskIoDto.IoLatencyChart6h.builder()
                .categories(new ArrayList<>())
                .readLatency(new ArrayList<>())
                .writeLatency(new ArrayList<>()).build();
    }

    private DiskIoDto.DiskUsageChart24h buildEmptyDiskUsageChart24h() {
        return DiskIoDto.DiskUsageChart24h.builder()
                .categories(new ArrayList<>())
                .usagePercent(new ArrayList<>())
                .warningThreshold(80.0)
                .dangerThreshold(90.0).build();
    }

    private DiskIoDto.CheckpointVsBackendChart24h buildEmptyCheckpointVsBackendChart24h() {
        return DiskIoDto.CheckpointVsBackendChart24h.builder()
                .categories(new ArrayList<>())
                .checkpointBuffers(new ArrayList<>())
                .cleanBuffers(new ArrayList<>())
                .backendBuffers(new ArrayList<>()).build();
    }

    private DiskIoDto.BackendFsyncChart24h buildEmptyBackendFsyncChart24h() {
        return DiskIoDto.BackendFsyncChart24h.builder()
                .categories(new ArrayList<>())
                .fsyncRate(new ArrayList<>())
                .warningThreshold(100.0).build();
    }

    private DiskIoDto.PhysicalVsCacheChart24h buildEmptyPhysicalVsCacheChart24h() {
        return DiskIoDto.PhysicalVsCacheChart24h.builder()
                .categories(new ArrayList<>())
                .physicalReads(new ArrayList<>())
                .cacheHits(new ArrayList<>()).build();
    }

    private DiskIoDto.ThroughputChart24h buildEmptyThroughputChart24h() {
        return DiskIoDto.ThroughputChart24h.builder()
                .categories(new ArrayList<>())
                .readMBps(new ArrayList<>())
                .writeMBps(new ArrayList<>()).build();
    }
}