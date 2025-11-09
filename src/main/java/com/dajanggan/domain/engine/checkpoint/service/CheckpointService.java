package com.dajanggan.domain.engine.checkpoint.service;

import com.dajanggan.domain.engine.checkpoint.dto.CheckpointDashboardDto;
import com.dajanggan.domain.engine.checkpoint.dto.CheckpointRawDto;
import com.dajanggan.domain.engine.checkpoint.dto.TimeSeriesDto;
import com.dajanggan.domain.engine.checkpoint.dto.CheckpointListRequest;
import com.dajanggan.domain.engine.checkpoint.dto.CheckpointListResponse;
import com.dajanggan.domain.engine.checkpoint.dto.CheckpointListDto;
import com.dajanggan.domain.engine.checkpoint.repository.CheckpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CheckpointService {

    private final CheckpointRepository checkpointRepository;
    
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");

    /**
     * Checkpoint 대시보드 데이터 조회
     * @param instanceId 인스턴스 ID
     * @return 대시보드 전체 데이터
     */
    public CheckpointDashboardDto getDashboardData(Long instanceId) {
        log.info("Fetching checkpoint dashboard data for instance: {}", instanceId);

        OffsetDateTime endTime = OffsetDateTime.now();
        OffsetDateTime startTime = endTime.minusHours(24);

        // 1. 시계열 데이터 조회 (24시간, 2시간 단위)
        List<TimeSeriesDto> timeSeriesData = checkpointRepository.selectTimeSeriesData(
                instanceId, startTime, endTime
        );

        // 2. 24시간 전체 통계
        CheckpointRawDto stats24h = checkpointRepository.select24HourStats(
                instanceId, startTime, endTime
        );

        // 3. 최근 5분 통계
        CheckpointRawDto recentStats = checkpointRepository.selectRecentStats(instanceId, 5);

        // 4. 이전 5분 통계 (변화량 계산용)
        CheckpointRawDto previousStats = checkpointRepository.selectPreviousStats(instanceId, 5);

        // 5. 데이터 조합
        CheckpointDashboardDto.DataWrapper data = buildDataWrapper(
                instanceId,
                timeSeriesData,
                stats24h,
                recentStats,
                previousStats
        );

        return CheckpointDashboardDto.builder()
                .data(data)
                .timestamp(OffsetDateTime.now())
                .success(true)
                .build();
    }

    /**
     * DataWrapper 구성
     */
    private CheckpointDashboardDto.DataWrapper buildDataWrapper(
            Long instanceId,
            List<TimeSeriesDto> timeSeriesData,
            CheckpointRawDto stats24h,
            CheckpointRawDto recentStats,
            CheckpointRawDto previousStats
    ) {
        return CheckpointDashboardDto.DataWrapper.builder()
                .instance(instanceId)
                .requestRatio(buildRequestRatio(stats24h))
                .avgWriteTime(buildAvgWriteTime(timeSeriesData))
                .occurrence(buildOccurrence(timeSeriesData))
                .walGeneration(buildWalGeneration(timeSeriesData))
                .processTime(buildProcessTime(timeSeriesData))
                .buffer(buildBuffer(timeSeriesData))
                .checkpointInterval(buildCheckpointInterval(timeSeriesData))
                .recentStats(buildRecentStats(recentStats, previousStats))
                .build();
    }

    /**
     * RequestRatio 구성
     */
    private CheckpointDashboardDto.RequestRatio buildRequestRatio(CheckpointRawDto stats24h) {
        if (stats24h == null) {
            return CheckpointDashboardDto.RequestRatio.builder()
                    .value(0.0)
                    .requestedCount(0L)
                    .timedCount(0L)
                    .build();
        }

        return CheckpointDashboardDto.RequestRatio.builder()
                .value(stats24h.getRequestRatio() != null ? 
                       Math.round(stats24h.getRequestRatio() * 10.0) / 10.0 : 0.0)
                .requestedCount(stats24h.getCheckpointsReq() != null ? 
                                stats24h.getCheckpointsReq() : 0L)
                .timedCount(stats24h.getCheckpointsTimed() != null ? 
                            stats24h.getCheckpointsTimed() : 0L)
                .build();
    }

    /**
     * AvgWriteTime 구성 (24시간 추이)
     */
    private CheckpointDashboardDto.AvgWriteTime buildAvgWriteTime(List<TimeSeriesDto> timeSeriesData) {
        List<String> categories = extractTimeLabels(timeSeriesData);
        List<Double> data = timeSeriesData.stream()
                .map(ts -> ts.getAvgWriteTime() != null ? 
                          Math.round(ts.getAvgWriteTime() / 1000.0 * 10.0) / 10.0 : 0.0) // ms -> 초
                .collect(Collectors.toList());

        double average = data.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        
        double max = data.stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);
        
        double min = data.stream()
                .mapToDouble(Double::doubleValue)
                .min()
                .orElse(0.0);

        return CheckpointDashboardDto.AvgWriteTime.builder()
                .categories(categories)
                .data(data)
                .average(Math.round(average * 10.0) / 10.0)
                .max(Math.round(max * 10.0) / 10.0)
                .min(Math.round(min * 10.0) / 10.0)
                .build();
    }

    /**
     * Occurrence 구성 (Checkpoint 발생 추이)
     */
    private CheckpointDashboardDto.Occurrence buildOccurrence(List<TimeSeriesDto> timeSeriesData) {
        List<String> categories = extractTimeLabels(timeSeriesData);
        List<Long> requested = timeSeriesData.stream()
                .map(ts -> ts.getRequestedCount() != null ? ts.getRequestedCount() : 0L)
                .collect(Collectors.toList());
        
        List<Long> timed = timeSeriesData.stream()
                .map(ts -> ts.getTimedCount() != null ? ts.getTimedCount() : 0L)
                .collect(Collectors.toList());

        long requestedTotal = requested.stream().mapToLong(Long::longValue).sum();
        long timedTotal = timed.stream().mapToLong(Long::longValue).sum();
        
        double ratio = (requestedTotal + timedTotal) > 0 ?
                Math.round((double) requestedTotal / (requestedTotal + timedTotal) * 1000.0) / 10.0 : 0.0;

        return CheckpointDashboardDto.Occurrence.builder()
                .categories(categories)
                .requested(requested)
                .timed(timed)
                .requestedTotal(requestedTotal)
                .timedTotal(timedTotal)
                .ratio(ratio)
                .build();
    }

    /**
     * WalGeneration 구성
     */
    private CheckpointDashboardDto.WalGeneration buildWalGeneration(List<TimeSeriesDto> timeSeriesData) {
        List<String> categories = extractTimeLabels(timeSeriesData);
        List<Long> data = timeSeriesData.stream()
                .map(ts -> ts.getWalBytes() != null ? ts.getWalBytes() : 0L)
                .collect(Collectors.toList());

        long total = data.stream().mapToLong(Long::longValue).sum();
        long average = data.isEmpty() ? 0L : total / data.size();
        long max = data.stream().mapToLong(Long::longValue).max().orElse(0L);

        return CheckpointDashboardDto.WalGeneration.builder()
                .categories(categories)
                .data(data)
                .total(total)
                .average(average)
                .max(max)
                .build();
    }

    /**
     * ProcessTime 구성
     */
    private CheckpointDashboardDto.ProcessTime buildProcessTime(List<TimeSeriesDto> timeSeriesData) {
        List<String> categories = extractTimeLabels(timeSeriesData);
        List<Long> syncTime = timeSeriesData.stream()
                .map(ts -> ts.getAvgSyncTime() != null ? 
                          Math.round(ts.getAvgSyncTime()) : 0L)
                .collect(Collectors.toList());
        
        List<Long> writeTime = timeSeriesData.stream()
                .map(ts -> ts.getAvgWriteTime() != null ? 
                          Math.round(ts.getAvgWriteTime()) : 0L)
                .collect(Collectors.toList());

        long avgSync = syncTime.stream().mapToLong(Long::longValue).sum() / 
                      (syncTime.isEmpty() ? 1 : syncTime.size());
        long avgWrite = writeTime.stream().mapToLong(Long::longValue).sum() / 
                       (writeTime.isEmpty() ? 1 : writeTime.size());
        long avgTotal = avgSync + avgWrite;

        return CheckpointDashboardDto.ProcessTime.builder()
                .categories(categories)
                .syncTime(syncTime)
                .writeTime(writeTime)
                .avgSync(avgSync)
                .avgWrite(avgWrite)
                .avgTotal(avgTotal)
                .build();
    }

    /**
     * Buffer 구성
     */
    private CheckpointDashboardDto.Buffer buildBuffer(List<TimeSeriesDto> timeSeriesData) {
        List<String> categories = extractTimeLabels(timeSeriesData);
        List<Long> data = timeSeriesData.stream()
                .map(ts -> ts.getBuffersCheckpoint() != null ? ts.getBuffersCheckpoint() : 0L)
                .collect(Collectors.toList());

        long average = data.isEmpty() ? 0L : 
                      data.stream().mapToLong(Long::longValue).sum() / data.size();
        long max = data.stream().mapToLong(Long::longValue).max().orElse(0L);
        long min = data.stream().mapToLong(Long::longValue).min().orElse(0L);

        return CheckpointDashboardDto.Buffer.builder()
                .categories(categories)
                .data(data)
                .average(average)
                .max(max)
                .min(min)
                .build();
    }

    /**
     * CheckpointInterval 구성 (Checkpoint 간격 추이)
     */
    private CheckpointDashboardDto.CheckpointInterval buildCheckpointInterval(List<TimeSeriesDto> timeSeriesData) {
        List<String> categories = extractTimeLabels(timeSeriesData);
        List<Double> data = timeSeriesData.stream()
                .map(ts -> ts.getAvgIntervalMinutes() != null ? 
                          Math.round(ts.getAvgIntervalMinutes() * 10.0) / 10.0 : 0.0)
                .collect(Collectors.toList());

        double average = data.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        
        double max = data.stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);
        
        double min = data.stream()
                .filter(d -> d > 0.0)  // 0을 제외하고 최소값 계산
                .mapToDouble(Double::doubleValue)
                .min()
                .orElse(0.0);

        return CheckpointDashboardDto.CheckpointInterval.builder()
                .categories(categories)
                .data(data)
                .average(Math.round(average * 10.0) / 10.0)
                .max(Math.round(max * 10.0) / 10.0)
                .min(Math.round(min * 10.0) / 10.0)
                .build();
    }

    /**
     * RecentStats 구성 (최근 5분 평균 통계)
     */
    private CheckpointDashboardDto.RecentStats buildRecentStats(
            CheckpointRawDto recentStats,
            CheckpointRawDto previousStats
    ) {
        if (recentStats == null) {
            return buildEmptyRecentStats();
        }

        return CheckpointDashboardDto.RecentStats.builder()
                .buffersWritten(buildMetricWithDiff(
                        recentStats.getBuffersCheckpoint() != null ?
                            recentStats.getBuffersCheckpoint().doubleValue() : 0.0,
                        previousStats != null && previousStats.getBuffersCheckpoint() != null ?
                            previousStats.getBuffersCheckpoint().doubleValue() : 0.0
                ))
                .avgTotalProcessTime(buildMetricWithDiff(
                        recentStats.getTotalTime() != null ?
                            Math.round(recentStats.getTotalTime() / 1000.0 * 100.0) / 100.0 : 0.0,
                        previousStats != null && previousStats.getTotalTime() != null ?
                            Math.round(previousStats.getTotalTime() / 1000.0 * 100.0) / 100.0 : 0.0
                ))
                .checkpointDistance(buildMetricWithDiff(
                        recentStats.getCheckpointDistance() != null ?
                            recentStats.getCheckpointDistance().doubleValue() : 0.0,
                        previousStats != null && previousStats.getCheckpointDistance() != null ?
                            previousStats.getCheckpointDistance().doubleValue() : 0.0
                ))
                .checkpointInterval(buildMetricWithDiff(
                        recentStats.getCheckpointInterval() != null ?
                            Math.round(recentStats.getCheckpointInterval() * 10.0) / 10.0 : 0.0,
                        previousStats != null && previousStats.getCheckpointInterval() != null ?
                            Math.round(previousStats.getCheckpointInterval() * 10.0) / 10.0 : 0.0
                ))
                .avgWalGenerationSpeed(buildMetricWithDiff(
                        recentStats.getAvgWalGenerationSpeed() != null ?
                            Math.round(recentStats.getAvgWalGenerationSpeed() * 10.0) / 10.0 : 0.0,
                        previousStats != null && previousStats.getAvgWalGenerationSpeed() != null ?
                            Math.round(previousStats.getAvgWalGenerationSpeed() * 10.0) / 10.0 : 0.0
                ))
                .build();
    }

    /**
     * MetricWithDiff 생성
     */
    private CheckpointDashboardDto.MetricWithDiff buildMetricWithDiff(
            Double current,
            Double previous
    ) {
        double diff = current - previous;
        return CheckpointDashboardDto.MetricWithDiff.builder()
                .current(Math.round(current * 100.0) / 100.0)
                .diff(Math.round(diff * 100.0) / 100.0)
                .build();
    }

    /**
     * 빈 RecentStats 생성
     */
    private CheckpointDashboardDto.RecentStats buildEmptyRecentStats() {
        CheckpointDashboardDto.MetricWithDiff empty = CheckpointDashboardDto.MetricWithDiff.builder()
                .current(0.0)
                .diff(0.0)
                .build();

        return CheckpointDashboardDto.RecentStats.builder()
                .buffersWritten(empty)
                .avgTotalProcessTime(empty)
                .checkpointDistance(empty)
                .checkpointInterval(empty)
                .avgWalGenerationSpeed(empty)
                .build();
    }

    /**
     * 시간 라벨 추출
     */
    private List<String> extractTimeLabels(List<TimeSeriesDto> timeSeriesData) {
        return timeSeriesData.stream()
                .map(TimeSeriesDto::getTimeLabel)
                .collect(Collectors.toList());
    }

    /**
     * Checkpoint 리스트 조회 (필터링 + 페이징)
     * @param request 리스트 요청 파라미터
     * @return 페이징된 리스트 응답
     */
    public CheckpointListResponse getCheckpointList(CheckpointListRequest request) {
        log.info("Fetching checkpoint list for instance: {}, period: {}", 
                request.getInstanceId(), request.getPeriod());

        // 1. 시간 범위 계산
        OffsetDateTime endTime = OffsetDateTime.now();
        OffsetDateTime startTime = endTime.minusHours(request.getPeriodInHours());

        // 2. 리스트 데이터 조회
        List<CheckpointRawDto> rawList = checkpointRepository.selectCheckpointList(
                request.getInstanceId(),
                startTime,
                endTime,
                request.getTypes(),
                request.getOffset(),
                request.getLimit()
        );

        // 3. 총 개수 조회
        Long totalElements = checkpointRepository.countCheckpointList(
                request.getInstanceId(),
                startTime,
                endTime,
                request.getTypes()
        );

        // 4. DTO 변환 및 상태 판단
        List<CheckpointListDto> dataList = rawList.stream()
                .map(this::convertToListDto)
                .collect(Collectors.toList());

        // 5. 상태 필터 적용 (백엔드에서 추가 필터링)
        if (request.getStatuses() != null && !request.getStatuses().isEmpty()) {
            dataList = dataList.stream()
                    .filter(dto -> request.getStatuses().contains(dto.getStatus()))
                    .collect(Collectors.toList());
        }

        // 6. 페이징 정보 계산
        int size = request.getLimit();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int currentPage = request.getPage() != null ? request.getPage() : 1;

        // 7. 응답 생성
        return CheckpointListResponse.builder()
                .data(dataList)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .currentPage(currentPage)
                .size(size)
                .success(true)
                .build();
    }

    /**
     * CheckpointRawDto를 CheckpointListDto로 변환
     */
    private CheckpointListDto convertToListDto(CheckpointRawDto raw) {
        // 1. 기본 시간 변환 (ms -> 초)
        Double writeTime = raw.getCheckpointWriteTime() != null ? 
                Math.round(raw.getCheckpointWriteTime() / 1000.0 * 10.0) / 10.0 : 0.0;
        Double syncTime = raw.getCheckpointSyncTime() != null ? 
                Math.round(raw.getCheckpointSyncTime() / 1000.0 * 10.0) / 10.0 : 0.0;
        Double totalTime = raw.getTotalTime() != null ? 
                Math.round(raw.getTotalTime() / 1000.0 * 10.0) / 10.0 : 0.0;

        // 2. WAL 생성량 변환 (bytes -> GB)
        String walGenerated = formatWalSize(raw.getWalBytes());

        // 3. Checkpoint 간격 포맷팅
        String checkpointDistance = formatCheckpointDistance(raw.getIntervalMinutes());

        // 4. 요청 비율 계산
        Double requestRatio = raw.getRequestRatio() != null ? raw.getRequestRatio() : 0.0;

        // 5. Backend 비율 계산
        Double backendRatio = calculateBackendRatio(
                raw.getBuffersBackend(),
                raw.getBuffersCheckpoint()
        );

        // 6. 상태 판단
        String status = determineStatus(
                totalTime,
                requestRatio,
                raw.getIntervalMinutes(),
                backendRatio
        );

        return CheckpointListDto.builder()
                .id(raw.getCheckpointRawId())
                .timestamp(raw.getCollectedAt())
                .type(raw.getCheckpointType())
                .writeTime(writeTime)
                .syncTime(syncTime)
                .totalTime(totalTime)
                .walGenerated(walGenerated)
                .walFilesAdded(raw.getWalFilesAdded())
                .walFilesRemoved(raw.getWalFilesRemoved())
                .checkpointDistance(checkpointDistance)
                .buffersWritten(raw.getBuffersCheckpoint() != null ? raw.getBuffersCheckpoint() : 0L)
                .buffersBackend(raw.getBuffersBackend() != null ? raw.getBuffersBackend() : 0L)
                .avgBuffersPerSec(raw.getAvgBuffersPerSec() != null ? raw.getAvgBuffersPerSec() : 0L)
                .status(status)
                .build();
    }

    /**
     * WAL 크기 포맷팅 (bytes -> GB/MB)
     */
    private String formatWalSize(Long walBytes) {
        if (walBytes == null || walBytes == 0) {
            return "0GB";
        }

        double gb = walBytes / (1024.0 * 1024.0 * 1024.0);
        if (gb >= 1.0) {
            return String.format("%.1fGB", gb);
        }

        double mb = walBytes / (1024.0 * 1024.0);
        return String.format("%.1fMB", mb);
    }

    /**
     * Checkpoint 간격 포맷팅 (분)
     */
    private String formatCheckpointDistance(Double intervalMinutes) {
        if (intervalMinutes == null || intervalMinutes == 0) {
            return "-";
        }

        long minutes = Math.round(intervalMinutes);
        if (minutes < 60) {
            return minutes + "분";
        }

        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        
        if (remainingMinutes == 0) {
            return hours + "시간";
        }
        
        return hours + "시간 " + remainingMinutes + "분";
    }

    /**
     * Backend 비율 계산
     */
    private Double calculateBackendRatio(Long buffersBackend, Long buffersCheckpoint) {
        if (buffersBackend == null || buffersCheckpoint == null || buffersCheckpoint == 0) {
            return 0.0;
        }

        return Math.round((double) buffersBackend / buffersCheckpoint * 1000.0) / 10.0;
    }

    /**
     * 상태 판단 (정상/주의/위험)
     * 
     * 위험 조건:
     * - totalTime > 60초
     * - requestedRatio > 30%
     * - interval < 3분
     * - backendRatio > 5%
     * 
     * 주의 조건:
     * - totalTime > 30초
     * - requestedRatio > 10%
     * - interval < 5분
     * - backendRatio > 1%
     */
    private String determineStatus(
            Double totalTime,
            Double requestRatio,
            Double intervalMinutes,
            Double backendRatio
    ) {
        // 위험: 하나라도 위험 조건 충족
        if (totalTime != null && totalTime > 60.0) {
            return "위험";
        }
        if (requestRatio != null && requestRatio > 30.0) {
            return "위험";
        }
        if (intervalMinutes != null && intervalMinutes < 3.0) {
            return "위험";
        }
        if (backendRatio != null && backendRatio > 5.0) {
            return "위험";
        }

        // 주의: 하나라도 주의 조건 충족
        if (totalTime != null && totalTime > 30.0) {
            return "주의";
        }
        if (requestRatio != null && requestRatio > 10.0) {
            return "주의";
        }
        if (intervalMinutes != null && intervalMinutes < 5.0) {
            return "주의";
        }
        if (backendRatio != null && backendRatio > 1.0) {
            return "주의";
        }

        // 정상
        return "정상";
    }
}
