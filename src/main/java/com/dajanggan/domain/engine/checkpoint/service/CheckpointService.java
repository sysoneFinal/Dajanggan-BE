package com.dajanggan.domain.engine.checkpoint.service;

import com.dajanggan.domain.engine.checkpoint.dto.CheckpointDashboardDto;
import com.dajanggan.domain.engine.checkpoint.dto.CheckpointRawDto;
import com.dajanggan.domain.engine.checkpoint.dto.TimeSeriesDto;
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
                        85.0, // TODO: 실제 계산 로직 필요
                        80.0
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
}
