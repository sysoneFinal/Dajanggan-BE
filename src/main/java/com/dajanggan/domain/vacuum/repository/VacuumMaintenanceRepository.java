package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.VacuumMaintenanceDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class VacuumMaintenanceRepository {

    private final VacuumRawMapper rawMapper;
    private final VacuumTrendMapper trendMapper;

    // ========== KPI 지표 ==========

    public Double getAvgDelaySeconds(
            OffsetDateTime start, OffsetDateTime end,
           Long databaseId) {
        return trendMapper.getAvgDelaySeconds(start, end, databaseId);
    }

    public Double getAvgVacuumDuration(
            OffsetDateTime start, OffsetDateTime end,
            Long databaseId) {
        return trendMapper.getAvgVacuumDuration(start, end, databaseId);
    }

    public Long getTotalDeadTuples(
            OffsetDateTime start, OffsetDateTime end,
            Long databaseId) {
        return trendMapper.getTotalDeadTuples(start, end, databaseId);
    }

    public Integer getMaxWorkers(Long databaseId) {
        return rawMapper.getMaxWorkers(databaseId);
    }

    public Integer getActiveWorkers(Long databaseId) {
        return rawMapper.getActiveWorkers(databaseId);
    }

    // ========== 차트 데이터 ==========

    public List<VacuumMaintenanceDto.VacuumTrendRaw> getDeadTupleTrend(
            OffsetDateTime start, OffsetDateTime end, int buckets,
            Long databaseId) {
        return trendMapper.getDeadTupleTrend(start, end, buckets, databaseId);
    }

    public List<VacuumMaintenanceDto.VacuumTrendRaw> getAutovacuumTrend(
            OffsetDateTime start, OffsetDateTime end, int buckets,
            Long databaseId) {
        return trendMapper.getAutovacuumTrend(start, end, buckets, databaseId);
    }

    public List<VacuumMaintenanceDto.VacuumTrendRaw> getLatencyTrend(
            OffsetDateTime start, OffsetDateTime end, int buckets,
            Long databaseId) {
        return trendMapper.getLatencyTrend(start, end, buckets, databaseId);
    }

    // ========== 세션 데이터 ==========

    public List<VacuumMaintenanceDto.VacuumSessionRaw> getCurrentVacuumSessions(
            Long databaseId, String tableName) {
        return rawMapper.getCurrentVacuumSessions(databaseId, tableName);
    }

    public List<Integer> getSessionProgressHistory(Long databaseId, String tableName, int limit) {
        return rawMapper.getSessionProgressHistory(databaseId, tableName,limit);
    }
}