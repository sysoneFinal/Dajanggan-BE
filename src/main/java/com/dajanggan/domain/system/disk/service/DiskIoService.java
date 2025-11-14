package com.dajanggan.domain.system.disk.service;


import com.dajanggan.domain.system.disk.dto.DiskIoDto;
import com.dajanggan.domain.system.disk.repository.DiskIoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiskIoService {

    private final DiskIoMapper diskIoMapper;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Disk I/O 대시보드 데이터 조회
     */
    public DiskIoDto.DashboardResponse getDiskIoDashboard(Long instanceId) {
        if (instanceId == null) {
            log.error("instanceId가 필수입니다");
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }

        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(24);

        // 각 섹션 데이터 조회
        DiskIoDto.DiskUsage diskUsage = getDiskUsage(instanceId);
        DiskIoDto.ProcessIO processIO = getProcessIO(instanceId, startTime, endTime);
        DiskIoDto.QueueDepth queueDepth = getQueueDepth(instanceId, startTime, endTime);
        DiskIoDto.IoLatency ioLatency = getIoLatency(instanceId, startTime, endTime);
        DiskIoDto.Throughput throughput = getThroughput(instanceId, startTime, endTime);
        DiskIoDto.Evictions evictions = getEvictions(instanceId, startTime, endTime);
        DiskIoDto.WalBytes walBytes = getWalBytes(instanceId, startTime, endTime);
        DiskIoDto.RecentStats recentStats = getRecentStats(instanceId);

        return DiskIoDto.DashboardResponse.builder()
                .diskUsage(diskUsage)
                .processIO(processIO)
                .queueDepth(queueDepth)
                .ioLatency(ioLatency)
                .throughput(throughput)
                .evictions(evictions)
                .walBytes(walBytes)
                .recentStats(recentStats)
                .build();
    }

    /**
     * 디스크 사용률 조회
     */
    private DiskIoDto.DiskUsage getDiskUsage(Long instanceId) {
        Map<String, Object> result = diskIoMapper.selectDiskUsage(instanceId);

        if (result == null || result.isEmpty()) {
            return DiskIoDto.DiskUsage.builder()
                    .value(0.0)
                    .iopsRead(0L)
                    .iopsWrite(0L)
                    .build();
        }

        return DiskIoDto.DiskUsage.builder()
                .value(getDoubleValue(result, "disk_usage"))
                .iopsRead(getLongValue(result, "iops_read"))
                .iopsWrite(getLongValue(result, "iops_write"))
                .build();
    }

    /**
     * 프로세스별 I/O 조회
     */
    private DiskIoDto.ProcessIO getProcessIO(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> results = diskIoMapper.selectProcessIOTimeSeries(instanceId, startTime, endTime);

        if (results == null || results.isEmpty()) {
            return DiskIoDto.ProcessIO.builder()
                    .categories(new ArrayList<>())
                    .series(new ArrayList<>())
                    .build();
        }

        // 시간 카테고리 추출
        List<String> categories = results.stream()
                .map(r -> formatTime(r.get("time_bucket")))
                .distinct()
                .collect(Collectors.toList());

        // 프로세스별로 그룹화
        Map<String, List<Map<String, Object>>> groupedByProcess = results.stream()
                .collect(Collectors.groupingBy(r -> (String) r.get("backend_type")));

        // 각 프로세스별 시리즈 생성
        List<DiskIoDto.Series> seriesList = groupedByProcess.entrySet().stream()
                .map(entry -> {
                    String processName = entry.getKey();
                    List<Long> data = entry.getValue().stream()
                            .map(r -> getLongValue(r, "total_io"))
                            .collect(Collectors.toList());

                    return DiskIoDto.Series.builder()
                            .name(processName)
                            .data(data)
                            .build();
                })
                .collect(Collectors.toList());

        return DiskIoDto.ProcessIO.builder()
                .categories(categories)
                .series(seriesList)
                .build();
    }

    /**
     * Queue Depth 조회
     */
    private DiskIoDto.QueueDepth getQueueDepth(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> results = diskIoMapper.selectQueueDepthTimeSeries(instanceId, startTime, endTime);

        if (results == null || results.isEmpty()) {
            return DiskIoDto.QueueDepth.builder()
                    .categories(new ArrayList<>())
                    .queueLength(new ArrayList<>())
                    .average(0.0)
                    .build();
        }

        List<String> categories = results.stream()
                .map(r -> formatTime(r.get("time_bucket")))
                .collect(Collectors.toList());

        List<Double> queueLength = results.stream()
                .map(r -> getDoubleValue(r, "avg_queue_depth"))
                .collect(Collectors.toList());

        Double average = queueLength.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        return DiskIoDto.QueueDepth.builder()
                .categories(categories)
                .queueLength(queueLength)
                .average(average)
                .build();
    }

    /**
     * I/O Latency 조회
     */
    private DiskIoDto.IoLatency getIoLatency(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> results = diskIoMapper.selectIoLatencyTimeSeries(instanceId, startTime, endTime);

        if (results == null || results.isEmpty()) {
            return DiskIoDto.IoLatency.builder()
                    .categories(new ArrayList<>())
                    .readLatency(new ArrayList<>())
                    .writeLatency(new ArrayList<>())
                    .avgRead(0.0)
                    .avgWrite(0.0)
                    .build();
        }

        List<String> categories = results.stream()
                .map(r -> formatTime(r.get("time_bucket")))
                .collect(Collectors.toList());

        List<Double> readLatency = results.stream()
                .map(r -> getDoubleValue(r, "avg_read_latency"))
                .collect(Collectors.toList());

        List<Double> writeLatency = results.stream()
                .map(r -> getDoubleValue(r, "avg_write_latency"))
                .collect(Collectors.toList());

        Double avgRead = readLatency.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        Double avgWrite = writeLatency.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        return DiskIoDto.IoLatency.builder()
                .categories(categories)
                .readLatency(readLatency)
                .writeLatency(writeLatency)
                .avgRead(avgRead)
                .avgWrite(avgWrite)
                .build();
    }

    /**
     * Throughput 조회
     */
    private DiskIoDto.Throughput getThroughput(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> results = diskIoMapper.selectThroughputTimeSeries(instanceId, startTime, endTime);

        if (results == null || results.isEmpty()) {
            return DiskIoDto.Throughput.builder()
                    .categories(new ArrayList<>())
                    .iops(new ArrayList<>())
                    .throughputMB(new ArrayList<>())
                    .build();
        }

        List<String> categories = results.stream()
                .map(r -> formatTime(r.get("time_bucket")))
                .collect(Collectors.toList());

        List<Long> iops = results.stream()
                .map(r -> getLongValue(r, "total_iops"))
                .collect(Collectors.toList());

        List<Double> throughputMB = results.stream()
                .map(r -> getDoubleValue(r, "throughput_mb"))
                .collect(Collectors.toList());

        return DiskIoDto.Throughput.builder()
                .categories(categories)
                .iops(iops)
                .throughputMB(throughputMB)
                .build();
    }

    /**
     * Evictions 조회
     */
    private DiskIoDto.Evictions getEvictions(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> results = diskIoMapper.selectEvictionsTimeSeries(instanceId, startTime, endTime);

        if (results == null || results.isEmpty()) {
            return DiskIoDto.Evictions.builder()
                    .categories(new ArrayList<>())
                    .evictionRate(new ArrayList<>())
                    .average(0.0)
                    .build();
        }

        List<String> categories = results.stream()
                .map(r -> formatTime(r.get("time_bucket")))
                .collect(Collectors.toList());

        List<Long> evictionRate = results.stream()
                .map(r -> getLongValue(r, "eviction_rate"))
                .collect(Collectors.toList());

        Double average = evictionRate.stream()
                .mapToDouble(Long::doubleValue)
                .average()
                .orElse(0.0);

        return DiskIoDto.Evictions.builder()
                .categories(categories)
                .evictionRate(evictionRate)
                .average(average)
                .build();
    }

    /**
     * WAL Bytes 조회
     */
    private DiskIoDto.WalBytes getWalBytes(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> results = diskIoMapper.selectWalBytesTimeSeries(instanceId, startTime, endTime);

        if (results == null || results.isEmpty()) {
            return DiskIoDto.WalBytes.builder()
                    .categories(new ArrayList<>())
                    .walBytes(new ArrayList<>())
                    .average(0.0)
                    .build();
        }

        List<String> categories = results.stream()
                .map(r -> formatTime(r.get("time_bucket")))
                .collect(Collectors.toList());

        List<Long> walBytes = results.stream()
                .map(r -> getLongValue(r, "wal_bytes"))
                .collect(Collectors.toList());

        Double average = walBytes.stream()
                .mapToDouble(Long::doubleValue)
                .average()
                .orElse(0.0);

        return DiskIoDto.WalBytes.builder()
                .categories(categories)
                .walBytes(walBytes)
                .average(average)
                .build();
    }

    /**
     * 최근 통계 조회
     */
    private DiskIoDto.RecentStats getRecentStats(Long instanceId) {
        Map<String, Object> result = diskIoMapper.selectRecentStats(instanceId);

        if (result == null || result.isEmpty()) {
            return DiskIoDto.RecentStats.builder()
                    .diskQueueLength(0.0)
                    .iopsSaturation(0.0)
                    .avgLatency(0.0)
                    .walBottleneck(0.0)
                    .bufferEvictionRate(0.0)
                    .build();
        }

        return DiskIoDto.RecentStats.builder()
                .diskQueueLength(getDoubleValue(result, "disk_queue_length"))
                .iopsSaturation(getDoubleValue(result, "iops_saturation"))
                .avgLatency(getDoubleValue(result, "avg_latency"))
                .walBottleneck(getDoubleValue(result, "wal_bottleneck"))
                .bufferEvictionRate(getDoubleValue(result, "buffer_eviction_rate"))
                .build();
    }

    /**
     * Disk I/O 리스트 조회
     */
    public DiskIoDto.ListResponse getDiskIoList(Long instanceId, String timeRange, List<String> statusList) {
        if (instanceId == null) {
            log.error("instanceId가 필수입니다");
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }

        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = calculateStartTime(endTime, timeRange);

        List<Map<String, Object>> results = diskIoMapper.selectDiskIoList(instanceId, startTime, endTime, statusList);

        if (results == null || results.isEmpty()) {
            return DiskIoDto.ListResponse.builder()
                    .data(new ArrayList<>())
                    .total(0L)
                    .build();
        }

        List<DiskIoDto.ListItem> items = results.stream()
                .map(this::mapToListItem)
                .collect(Collectors.toList());

        return DiskIoDto.ListResponse.builder()
                .data(items)
                .total((long) items.size())
                .build();
    }

    /**
     * Map을 ListItem으로 변환
     */
    private DiskIoDto.ListItem mapToListItem(Map<String, Object> result) {
        return DiskIoDto.ListItem.builder()
                .id(String.valueOf(result.get("id")))
                .processType((String) result.get("backend_type"))
                .totalIO(getLongValue(result, "total_io"))
                .readRate(getLongValue(result, "read_rate"))
                .writeRate(getLongValue(result, "write_rate"))
                .readMBs(getDoubleValue(result, "read_mbs"))
                .writeMBs(getDoubleValue(result, "write_mbs"))
                .throughputMBs(getDoubleValue(result, "throughput_mbs"))
                .fsyncRate(getLongValue(result, "fsync_rate"))
                .evictionRate(getLongValue(result, "eviction_rate"))
                .extendRate(getLongValue(result, "extend_rate"))
                .hitRatio(getDoubleValue(result, "hit_ratio"))
                .avgQueueDepth(getDoubleValue(result, "avg_queue_depth"))
                .avgLatency(getDoubleValue(result, "avg_latency"))
                .readPercent(getDoubleValue(result, "read_percent"))
                .writePercent(getDoubleValue(result, "write_percent"))
                .status((String) result.get("status"))
                .build();
    }

    /**
     * 시간 범위에 따른 시작 시간 계산
     */
    private LocalDateTime calculateStartTime(LocalDateTime endTime, String timeRange) {
        return switch (timeRange) {
            case "1h" -> endTime.minusHours(1);
            case "6h" -> endTime.minusHours(6);
            case "24h" -> endTime.minusHours(24);
            case "7d" -> endTime.minusDays(7);
            default -> endTime.minusDays(7);
        };
    }

    /**
     * 시간 포맷팅
     */
    private String formatTime(Object timeObj) {
        if (timeObj instanceof LocalDateTime) {
            return ((LocalDateTime) timeObj).format(TIME_FORMATTER);
        }
        return timeObj != null ? timeObj.toString() : "";
    }

    /**
     * Double 값 추출
     */
    private Double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0.0;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }

    /**
     * Long 값 추출
     */
    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0L;
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }
}