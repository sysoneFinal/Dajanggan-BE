package com.dajanggan.domain.vacuum.service;

import com.dajanggan.domain.vacuum.dto.VacuumMaintenanceDto;
import com.dajanggan.domain.vacuum.dto.agg.VacuumAgg1mDto;
import com.dajanggan.domain.vacuum.dto.agg.VacuumAgg5mDto;
import com.dajanggan.domain.vacuum.repository.VacuumMaintenanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
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

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * 대시보드용 데이터 조회
     */
    public VacuumMaintenanceDto.Response getDashboardData(
            int hours, Long databaseId, Long instanceId, String tableName) {

        OffsetDateTime endTime = OffsetDateTime.now();
        OffsetDateTime startTime = endTime.minusHours(hours);

        log.info("🔍 Vacuum Dashboard: databaseId={}, instanceId={}, hours={}",
                databaseId, instanceId, hours);

        return VacuumMaintenanceDto.Response.builder()
                .kpi(buildKpiMetrics(startTime, endTime, databaseId, instanceId, hours))
                .deadtuple(buildDeadTupleChart(startTime, endTime, databaseId, instanceId, hours))
                .autovacuum(buildAutovacuumChart(startTime, endTime, databaseId, instanceId, hours))
                .latency(buildLatencyChart(startTime, endTime, databaseId, instanceId, hours))
                .sessions(getCurrentSessions(databaseId, tableName))
                .build();
    }

    /**
     * KPI 지표 생성 (수정됨)
     *
     * - blockedSessions: 차단된 세션 수
     * - avgRunningTime: 실행 중인 vacuum의 평균 실행 시간
     * - totalDeadTuples: 총 dead tuple 수
     * - activeWorkers: 활성 워커 / 최대 워커
     */
    private VacuumMaintenanceDto.Kpi buildKpiMetrics(
            OffsetDateTime start, OffsetDateTime end,
            Long databaseId, Long instanceId, int hours) {

        try {
            log.info("🔍 KPI 조회 시작 - databaseId: {}, instanceId: {}, hours: {}, start: {}, end: {}",
                    databaseId, instanceId, hours, start, end);

            if (hours <= 1) {
                VacuumAgg1mDto summary = repository.getKpiFrom1m(databaseId, instanceId, start, end);

                log.info("📊 1분 집계 조회 결과: {}", summary != null ? "데이터 존재" : "NULL");

                if (summary == null) {
                    log.warn("⚠️ 1분 집계 데이터 없음 - 기본값 반환");
                    return getDefaultKpi();
                }

                // 각 필드 상세 로깅
                log.info("📈 blockedVacuumCount: {}", summary.getBlockedVacuumCount());
                log.info("📈 avgElapsedSeconds: {}", summary.getAvgElapsedSeconds());
                log.info("📈 totalDeadTuples: {}", summary.getTotalDeadTuples());
                log.info("📈 activeVacuumSessions: {}", summary.getActiveVacuumSessions());
                log.info("📈 maxWorkersConfigured: {}", summary.getMaxWorkersConfigured());

                // Active Workers 문자열 생성
                int activeWorkerCount = summary.getActiveVacuumSessions() != null ? summary.getActiveVacuumSessions() : 0;
                int maxWorkers = summary.getMaxWorkersConfigured() != null ? summary.getMaxWorkersConfigured() : 3;
                String activeWorkersStr = activeWorkerCount + "/" + maxWorkers;

                VacuumMaintenanceDto.Kpi kpi = VacuumMaintenanceDto.Kpi.builder()
                        .blockedSessions(summary.getBlockedVacuumCount() != null ? summary.getBlockedVacuumCount() : 0)
                        .avgRunningTime(summary.getAvgElapsedSeconds() != null ? summary.getAvgElapsedSeconds() : 0.0)
                        .totalDeadTuples(summary.getTotalDeadTuples() != null ? summary.getTotalDeadTuples() : 0L)
                        .activeWorkers(activeWorkersStr)
                        .build();

                log.info("✅ KPI 생성 완료: blockedSessions={}, avgRunningTime={}, totalDeadTuples={}, activeWorkers={}",
                        kpi.getBlockedSessions(), kpi.getAvgRunningTime(), kpi.getTotalDeadTuples(), kpi.getActiveWorkers());

                return kpi;

            } else {
                VacuumAgg5mDto summary = repository.getKpiFrom5m(databaseId, instanceId, start, end);

                if (summary == null) {
                    log.warn("⚠️ 5분 집계 데이터 없음 - 기본값 반환");
                    return getDefaultKpi();
                }

                // Active Workers 문자열 생성
                int activeWorkerCount = summary.getActiveVacuumSessions() != null ? summary.getActiveVacuumSessions() : 0;
                int maxWorkers = summary.getMaxWorkersConfigured() != null ? summary.getMaxWorkersConfigured() : 3;
                String activeWorkersStr = activeWorkerCount + "/" + maxWorkers;

                return VacuumMaintenanceDto.Kpi.builder()
                        .blockedSessions(summary.getBlockedVacuumCount() != null ? summary.getBlockedVacuumCount() : 0)
                        .avgRunningTime(summary.getAvgElapsedSeconds() != null ? summary.getAvgElapsedSeconds() : 0.0)
                        .totalDeadTuples(summary.getTotalDeadTuples() != null ? summary.getTotalDeadTuples() : 0L)
                        .activeWorkers(activeWorkersStr)
                        .build();
            }
        } catch (Exception e) {
            log.error("❌ KPI 조회 실패", e);
            return getDefaultKpi();
        }
    }

    private VacuumMaintenanceDto.Kpi getDefaultKpi() {
        return VacuumMaintenanceDto.Kpi.builder()
                .blockedSessions(0)
                .avgRunningTime(0.0)
                .totalDeadTuples(0L)
                .activeWorkers("0/3")
                .build();
    }

    /**
     * Dead Tuple 차트 데이터 생성
     * - 증가율 vs 감소율 (vacuum 처리 속도)
     */
    private VacuumMaintenanceDto.Chart buildDeadTupleChart(
            OffsetDateTime start, OffsetDateTime end,
            Long databaseId, Long instanceId, int hours) {

        List<String> labels = new ArrayList<>();
        List<Long> increaseRate = new ArrayList<>();
        List<Long> decreaseRate = new ArrayList<>();

        try {
            if (hours <= 1) {
                List<VacuumAgg1mDto> trends = repository.getTimeSeriesFrom1m(databaseId, instanceId, start, end);

                if (trends == null || trends.isEmpty()) {
                    log.warn("⚠️ 1분 집계 시계열 데이터 없음");
                    return getEmptyChart();
                }

                for (VacuumAgg1mDto t : trends) {
                    if (t == null || t.getCollectedAt() == null) continue;

                    labels.add(formatTimeLabel(t.getCollectedAt()));
                    increaseRate.add(t.getDeadTupleIncreaseRate() != null ? t.getDeadTupleIncreaseRate() : 0L);
                    decreaseRate.add(t.getDeadTupleDecreaseRate() != null ? t.getDeadTupleDecreaseRate() : 0L);
                }
            } else {
                List<VacuumAgg5mDto> trends = repository.getTimeSeriesFrom5m(databaseId, instanceId, start, end);

                if (trends == null || trends.isEmpty()) {
                    log.warn("⚠️ 5분 집계 시계열 데이터 없음");
                    return getEmptyChart();
                }

                for (VacuumAgg5mDto t : trends) {
                    if (t == null || t.getCollectedAt() == null) continue;

                    labels.add(formatTimeLabel(t.getCollectedAt()));
                    increaseRate.add(t.getDeadTupleIncreaseRate() != null ? t.getDeadTupleIncreaseRate() : 0L);
                    decreaseRate.add(t.getDeadTupleDecreaseRate() != null ? t.getDeadTupleDecreaseRate() : 0L);
                }
            }

            if (labels.isEmpty()) {
                log.warn("⚠️ 유효한 시계열 데이터가 없음");
                return getEmptyChart();
            }

        } catch (Exception e) {
            log.error("❌ Dead Tuple 차트 생성 실패", e);
            return getEmptyChart();
        }

        return VacuumMaintenanceDto.Chart.builder()
                .data(Arrays.asList(increaseRate, decreaseRate))
                .labels(labels)
                .build();
    }

    private VacuumMaintenanceDto.Chart getEmptyChart() {
        return VacuumMaintenanceDto.Chart.builder()
                .data(Arrays.asList(new ArrayList<>(), new ArrayList<>()))
                .labels(new ArrayList<>())
                .build();
    }

    /**
     * Autovacuum 차트 데이터 생성
     * - Cost Delay vs Active Workers
     */
    private VacuumMaintenanceDto.Chart buildAutovacuumChart(
            OffsetDateTime start, OffsetDateTime end,
            Long databaseId, Long instanceId, int hours) {

        List<String> labels = new ArrayList<>();
        List<Double> costDelay = new ArrayList<>();
        List<Integer> activeWorkers = new ArrayList<>();

        try {
            if (hours <= 1) {
                List<VacuumAgg1mDto> trends = repository.getTimeSeriesFrom1m(databaseId, instanceId, start, end);

                if (trends == null || trends.isEmpty()) {
                    log.warn("⚠️ 1분 집계 Autovacuum 데이터 없음");
                    return getEmptyChart();
                }

                for (VacuumAgg1mDto t : trends) {
                    if (t == null) continue;
                    labels.add(formatTimeLabel(t.getCollectedAt()));
                    costDelay.add(t.getAvgCostDelayMs() != null ? t.getAvgCostDelayMs() : 0.0);
                    activeWorkers.add(t.getActiveVacuumSessions() != null ? t.getActiveVacuumSessions() : 0);
                }
            } else {
                List<VacuumAgg5mDto> trends = repository.getTimeSeriesFrom5m(databaseId, instanceId, start, end);

                if (trends == null || trends.isEmpty()) {
                    log.warn("⚠️ 5분 집계 Autovacuum 데이터 없음");
                    return getEmptyChart();
                }

                for (VacuumAgg5mDto t : trends) {
                    if (t == null) continue;
                    labels.add(formatTimeLabel(t.getCollectedAt()));
                    costDelay.add(t.getAvgCostDelayMs() != null ? t.getAvgCostDelayMs() : 0.0);
                    activeWorkers.add(t.getActiveVacuumSessions() != null ? t.getActiveVacuumSessions() : 0);
                }
            }
        } catch (Exception e) {
            log.error("❌ Autovacuum 차트 생성 실패", e);
            return getEmptyChart();
        }

        return VacuumMaintenanceDto.Chart.builder()
                .data(Arrays.asList(costDelay, activeWorkers))
                .labels(labels)
                .build();
    }

    /**
     * Latency 차트 데이터 생성 (수정됨)
     * - 블로킹 대기 시간을 초 단위로 표시
     */
    private VacuumMaintenanceDto.Chart buildLatencyChart(
            OffsetDateTime start, OffsetDateTime end,
            Long databaseId, Long instanceId, int hours) {

        List<String> labels = new ArrayList<>();
        List<Double> latency = new ArrayList<>();

        try {
            if (hours <= 1) {
                List<VacuumAgg1mDto> trends = repository.getTimeSeriesFrom1m(databaseId, instanceId, start, end);

                if (trends == null || trends.isEmpty()) {
                    log.warn("⚠️ 1분 집계 Latency 데이터 없음");
                    return getEmptyChart();
                }

                for (VacuumAgg1mDto t : trends) {
                    if (t == null) continue;
                    labels.add(formatTimeLabel(t.getCollectedAt()));

                    // 대기 시간 = 블로킹 시간 + cost delay (초 단위)
                    double blockingSeconds = t.getAvgBlockedSeconds() != null ? t.getAvgBlockedSeconds() : 0.0;
                    double costDelaySeconds = t.getAvgCostDelayMs() != null ? t.getAvgCostDelayMs() / 1000.0 : 0.0;
                    latency.add(blockingSeconds + costDelaySeconds);
                }
            } else {
                List<VacuumAgg5mDto> trends = repository.getTimeSeriesFrom5m(databaseId, instanceId, start, end);

                if (trends == null || trends.isEmpty()) {
                    log.warn("⚠️ 5분 집계 Latency 데이터 없음");
                    return getEmptyChart();
                }

                for (VacuumAgg5mDto t : trends) {
                    if (t == null) continue;
                    labels.add(formatTimeLabel(t.getCollectedAt()));

                    // 대기 시간 = 블로킹 시간 + cost delay (초 단위)
                    double blockingSeconds = t.getAvgBlockedSeconds() != null ? t.getAvgBlockedSeconds() : 0.0;
                    double costDelaySeconds = t.getAvgCostDelayMs() != null ? t.getAvgCostDelayMs() / 1000.0 : 0.0;
                    latency.add(blockingSeconds + costDelaySeconds);
                }
            }
        } catch (Exception e) {
            log.error("❌ Latency 차트 생성 실패", e);
            return getEmptyChart();
        }

        return VacuumMaintenanceDto.Chart.builder()
                .data(Collections.singletonList(latency))
                .labels(labels)
                .build();
    }

    /**
     * 현재 실행 중인 Vacuum 세션 조회
     */
    public List<VacuumMaintenanceDto.Session> getCurrentSessions(
            Long databaseId, String tableName) {

        List<VacuumMaintenanceDto.VacuumSessionRaw> rawSessions =
                repository.getCurrentVacuumSessions(databaseId, tableName);

        if (rawSessions == null || rawSessions.isEmpty()) {
            log.warn("⚠️ Vacuum 세션 데이터가 없습니다");
            return new ArrayList<>();
        }

        return rawSessions.stream()
                .filter(raw -> raw != null && raw.getTableName() != null)
                .map(this::convertToSessionDto)
                .collect(Collectors.toList());
    }

    private VacuumMaintenanceDto.Session convertToSessionDto(
            VacuumMaintenanceDto.VacuumSessionRaw raw) {

        return VacuumMaintenanceDto.Session.builder()
                .tableName(raw.getTableName())
                .phase(raw.getSessionPhase())
                .deadTuples(formatTuples(raw.getDeadTupleTotal()))
                .trigger(Boolean.TRUE.equals(raw.getAutovacuum()) ? "autovacuum" : "manual")
                .elapsed(formatElapsed(raw.getElapsedSeconds()))
                .build();
    }

    // ========== 유틸리티 ==========

    private String formatTimeLabel(OffsetDateTime dateTime) {
        return dateTime.format(TIME_FORMATTER);
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