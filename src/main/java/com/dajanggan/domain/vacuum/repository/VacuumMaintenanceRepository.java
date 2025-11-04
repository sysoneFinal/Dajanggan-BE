package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vacuum Maintenance Repository
 * - MyBatis Mapper 호출
 * - 데이터 접근 계층
 */
@Repository
@RequiredArgsConstructor
public class VacuumMaintenanceRepository {

    private final VacuumRawMapper rawMapper;
    private final VacuumRiskMapper riskMapper;
    private final VacuumTrendMapper trendMapper;
    private final VacuumHistoryMapper historyMapper;

    // ========== KPI 지표 ==========

    public Double getAvgDelaySeconds(LocalDateTime start, LocalDateTime end) {
        return trendMapper.getAvgDelaySeconds(start, end);
    }

    public Double getAvgVacuumDuration(LocalDateTime start, LocalDateTime end) {
        return trendMapper.getAvgVacuumDuration(start, end);
    }

    public Long getTotalDeadTuples(LocalDateTime start, LocalDateTime end) {
        return trendMapper.getTotalDeadTuples(start, end);
    }

    public Integer getMaxWorkers() {
        return rawMapper.getMaxWorkers();
    }

    public Integer getActiveWorkers() {
        return rawMapper.getActiveWorkers();
    }

    // ========== 차트 데이터 ==========

    public List<VacuumTrendDto> getDeadTupleTrend(
            LocalDateTime start, LocalDateTime end, int buckets) {
        return trendMapper.getDeadTupleTrend(start, end, buckets);
    }

    public List<VacuumTrendDto> getAutovacuumTrend(
            LocalDateTime start, LocalDateTime end, int buckets) {
        return trendMapper.getAutovacuumTrend(start, end, buckets);
    }

    public List<VacuumTrendDto> getLatencyTrend(
            LocalDateTime start, LocalDateTime end, int buckets) {
        return trendMapper.getLatencyTrend(start, end, buckets);
    }

    // ========== 세션 데이터 ==========

    public List<VacuumRawDto> getCurrentVacuumSessions() {
        return rawMapper.getCurrentVacuumSessions();
    }

    public List<Integer> getSessionProgressHistory(String databaseId, int limit) {
        return rawMapper.getSessionProgressHistory(databaseId, limit);
    }

    // ========== History 데이터 ==========

    public List<VacuumHistoryRawDto> getVacuumHistoryList(
            LocalDateTime start, LocalDateTime end) {
        return historyMapper.getVacuumHistoryList(start, end);
    }

    public Integer getVacuumFrequency(Long databaseId, int hours) {
        return historyMapper.getVacuumFrequency(databaseId, hours);
    }


    // ========== Risk 데이터 ==========

    public List<BlockersPerHourRawDto> getBlockersPerHour(
            LocalDateTime start, LocalDateTime end, int buckets) {
        return riskMapper.getBlockersPerHour(start, end, buckets);
    }

    public List<TopBloatRawDto> getTopBloatTables(int limit) {
        return riskMapper.getTopBloatTables(limit);
    }

    public List<VacuumBlockerDetailRawDto> getVacuumBlockers() {
        return riskMapper.getVacuumBlockers();
    }

    public List<WraparoundProgressRawDto> getWraparoundProgress() {
        return riskMapper.getWraparoundProgress();
    }
}