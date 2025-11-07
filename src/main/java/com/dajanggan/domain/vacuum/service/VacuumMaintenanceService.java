package com.dajanggan.domain.vacuum.service;

import com.dajanggan.domain.vacuum.dto.VacuumMaintenanceDto;
import com.dajanggan.domain.vacuum.repository.VacuumMaintenanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VacuumMaintenanceService {

    private final VacuumMaintenanceRepository repository;

    // Maintenance 대시보드 전체 데이터 조회
    public VacuumMaintenanceDto.Response getDashboardData(int hours) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(hours);

        log.info("Vacuum Maintenance 대시보드 조회: {} ~ {}", startTime, endTime);

        return VacuumMaintenanceDto.Response.builder()
                .kpi(buildKpiMetrics(startTime, endTime))
                .deadtuple(buildDeadTupleChart(startTime, endTime))
                .autovacuum(buildAutovacuumChart(startTime, endTime))
                .latency(buildLatencyChart(startTime, endTime))
                .sessions(getCurrentSessions())
                .build();
    }

    // KPI 지표 계산
    private VacuumMaintenanceDto.Kpi buildKpiMetrics(LocalDateTime start, LocalDateTime end) {
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

        return VacuumMaintenanceDto.Kpi.builder()
                .avgDelay(avgDelay != null ? avgDelay : 0.0)
                .avgDuration(avgDuration != null ? avgDuration : 0.0)
                .totalDeadTuple(totalDeadTuple != null ? totalDeadTuple / 1_000_000.0 : 0.0)
                .autovacuumWorker((int) Math.round(utilization))
                .build();
    }

    // Dead Tuple 증가 vs 처리 속도 차트
    private VacuumMaintenanceDto.Chart buildDeadTupleChart(LocalDateTime start, LocalDateTime end) {
        List<VacuumMaintenanceDto.VacuumTrendRaw> trends = repository.getDeadTupleTrend(start, end, 24);

        List<String> labels = new ArrayList<>();
        List<Double> increaseRate = new ArrayList<>();
        List<Double> processRate = new ArrayList<>();

        for (VacuumMaintenanceDto.VacuumTrendRaw t : trends) {
            labels.add(t.getHourLabel());
            increaseRate.add(t.getDeadTupleIncreaseRate() != null ? t.getDeadTupleIncreaseRate() : 0.0);
            processRate.add(t.getAvgProgress() != null ? t.getAvgProgress() : 0.0);
        }

        return VacuumMaintenanceDto.Chart.builder()
                .data(Arrays.asList(increaseRate, processRate))
                .labels(labels)
                .build();
    }

    // Autovacuum Cost Delay & Workers 차트
    private VacuumMaintenanceDto.Chart buildAutovacuumChart(LocalDateTime start, LocalDateTime end) {
        List<VacuumMaintenanceDto.VacuumTrendRaw> trends = repository.getAutovacuumTrend(start, end, 24);

        List<String> labels = new ArrayList<>();
        List<Double> costDelay = new ArrayList<>();
        List<Integer> workers = new ArrayList<>();

        for (VacuumMaintenanceDto.VacuumTrendRaw t : trends) {
            labels.add(t.getHourLabel());
            costDelay.add(t.getAvgCostDelayMs() != null ? t.getAvgCostDelayMs() : 0.0);
            workers.add(t.getActiveWorkers() != null ? t.getActiveWorkers() : 0);
        }

        return VacuumMaintenanceDto.Chart.builder()
                .data(Arrays.asList(
                        costDelay.stream().map(d -> (Number) d).collect(Collectors.toList()),
                        workers.stream().map(i -> (Number) i).collect(Collectors.toList())
                ))
                .labels(labels)
                .build();
    }

    // Latency Trend 차트
    private VacuumMaintenanceDto.Chart buildLatencyChart(LocalDateTime start, LocalDateTime end) {
        List<VacuumMaintenanceDto.VacuumTrendRaw> trends = repository.getLatencyTrend(start, end, 24);

        List<String> labels = new ArrayList<>();
        List<Double> latency = new ArrayList<>();

        for (VacuumMaintenanceDto.VacuumTrendRaw t : trends) {
            labels.add(t.getHourLabel());
            latency.add(t.getAvgDelaySeconds() != null ? t.getAvgDelaySeconds() * 1000 : 0.0);
        }

        return VacuumMaintenanceDto.Chart.builder()
                .data(Collections.singletonList(latency))
                .labels(labels)
                .build();
    }

    // 현재 실행 중인 세션 목록
    public List<VacuumMaintenanceDto.Session> getCurrentSessions() {
        List<VacuumMaintenanceDto.VacuumSessionRaw> rawSessions = repository.getCurrentVacuumSessions();

        return rawSessions.stream()
                .map(this::convertToSessionDto)
                .collect(Collectors.toList());
    }

    // Raw DTO → Session DTO 변환
    private VacuumMaintenanceDto.Session convertToSessionDto(VacuumMaintenanceDto.VacuumSessionRaw raw) {
        List<Integer> progressSeries = repository.getSessionProgressHistory(
                raw.getDatabaseId(),
                9
        );

        return VacuumMaintenanceDto.Session.builder()
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
}