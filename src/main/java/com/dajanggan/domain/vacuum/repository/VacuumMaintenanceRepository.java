package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.VacuumMaintenanceDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class VacuumMaintenanceRepository {

    private final VacuumRawMapper rawMapper;
    private final VacuumTrendMapper trendMapper;

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

    public List<VacuumMaintenanceDto.VacuumTrendRaw> getDeadTupleTrend(
            LocalDateTime start, LocalDateTime end, int buckets) {
        return trendMapper.getDeadTupleTrend(start, end, buckets);
    }

    public List<VacuumMaintenanceDto.VacuumTrendRaw> getAutovacuumTrend(
            LocalDateTime start, LocalDateTime end, int buckets) {
        return trendMapper.getAutovacuumTrend(start, end, buckets);
    }

    public List<VacuumMaintenanceDto.VacuumTrendRaw> getLatencyTrend(
            LocalDateTime start, LocalDateTime end, int buckets) {
        return trendMapper.getLatencyTrend(start, end, buckets);
    }

    // ========== 세션 데이터 ==========

    public List<VacuumMaintenanceDto.VacuumSessionRaw> getCurrentVacuumSessions() {
        return rawMapper.getCurrentVacuumSessions();
    }

    public List<Integer> getSessionProgressHistory(String databaseId, int limit) {
        return rawMapper.getSessionProgressHistory(databaseId, limit);
    }
}