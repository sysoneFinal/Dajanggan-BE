package com.dajanggan.domain.engine.checkpoint.service;

import com.dajanggan.domain.engine.checkpoint.dto.CheckpointDto;
import com.dajanggan.domain.engine.checkpoint.repository.CheckpointMapper;
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
public class CheckpointService {

    private final CheckpointMapper checkpointMapper;
    
    // 기본 인스턴스 ID (실제 환경에서는 설정 파일이나 DB에서 조회)
    private static final Long DEFAULT_INSTANCE_ID = 1L;

    /**
     * Checkpoint 대시보드 데이터 조회
     * @param instanceId PostgreSQL 인스턴스 ID
     * @return Checkpoint 대시보드 데이터
     */
    public CheckpointDto.DashboardResponse getCheckpointDashboard(Long instanceId) {
        log.debug("Checkpoint 대시보드 데이터 조회 시작 - instanceId: {}", instanceId);

        // instanceId가 null이면 기본값 사용
        Long targetInstanceId = (instanceId != null) ? instanceId : DEFAULT_INSTANCE_ID;
        
        // 조회 시간 범위 설정 (최근 24시간)
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(24);
        
        log.info("데이터 조회 범위: {} ~ {}", startTime, endTime);

        try {
            // 1. 요청형 체크포인트 비율 조회
            CheckpointDto.RequestRatio requestRatio = getRequestRatio(targetInstanceId);
            
            // 2. 평균 쓰기 시간 시계열 데이터 조회
            CheckpointDto.AvgWriteTime avgWriteTime = getAvgWriteTime(targetInstanceId, startTime, endTime);
            
            // 3. 체크포인트 발생 횟수 데이터 조회
            CheckpointDto.Occurrence occurrence = getOccurrence(targetInstanceId, startTime, endTime);
            
            // 4. WAL 생성량 데이터 조회
            CheckpointDto.WalGeneration walGeneration = getWalGeneration(targetInstanceId, startTime, endTime);
            
            // 5. 처리 시간 데이터 조회
            CheckpointDto.ProcessTime processTime = getProcessTime(targetInstanceId, startTime, endTime);
            
            // 6. 버퍼 처리량 데이터 조회
            CheckpointDto.Buffer buffer = getBuffer(targetInstanceId, startTime.plusHours(23), endTime);
            
            // 7. 체크포인트 간격 데이터 조회
            CheckpointDto.CheckpointInterval checkpointInterval = getCheckpointInterval(targetInstanceId, startTime, endTime);
            
            // 8. 최근 통계 조회
            CheckpointDto.RecentStats recentStats = getRecentStats(targetInstanceId);

            return CheckpointDto.DashboardResponse.builder()
                    .requestRatio(requestRatio)
                    .avgWriteTime(avgWriteTime)
                    .occurrence(occurrence)
                    .walGeneration(walGeneration)
                    .processTime(processTime)
                    .buffer(buffer)
                    .checkpointInterval(checkpointInterval)
                    .recentStats(recentStats)
                    .build();
                    
        } catch (Exception e) {
            log.error("Checkpoint 대시보드 데이터 조회 중 오류 발생", e);
            throw new RuntimeException("Checkpoint 데이터 조회 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 요청형 체크포인트 비율 조회
     */
    private CheckpointDto.RequestRatio getRequestRatio(Long instanceId) {
        Map<String, Object> data = checkpointMapper.selectRequestRatio(instanceId);
        
        if (data == null || data.isEmpty()) {
            log.warn("요청형 체크포인트 비율 데이터 없음 - instanceId: {}", instanceId);
            return CheckpointDto.RequestRatio.builder()
                    .value(0.0)
                    .requestedCount(0L)
                    .timedCount(0L)
                    .build();
        }
        
        return CheckpointDto.RequestRatio.builder()
                .value(getDoubleValue(data, "value"))
                .requestedCount(getLongValue(data, "requestedcount"))
                .timedCount(getLongValue(data, "timedcount"))
                .build();
    }

    /**
     * 평균 쓰기 시간 시계열 데이터 조회
     */
    private CheckpointDto.AvgWriteTime getAvgWriteTime(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> dataList = checkpointMapper.selectAvgWriteTimeTimeSeries(instanceId, startTime, endTime);
        
        if (dataList == null || dataList.isEmpty()) {
            log.warn("평균 쓰기 시간 데이터 없음 - instanceId: {}", instanceId);
            return createEmptyAvgWriteTime();
        }
        
        List<String> categories = dataList.stream()
                .map(data -> getString(data, "time_label"))
                .collect(Collectors.toList());
        
        List<Double> values = dataList.stream()
                .map(data -> getDoubleValue(data, "avg_write_time"))
                .collect(Collectors.toList());
        
        double average = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        
        return CheckpointDto.AvgWriteTime.builder()
                .categories(categories)
                .data(values)
                .average(average)
                .max(max)
                .min(min)
                .build();
    }

    /**
     * 체크포인트 발생 횟수 시계열 데이터 조회
     */
    private CheckpointDto.Occurrence getOccurrence(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> dataList = checkpointMapper.selectOccurrenceTimeSeries(instanceId, startTime, endTime);
        
        if (dataList == null || dataList.isEmpty()) {
            log.warn("체크포인트 발생 횟수 데이터 없음 - instanceId: {}", instanceId);
            return createEmptyOccurrence();
        }
        
        List<String> categories = dataList.stream()
                .map(data -> getString(data, "time_label"))
                .collect(Collectors.toList());
        
        List<Long> requested = dataList.stream()
                .map(data -> getLongValue(data, "requested"))
                .collect(Collectors.toList());
        
        List<Long> timed = dataList.stream()
                .map(data -> getLongValue(data, "timed"))
                .collect(Collectors.toList());
        
        long requestedTotal = requested.stream().mapToLong(Long::longValue).sum();
        long timedTotal = timed.stream().mapToLong(Long::longValue).sum();
        
        double ratio = 0.0;
        if (requestedTotal + timedTotal > 0) {
            ratio = (double) requestedTotal / (requestedTotal + timedTotal) * 100.0;
        }
        
        return CheckpointDto.Occurrence.builder()
                .categories(categories)
                .requested(requested)
                .timed(timed)
                .requestedTotal(requestedTotal)
                .timedTotal(timedTotal)
                .ratio(ratio)
                .build();
    }

    /**
     * WAL 생성량 시계열 데이터 조회
     */
    private CheckpointDto.WalGeneration getWalGeneration(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> dataList = checkpointMapper.selectWalGenerationTimeSeries(instanceId, startTime, endTime);
        
        if (dataList == null || dataList.isEmpty()) {
            log.warn("WAL 생성량 데이터 없음 - instanceId: {}", instanceId);
            return createEmptyWalGeneration();
        }
        
        List<String> categories = dataList.stream()
                .map(data -> getString(data, "time_label"))
                .collect(Collectors.toList());
        
        List<Long> values = dataList.stream()
                .map(data -> getLongValue(data, "wal_bytes"))
                .collect(Collectors.toList());
        
        long total = values.stream().mapToLong(Long::longValue).sum();
        double average = values.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long max = values.stream().mapToLong(Long::longValue).max().orElse(0L);
        
        return CheckpointDto.WalGeneration.builder()
                .categories(categories)
                .data(values)
                .total(total)
                .average(average)
                .max(max)
                .build();
    }

    /**
     * 처리 시간 시계열 데이터 조회
     */
    private CheckpointDto.ProcessTime getProcessTime(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> dataList = checkpointMapper.selectProcessTimeTimeSeries(instanceId, startTime, endTime);
        
        if (dataList == null || dataList.isEmpty()) {
            log.warn("처리 시간 데이터 없음 - instanceId: {}", instanceId);
            return createEmptyProcessTime();
        }
        
        List<String> categories = dataList.stream()
                .map(data -> getString(data, "time_label"))
                .collect(Collectors.toList());
        
        List<Double> syncTime = dataList.stream()
                .map(data -> getDoubleValue(data, "sync_time"))
                .collect(Collectors.toList());
        
        List<Double> writeTime = dataList.stream()
                .map(data -> getDoubleValue(data, "write_time"))
                .collect(Collectors.toList());
        
        double avgSync = syncTime.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgWrite = writeTime.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgTotal = avgSync + avgWrite;
        
        return CheckpointDto.ProcessTime.builder()
                .categories(categories)
                .syncTime(syncTime)
                .writeTime(writeTime)
                .avgSync(avgSync)
                .avgWrite(avgWrite)
                .avgTotal(avgTotal)
                .build();
    }

    /**
     * 버퍼 처리량 시계열 데이터 조회
     */
    private CheckpointDto.Buffer getBuffer(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> dataList = checkpointMapper.selectBufferTimeSeries(instanceId, startTime, endTime);
        
        if (dataList == null || dataList.isEmpty()) {
            log.warn("버퍼 처리량 데이터 없음 - instanceId: {}", instanceId);
            return createEmptyBuffer();
        }
        
        List<String> categories = dataList.stream()
                .map(data -> getString(data, "time_label"))
                .collect(Collectors.toList());
        
        List<Double> values = dataList.stream()
                .map(data -> getDoubleValue(data, "buffers_per_sec"))
                .collect(Collectors.toList());
        
        double average = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        
        return CheckpointDto.Buffer.builder()
                .categories(categories)
                .data(values)
                .average(average)
                .max(max)
                .min(min)
                .build();
    }

    /**
     * 체크포인트 간격 시계열 데이터 조회
     */
    private CheckpointDto.CheckpointInterval getCheckpointInterval(Long instanceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> dataList = checkpointMapper.selectCheckpointIntervalTimeSeries(instanceId, startTime, endTime);
        
        if (dataList == null || dataList.isEmpty()) {
            log.warn("체크포인트 간격 데이터 없음 - instanceId: {}", instanceId);
            return createEmptyCheckpointInterval();
        }
        
        List<String> categories = dataList.stream()
                .map(data -> getString(data, "time_label"))
                .collect(Collectors.toList());
        
        List<Double> values = dataList.stream()
                .map(data -> getDoubleValue(data, "interval_minutes"))
                .collect(Collectors.toList());
        
        double average = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        
        return CheckpointDto.CheckpointInterval.builder()
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
    private CheckpointDto.RecentStats getRecentStats(Long instanceId) {
        Map<String, Object> data = checkpointMapper.selectRecentStats(instanceId);
        
        if (data == null || data.isEmpty()) {
            log.warn("최근 통계 데이터 없음 - instanceId: {}", instanceId);
            return createEmptyRecentStats();
        }
        
        return CheckpointDto.RecentStats.builder()
                .buffersWritten(getLongValue(data, "bufferswritten"))
                .avgTotalProcessTime(getDoubleValue(data, "avgtotalprocesstime"))
                .checkpointDistance(getDoubleValue(data, "checkpointdistance"))
                .checkpointInterval(getDoubleValue(data, "checkpointinterval"))
                .avgWalGenerationSpeed(getDoubleValue(data, "avgwalgenerationspeed"))
                .build();
    }

    /**
     * Checkpoint 리스트 데이터 조회
     * @param instanceId PostgreSQL 인스턴스 ID
     * @param timeRange 시간 범위 (1h, 6h, 24h, 7d)
     * @param statusList 상태 필터 리스트
     * @return Checkpoint 리스트 데이터
     */
    public CheckpointDto.ListResponse getCheckpointList(Long instanceId, String timeRange, List<String> statusList) {
        log.debug("Checkpoint 리스트 데이터 조회 시작 - instanceId: {}, timeRange: {}, statusList: {}", 
                instanceId, timeRange, statusList);

        // instanceId가 null이면 기본값 사용
        Long targetInstanceId = (instanceId != null) ? instanceId : DEFAULT_INSTANCE_ID;
        
        // 시간 범위 계산
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = calculateStartTime(endTime, timeRange);
        
        log.info("리스트 데이터 조회 범위: {} ~ {}", startTime, endTime);

        try {
            // 리스트 데이터 조회
            List<Map<String, Object>> dataList = checkpointMapper.selectCheckpointList(
                    targetInstanceId, 
                    startTime, 
                    endTime, 
                    statusList
            );
            
            if (dataList == null || dataList.isEmpty()) {
                log.warn("Checkpoint 리스트 데이터 없음 - instanceId: {}, timeRange: {}", targetInstanceId, timeRange);
                return CheckpointDto.ListResponse.builder()
                        .data(new ArrayList<>())
                        .total(0L)
                        .build();
            }
            
            // DTO 변환
            List<CheckpointDto.ListItem> items = dataList.stream()
                    .map(this::convertToListItem)
                    .collect(Collectors.toList());
            
            return CheckpointDto.ListResponse.builder()
                    .data(items)
                    .total((long) items.size())
                    .build();
                    
        } catch (Exception e) {
            log.error("Checkpoint 리스트 데이터 조회 중 오류 발생", e);
            throw new RuntimeException("Checkpoint 리스트 데이터 조회 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 시간 범위 문자열을 기준으로 시작 시간 계산
     * @param endTime 종료 시간
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
     * @param data Map 데이터
     * @return ListItem DTO
     */
    private CheckpointDto.ListItem convertToListItem(Map<String, Object> data) {
        return CheckpointDto.ListItem.builder()
                .id(getString(data, "id"))
                .timestamp(getString(data, "timestamp"))
                .type(getString(data, "type"))
                .writeTime(getDoubleValue(data, "writetime"))
                .syncTime(getDoubleValue(data, "synctime"))
                .totalTime(getDoubleValue(data, "totaltime"))
                .walGenerated(getString(data, "walgenerated"))
                .walFilesAdded(getLongValue(data, "walfilesadded"))
                .walFilesRemoved(getLongValue(data, "walfilesremoved"))
                .checkpointDistance(getString(data, "checkpointdistance"))
                .buffersWritten(getLongValue(data, "bufferswritten"))
                .buffersBackend(getLongValue(data, "buffersbackend"))
                .avgBuffersPerSec(getDoubleValue(data, "avgbufferspersec"))
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
    
    private CheckpointDto.AvgWriteTime createEmptyAvgWriteTime() {
        return CheckpointDto.AvgWriteTime.builder()
                .categories(new ArrayList<>())
                .data(new ArrayList<>())
                .average(0.0)
                .max(0.0)
                .min(0.0)
                .build();
    }
    
    private CheckpointDto.Occurrence createEmptyOccurrence() {
        return CheckpointDto.Occurrence.builder()
                .categories(new ArrayList<>())
                .requested(new ArrayList<>())
                .timed(new ArrayList<>())
                .requestedTotal(0L)
                .timedTotal(0L)
                .ratio(0.0)
                .build();
    }
    
    private CheckpointDto.WalGeneration createEmptyWalGeneration() {
        return CheckpointDto.WalGeneration.builder()
                .categories(new ArrayList<>())
                .data(new ArrayList<>())
                .total(0L)
                .average(0.0)
                .max(0L)
                .build();
    }
    
    private CheckpointDto.ProcessTime createEmptyProcessTime() {
        return CheckpointDto.ProcessTime.builder()
                .categories(new ArrayList<>())
                .syncTime(new ArrayList<>())
                .writeTime(new ArrayList<>())
                .avgSync(0.0)
                .avgWrite(0.0)
                .avgTotal(0.0)
                .build();
    }
    
    private CheckpointDto.Buffer createEmptyBuffer() {
        return CheckpointDto.Buffer.builder()
                .categories(new ArrayList<>())
                .data(new ArrayList<>())
                .average(0.0)
                .max(0.0)
                .min(0.0)
                .build();
    }
    
    private CheckpointDto.CheckpointInterval createEmptyCheckpointInterval() {
        return CheckpointDto.CheckpointInterval.builder()
                .categories(new ArrayList<>())
                .data(new ArrayList<>())
                .average(0.0)
                .max(0.0)
                .min(0.0)
                .build();
    }
    
    private CheckpointDto.RecentStats createEmptyRecentStats() {
        return CheckpointDto.RecentStats.builder()
                .buffersWritten(0L)
                .avgTotalProcessTime(0.0)
                .checkpointDistance(0.0)
                .checkpointInterval(0.0)
                .avgWalGenerationSpeed(0.0)
                .build();
    }
}
