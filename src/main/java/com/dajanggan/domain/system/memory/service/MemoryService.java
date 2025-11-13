package com.dajanggan.domain.system.memory.service;

import com.dajanggan.domain.system.memory.dto.MemoryDto;
import com.dajanggan.domain.system.memory.repository.MemoryMapper;
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
public class MemoryService {

    private final MemoryMapper memoryMapper;

    /**
     * Memory 대시보드 데이터 조회
     * @param instanceId PostgreSQL 인스턴스 ID
     * @return Memory 대시보드 데이터
     */
    public MemoryDto.DashboardResponse getMemoryDashboard(Long instanceId) {
        log.debug("Memory 대시보드 데이터 조회 시작 - instanceId: {}", instanceId);

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
            // 1. Memory 사용률 조회
            MemoryDto.MemoryUtilization memoryUtilization = getMemoryUtilization(instanceId);

            // 2. Buffer Hit 비율 조회
            MemoryDto.BufferHitRatio bufferHitRatio = getBufferHitRatio(instanceId);

            // 3. Shared Buffer 사용량 조회
            MemoryDto.SharedBufferUsage sharedBufferUsage = getSharedBufferUsage(instanceId);

            // 4. Eviction Rate 시계열 데이터 조회
            MemoryDto.EvictionRate evictionRate = getEvictionRate(instanceId, startTime, endTime);

            // 5. Fsync Rate 시계열 데이터 조회
            MemoryDto.FsyncRate fsyncRate = getFsyncRate(instanceId, startTime, endTime);

            // 6. Dirty Buffer 추세 조회
            MemoryDto.DirtyBufferTrend dirtyBufferTrend = getDirtyBufferTrend(instanceId, startTime, endTime);

            // 7. Eviction vs Flush 비교 데이터 조회
            MemoryDto.EvictionFlushRatio evictionFlushRatio = getEvictionFlushRatio(instanceId, startTime, endTime);

            // 8. 상위 버퍼 사용 객체 조회
            MemoryDto.TopBufferObjects topBufferObjects = getTopBufferObjects(instanceId, 10);

            // 9. 요약 통계 조회
            MemoryDto.SummaryStats summaryStats = getSummaryStats(instanceId);

            return MemoryDto.DashboardResponse.builder()
                    .memoryUtilization(memoryUtilization)
                    .bufferHitRatio(bufferHitRatio)
                    .sharedBufferUsage(sharedBufferUsage)
                    .evictionRate(evictionRate)
                    .fsyncRate(fsyncRate)
                    .dirtyBufferTrend(dirtyBufferTrend)
                    .evictionFlushRatio(evictionFlushRatio)
                    .topBufferObjects(topBufferObjects)
                    .summaryStats(summaryStats)
                    .build();

        } catch (Exception e) {
            log.error("Memory 대시보드 데이터 조회 중 오류 발생", e);
            throw new RuntimeException("Memory 데이터 조회 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Memory 사용률 조회
     */
    private MemoryDto.MemoryUtilization getMemoryUtilization(Long instanceId) {
        Map<String, Object> data = memoryMapper.selectMemoryUtilization(instanceId);

        if (data == null || data.isEmpty()) {
            log.warn("Memory 사용률 데이터 없음 - instanceId: {}", instanceId);
            return MemoryDto.MemoryUtilization.builder()
                    .value(0.0)
                    .usedBuffers(0L)
                    .totalBuffers(0L)
                    .build();
        }

        return MemoryDto.MemoryUtilization.builder()
                .value(getDoubleValue(data, "value"))
                .usedBuffers(getLongValue(data, "usedbuffers"))
                .totalBuffers(getLongValue(data, "totalbuffers"))
                .build();
    }

    /**
     * Buffer Hit 비율 조회
     */
    private MemoryDto.BufferHitRatio getBufferHitRatio(Long instanceId) {
        Map<String, Object> data = memoryMapper.selectBufferHitRatio(instanceId);

        if (data == null || data.isEmpty()) {
            log.warn("Buffer Hit 비율 데이터 없음 - instanceId: {}", instanceId);
            return MemoryDto.BufferHitRatio.builder()
                    .value(0.0)
                    .hitCount(0L)
                    .totalCount(0L)
                    .build();
        }

        return MemoryDto.BufferHitRatio.builder()
                .value(getDoubleValue(data, "value"))
                .hitCount(getLongValue(data, "hitcount"))
                .totalCount(getLongValue(data, "totalcount"))
                .build();
    }

    /**
     * Shared Buffer 사용량 조회
     */
    private MemoryDto.SharedBufferUsage getSharedBufferUsage(Long instanceId) {
        Map<String, Object> data = memoryMapper.selectSharedBufferUsage(instanceId);

        if (data == null || data.isEmpty()) {
            log.warn("Shared Buffer 사용량 데이터 없음 - instanceId: {}", instanceId);
            return MemoryDto.SharedBufferUsage.builder()
                    .value(0.0)
                    .activeBuffers(0L)
                    .totalBuffers(0L)
                    .build();
        }

        return MemoryDto.SharedBufferUsage.builder()
                .value(getDoubleValue(data, "value"))
                .activeBuffers(getLongValue(data, "activebuffers"))
                .totalBuffers(getLongValue(data, "totalbuffers"))
                .build();
    }

    /**
     * Eviction Rate 시계열 데이터 조회
     */
    private MemoryDto.EvictionRate getEvictionRate(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> dataList = memoryMapper.selectEvictionRateTimeSeries(instanceId, startTime, endTime);

        if (dataList == null || dataList.isEmpty()) {
            log.warn("Eviction Rate 데이터 없음 - instanceId: {}", instanceId);
            return MemoryDto.EvictionRate.builder()
                    .categories(new ArrayList<>())
                    .data(new ArrayList<>())
                    .average(0.0)
                    .max(0L)
                    .min(0L)
                    .build();
        }

        List<String> categories = dataList.stream()
                .map(item -> (String) item.get("time_label"))
                .collect(Collectors.toList());

        List<Long> data = dataList.stream()
                .map(item -> getLongValue(item, "eviction_count"))
                .collect(Collectors.toList());

        double average = data.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long max = data.stream().mapToLong(Long::longValue).max().orElse(0L);
        long min = data.stream().mapToLong(Long::longValue).min().orElse(0L);

        return MemoryDto.EvictionRate.builder()
                .categories(categories)
                .data(data)
                .average(average)
                .max(max)
                .min(min)
                .build();
    }

    /**
     * Fsync Rate 시계열 데이터 조회
     */
    private MemoryDto.FsyncRate getFsyncRate(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> dataList = memoryMapper.selectFsyncRateTimeSeries(instanceId, startTime, endTime);

        if (dataList == null || dataList.isEmpty()) {
            log.warn("Fsync Rate 데이터 없음 - instanceId: {}", instanceId);
            return MemoryDto.FsyncRate.builder()
                    .categories(new ArrayList<>())
                    .data(new ArrayList<>())
                    .average(0.0)
                    .max(0L)
                    .backendFsync(0L)
                    .build();
        }

        List<String> categories = dataList.stream()
                .map(item -> (String) item.get("time_label"))
                .collect(Collectors.toList());

        List<Long> data = dataList.stream()
                .map(item -> getLongValue(item, "fsync_count"))
                .collect(Collectors.toList());

        double average = data.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long max = data.stream().mapToLong(Long::longValue).max().orElse(0L);
        long backendFsync = data.stream().mapToLong(Long::longValue).sum();

        return MemoryDto.FsyncRate.builder()
                .categories(categories)
                .data(data)
                .average(average)
                .max(max)
                .backendFsync(backendFsync)
                .build();
    }

    /**
     * Dirty Buffer 추세 조회
     */
    private MemoryDto.DirtyBufferTrend getDirtyBufferTrend(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> dataList = memoryMapper.selectDirtyBufferTrendTimeSeries(instanceId, startTime, endTime);

        if (dataList == null || dataList.isEmpty()) {
            log.warn("Dirty Buffer 추세 데이터 없음 - instanceId: {}", instanceId);
            return MemoryDto.DirtyBufferTrend.builder()
                    .categories(new ArrayList<>())
                    .data(new ArrayList<>())
                    .average(0.0)
                    .max(0L)
                    .min(0L)
                    .build();
        }

        List<String> categories = dataList.stream()
                .map(item -> (String) item.get("time_label"))
                .collect(Collectors.toList());

        List<Long> data = dataList.stream()
                .map(item -> ((Number) item.get("dirty_ratio")).longValue())
                .collect(Collectors.toList());

        double average = data.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long max = data.stream().mapToLong(Long::longValue).max().orElse(0L);
        long min = data.stream().mapToLong(Long::longValue).min().orElse(0L);

        return MemoryDto.DirtyBufferTrend.builder()
                .categories(categories)
                .data(data)
                .average(average)
                .max(max)
                .min(min)
                .build();
    }

    /**
     * Eviction vs Flush 비교 데이터 조회
     */
    private MemoryDto.EvictionFlushRatio getEvictionFlushRatio(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> dataList = memoryMapper.selectEvictionFlushRatioTimeSeries(instanceId, startTime, endTime);

        if (dataList == null || dataList.isEmpty()) {
            log.warn("Eviction vs Flush 데이터 없음 - instanceId: {}", instanceId);
            return MemoryDto.EvictionFlushRatio.builder()
                    .categories(new ArrayList<>())
                    .evictions(new ArrayList<>())
                    .fsyncs(new ArrayList<>())
                    .build();
        }

        List<String> categories = dataList.stream()
                .map(item -> (String) item.get("time_label"))
                .collect(Collectors.toList());

        List<Long> evictions = dataList.stream()
                .map(item -> getLongValue(item, "evictions"))
                .collect(Collectors.toList());

        List<Long> fsyncs = dataList.stream()
                .map(item -> getLongValue(item, "fsyncs"))
                .collect(Collectors.toList());

        return MemoryDto.EvictionFlushRatio.builder()
                .categories(categories)
                .evictions(evictions)
                .fsyncs(fsyncs)
                .build();
    }

    /**
     * 상위 버퍼 사용 객체 조회
     */
    private MemoryDto.TopBufferObjects getTopBufferObjects(Long instanceId, int limit) {
        List<Map<String, Object>> dataList = memoryMapper.selectTopBufferObjects(instanceId, limit);

        if (dataList == null || dataList.isEmpty()) {
            log.warn("상위 버퍼 사용 객체 데이터 없음 - instanceId: {}", instanceId);
            return MemoryDto.TopBufferObjects.builder()
                    .labels(new ArrayList<>())
                    .data(new ArrayList<>())
                    .types(new ArrayList<>())
                    .build();
        }

        List<String> labels = dataList.stream()
                .map(item -> (String) item.get("object_name"))
                .collect(Collectors.toList());

        List<Double> data = dataList.stream()
                .map(item -> getDoubleValue(item, "buffer_count"))  // Long → Double
                .collect(Collectors.toList());

        List<String> types = dataList.stream()
                .map(item -> (String) item.get("object_type"))
                .collect(Collectors.toList());

        return MemoryDto.TopBufferObjects.builder()
                .labels(labels)
                .data(data)
                .types(types)
                .build();
    }

    /**
     * 요약 통계 조회
     */
    private MemoryDto.SummaryStats getSummaryStats(Long instanceId) {
        Map<String, Object> data = memoryMapper.selectSummaryStats(instanceId);

        if (data == null || data.isEmpty()) {
            log.warn("요약 통계 데이터 없음 - instanceId: {}", instanceId);
            return MemoryDto.SummaryStats.builder()
                    .dirtyBufferRatio(0.0)
                    .backendWaitTime(0.0)
                    .workMemUsage(0.0)
                    .tempFileUsage(0.0)
                    .checkpointInterval(0.0)
                    .build();
        }

        return MemoryDto.SummaryStats.builder()
                .dirtyBufferRatio(getDoubleValue(data, "dirtybufferratio"))
                .backendWaitTime(getDoubleValue(data, "backendwaittime"))
                .workMemUsage(getDoubleValue(data, "workmemusage"))
                .tempFileUsage(getDoubleValue(data, "tempfileusage"))
                .checkpointInterval(getDoubleValue(data, "checkpointinterval"))
                .build();
    }

    /**
     * Memory 리스트 데이터 조회
     * @param instanceId PostgreSQL 인스턴스 ID
     * @param typeList 타입 필터 리스트
     * @param statusList 상태 필터 리스트
     * @return Memory 리스트 데이터
     */
    public MemoryDto.ListResponse getMemoryList(Long instanceId, List<String> typeList, List<String> statusList) {
        log.debug("Memory 리스트 조회 시작 - instanceId: {}, typeList: {}, statusList: {}",
                instanceId, typeList, statusList);

        // instanceId가 null이면 예외 발생
        if (instanceId == null) {
            log.error("instanceId가 필수입니다");
            throw new IllegalArgumentException("instanceId는 필수 파라미터입니다");
        }

        try {
            List<Map<String, Object>> dataList = memoryMapper.selectMemoryList(
                    instanceId, typeList, statusList);

            if (dataList == null || dataList.isEmpty()) {
                log.warn("Memory 리스트 데이터 없음 - instanceId: {}", instanceId);
                return MemoryDto.ListResponse.builder()
                        .data(new ArrayList<>())
                        .total(0L)
                        .build();
            }

            List<MemoryDto.ListItem> listItems = dataList.stream()
                    .map(this::mapToListItem)
                    .collect(Collectors.toList());

            return MemoryDto.ListResponse.builder()
                    .data(listItems)
                    .total((long) listItems.size())
                    .build();

        } catch (Exception e) {
            log.error("Memory 리스트 조회 중 오류 발생", e);
            throw new RuntimeException("Memory 리스트 조회 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Map 데이터를 ListItem으로 변환
     */
    private MemoryDto.ListItem mapToListItem(Map<String, Object> data) {
        return MemoryDto.ListItem.builder()
                .id(String.valueOf(data.get("id")))
                .objectName((String) data.get("objectname"))
                .type((String) data.get("type"))
                .sizeMb(getDoubleValue(data, "sizemb"))
                .bufferCount(getLongValue(data, "buffercount"))
                .usagePercent(getDoubleValue(data, "usagepercent"))
                .dirtyCount(getLongValue(data, "dirtycount"))
                .dirtyPercent(getDoubleValue(data, "dirtypercent"))
                .pinnedBuffers(getLongValue(data, "pinnedbuffers"))
                .hitPercent(getDoubleValue(data, "hitpercent"))
                .accessCount(getLongValue(data, "accesscount"))
                .evictionCount(getLongValue(data, "evictioncount"))
                .avgAccessTime(getDoubleValue(data, "avgaccesstime"))
                .status((String) data.get("status"))
                .build();
    }

    /**
     * Map에서 Double 값 추출 (null-safe)
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
     * Map에서 Long 값 추출 (null-safe)
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