package com.dajanggan.domain.vacuum.service;

import com.dajanggan.domain.vacuum.dto.*;
import com.dajanggan.domain.vacuum.repository.VacuumMaintenanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Vacuum Maintenance Service
 * - Service Facade 패턴
 * - 대시보드 데이터 통합 및 변환
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VacuumMaintenanceService {

    private final VacuumMaintenanceRepository repository;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 대시보드 전체 데이터 조회
     */
    public VacuumDashboardDto getDashboardData(int hours) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(hours);

        log.info("Vacuum 대시보드 조회: {} ~ {}", startTime, endTime);

        return VacuumDashboardDto.builder()
                .kpi(buildKpiMetrics(startTime, endTime))
                .deadtuple(buildDeadTupleChart(startTime, endTime))
                .autovacuum(buildAutovacuumChart(startTime, endTime))
                .latency(buildLatencyChart(startTime, endTime))
                .sessions(getCurrentSessions())
                .build();
    }

    /**
     * Vacuum History 목록 조회
     */
    public List<VacuumHistoryDto> getVacuumHistory(VacuumHistoryRequestDto request) {
        int hours = request.getHours() != null ? request.getHours() : 24;
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(hours);

        log.info("Vacuum History 조회: {} ~ {}, status={}", startTime, endTime, request.getStatus());

        List<VacuumHistoryRawDto> rawList = repository.getVacuumHistoryList(startTime, endTime);

        return rawList.stream()
                .map(raw -> convertToHistoryDto(raw, hours))
                .filter(dto -> filterByStatus(dto, request.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * Raw DTO → History DTO 변환
     */
    private VacuumHistoryDto convertToHistoryDto(VacuumHistoryRawDto raw, int hours) {
        // 빈도 계산
        Integer frequency = repository.getVacuumFrequency(raw.getDatabaseId(), hours);
        String frequencyStr = formatFrequency(frequency, hours);

        // 상태 판단 (bloat 비율 > 5% 또는 dead tuples > 100K → 주의)
        String status = determineStatus(raw);

        return VacuumHistoryDto.builder()
                .table(String.valueOf(raw.getDatabaseId()))
                .lastVacuum(formatDateTime(raw.getLastVacuum()))
                .lastAutovacuum(formatDateTime(raw.getLastAutovacuum()))
                .deadTuples(formatTuples(raw.getDeadTuples()))
                .modSinceAnalyze(formatTuples(raw.getModSinceAnalyze()))
                .bloatPct(formatBloatPct(raw.getBloatRatio()))
                .tableSize(formatBytes(raw.getTableSize()))
                .frequency(frequencyStr)
                .status(status)
                .build();
    }

    /**
     * 상태 필터링
     */
    private boolean filterByStatus(VacuumHistoryDto dto, String statusFilter) {
        if (statusFilter == null || statusFilter.isEmpty()) {
            return true;
        }
        return dto.getStatus().equals(statusFilter);
    }

    /**
     * 상태 판단 로직
     */
    private String determineStatus(VacuumHistoryRawDto raw) {
        // bloat 비율 > 5% 또는 dead tuples > 100K → 주의
        if (raw.getBloatRatio() != null && raw.getBloatRatio() > 0.05) {
            return "주의";
        }
        if (raw.getDeadTuples() != null && raw.getDeadTuples() > 100_000) {
            return "주의";
        }
        return "정상";
    }

    /**
     * 빈도 포맷팅
     */
    private String formatFrequency(Integer count, int hours) {
        if (count == null || count == 0) {
            return "0회";
        }

        // 일 단위 빈도 계산
        if (hours >= 24) {
            int perDay = (int) Math.round(count * 24.0 / hours);
            return perDay + "회/일";
        }

        return count + "회/" + hours + "h";
    }

    /**
     * DateTime 포맷팅
     */
    private String formatDateTime(Timestamp timestamp) {
        if (timestamp == null) return "-";
        return timestamp.toLocalDateTime().format(DATETIME_FORMATTER);
    }

    /**
     * Bloat 비율 포맷팅
     */
    private String formatBloatPct(Double ratio) {
        if (ratio == null) return "0.0%";
        return String.format("%.1f%%", ratio * 100);
    }

    /**
     * 바이트 크기 포맷팅
     */
    private String formatBytes(Long bytes) {
        if (bytes == null) return "0 B";
        if (bytes >= 1_073_741_824) return String.format("%.0f GB", bytes / 1_073_741_824.0);
        if (bytes >= 1_048_576) return String.format("%.0f MB", bytes / 1_048_576.0);
        if (bytes >= 1_024) return String.format("%.0f KB", bytes / 1_024.0);
        return bytes + " B";
    }

    // ========== 기존 메서드들 ==========

    /**
     * KPI 지표 계산
     */
    private KpiDto buildKpiMetrics(LocalDateTime start, LocalDateTime end) {
        Double avgDelay = repository.getAvgDelaySeconds(start, end);
        Double avgDuration = repository.getAvgVacuumDuration(start, end);
        Long totalDeadTuple = repository.getTotalDeadTuples(start, end);

        Integer maxWorkers = repository.getMaxWorkers();
        Integer activeWorkers = repository.getActiveWorkers();

        int maxWorkersValue = (maxWorkers != null) ? maxWorkers : 0;
        int activeWorkersValue = (activeWorkers != null) ? activeWorkers : 0;

        double utilization = maxWorkersValue > 0
                ? (activeWorkersValue * 100.0 / maxWorkersValue)
                : 0.0;

        return KpiDto.builder()
                .avgDelay(avgDelay != null ? avgDelay : 0.0)
                .avgDuration(avgDuration != null ? avgDuration : 0.0)
                .totalDeadTuple(totalDeadTuple != null ? totalDeadTuple / 1_000_000.0 : 0.0)
                .autovacuumWorker((int) Math.round(utilization))
                .build();
    }

    /**
     * Dead Tuple 증가 vs 처리 속도 차트
     */
    private ChartDto buildDeadTupleChart(LocalDateTime start, LocalDateTime end) {
        List<VacuumTrendDto> trends = repository.getDeadTupleTrend(start, end, 24);

        List<String> labels = new ArrayList<>();
        List<Double> increaseRate = new ArrayList<>();
        List<Double> processRate = new ArrayList<>();

        for (VacuumTrendDto t : trends) {
            labels.add(t.getHourLabel());
            increaseRate.add(t.getDeadTupleIncreaseRate() != null ? t.getDeadTupleIncreaseRate() : 0.0);
            processRate.add(t.getAvgProgress() != null ? t.getAvgProgress() : 0.0);
        }

        return ChartDto.builder()
                .data(Arrays.asList(increaseRate, processRate))
                .labels(labels)
                .build();
    }

    /**
     * Autovacuum Cost Delay & Workers 차트
     */
    private ChartDto buildAutovacuumChart(LocalDateTime start, LocalDateTime end) {
        List<VacuumTrendDto> trends = repository.getAutovacuumTrend(start, end, 24);

        List<String> labels = new ArrayList<>();
        List<Double> costDelay = new ArrayList<>();
        List<Integer> workers = new ArrayList<>();

        for (VacuumTrendDto t : trends) {
            labels.add(t.getHourLabel());
            costDelay.add(t.getAvgCostDelayMs() != null ? t.getAvgCostDelayMs() : 0.0);
            workers.add(t.getActiveWorkers() != null ? t.getActiveWorkers() : 0);
        }

        return ChartDto.builder()
                .data(Arrays.asList(
                        costDelay.stream().map(d -> (Number) d).collect(Collectors.toList()),
                        workers.stream().map(i -> (Number) i).collect(Collectors.toList())
                ))
                .labels(labels)
                .build();
    }

    /**
     * Latency Trend 차트
     */
    private ChartDto buildLatencyChart(LocalDateTime start, LocalDateTime end) {
        List<VacuumTrendDto> trends = repository.getLatencyTrend(start, end, 24);

        List<String> labels = new ArrayList<>();
        List<Double> latency = new ArrayList<>();

        for (VacuumTrendDto t : trends) {
            labels.add(t.getHourLabel());
            latency.add(t.getAvgDelaySeconds() != null ? t.getAvgDelaySeconds() * 1000 : 0.0);
        }

        return ChartDto.builder()
                .data(Collections.singletonList(latency))
                .labels(labels)
                .build();
    }

    /**
     * 현재 실행 중인 세션 목록
     */
    public List<VacuumSessionDto> getCurrentSessions() {
        List<VacuumRawDto> rawSessions = repository.getCurrentVacuumSessions();

        return rawSessions.stream()
                .map(this::convertToSessionDto)
                .collect(Collectors.toList());
    }

    /**
     * Raw DTO → Session DTO 변환
     */
    private VacuumSessionDto convertToSessionDto(VacuumRawDto raw) {
        List<Integer> progressSeries = repository.getSessionProgressHistory(
                raw.getDatabaseId(),
                9
        );

        return VacuumSessionDto.builder()
                .table(raw.getDatabaseId())
                .phase(raw.getSessionPhase())
                .deadTuples(formatTuples(raw.getDeadTupleTotal()))
                .trigger(Boolean.TRUE.equals(raw.getAutovacuum()) ? "autovacuum" : "manual")
                .elapsed(formatElapsed(raw.getElapsedSeconds()))
                .progressSeries(progressSeries)
                .build();
    }

    private String formatTuples(Long count) {
        if (count == null) return "0";
        if (count >= 1_000_000) return String.format("%.1fM", count / 1_000_000.0);
        if (count >= 1_000) return String.format("%.0fK", count / 1_000.0);
        return String.valueOf(count);
    }

    private String formatElapsed(Long seconds) {
        if (seconds == null) return "0s";
        if (seconds >= 3600) return String.format("%dh", seconds / 3600);
        if (seconds >= 60) return String.format("%dm", seconds / 60);
        return String.format("%ds", seconds);
    }

    /**
     * Vacuum Risk 페이지 데이터 조회
     */
    public VacuumRiskDto getVacuumRiskData(int hours) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(hours);

        log.info("Vacuum Risk 조회: {} ~ {}", startTime, endTime);

        return VacuumRiskDto.builder()
                .blockers(buildBlockersChart(startTime, endTime))
                .autovacuum(buildAutovacuumChart(startTime, endTime))
                .wraparound(buildWraparoundChart())
                .bloat(buildTopBloatTables(3))
                .vacuumblockers(buildVacuumBlockers())
                .build();
    }

    /**
     * Blockers per Hour 차트
     */
    private ChartDto buildBlockersChart(LocalDateTime start, LocalDateTime end) {
        List<BlockersPerHourRawDto> blockers = repository.getBlockersPerHour(start, end, 24);

        List<String> labels = new ArrayList<>();
        List<Double> data = new ArrayList<>();

        for (BlockersPerHourRawDto b : blockers) {
            labels.add(b.getHourLabel());
            data.add(b.getBlockersCount() != null ? b.getBlockersCount().doubleValue() : 0.0);
        }

        return ChartDto.builder()
                .data(Collections.singletonList(data))
                .labels(labels)
                .build();
    }

    /**
     * Wraparound Progress 차트
     */
    private ChartDto buildWraparoundChart() {
        List<WraparoundProgressRawDto> wraparound = repository.getWraparoundProgress();

        List<String> labels = new ArrayList<>();
        List<Double> data = new ArrayList<>();

        for (WraparoundProgressRawDto w : wraparound) {
            labels.add(String.valueOf(w.getDatabaseId()));
            data.add(w.getWraparoundProgressPct() != null ? w.getWraparoundProgressPct() : 0.0);
        }

        return ChartDto.builder()
                .data(Collections.singletonList(data))
                .labels(labels)
                .build();
    }

    /**
     * Top Bloat Tables 목록
     */
    private List<TopBloatTableDto> buildTopBloatTables(int limit) {
        List<TopBloatRawDto> rawList = repository.getTopBloatTables(limit);

        return rawList.stream()
                .map(this::convertToTopBloatDto)
                .collect(Collectors.toList());
    }

    /**
     * Vacuum Blockers 목록
     */
    private List<VacuumBlockerDto> buildVacuumBlockers() {
        List<VacuumBlockerDetailRawDto> rawList = repository.getVacuumBlockers();

        return rawList.stream()
                .map(this::convertToVacuumBlockerDto)
                .collect(Collectors.toList());
    }

    /**
     * Raw → TopBloatTableDto 변환
     */
    private TopBloatTableDto convertToTopBloatDto(TopBloatRawDto raw) {
        return TopBloatTableDto.builder()
                .table(String.valueOf(raw.getDatabaseId()))
                .bloat(formatBloatPct(raw.getBloatRatio()))
                .tableSize(formatBytes(raw.getTableSize()))
                .deadTuple(formatTuples(raw.getDeadTuples()))
                .build();
    }

    /**
     * Raw → VacuumBlockerDto 변환
     */
    private VacuumBlockerDto convertToVacuumBlockerDto(VacuumBlockerDetailRawDto raw) {
        return VacuumBlockerDto.builder()
                .table(String.valueOf(raw.getDatabaseId()))
                .pid(String.valueOf(raw.getPid()))
                .lockType(raw.getLockType() != null ? raw.getLockType() : "Unknown")
                .txAge(formatDuration(raw.getTransactionAge()))
                .blocked_seconds(formatDuration(raw.getBlockDuration()))
                .status(raw.getQueryState() != null ? raw.getQueryState() : "unknown")
                .build();
    }

    /**
     * Duration 포맷팅 (초 → "Xh Ym" 형식)
     */
    private String formatDuration(Long seconds) {
        if (seconds == null || seconds == 0) return "0s";

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;

        if (hours > 0 && minutes > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh", hours);
        } else if (minutes > 0) {
            return String.format("%dm", minutes);
        } else {
            return String.format("%ds", seconds);
        }
    }

}