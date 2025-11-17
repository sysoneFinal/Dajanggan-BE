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

    // ========== KPI 지표 (✅ aggTable 추가) ==========

    public Double getAvgDelaySeconds(
            OffsetDateTime start, OffsetDateTime end,
            Long databaseId, String aggTable) {
        return trendMapper.getAvgDelaySeconds(start, end, databaseId, aggTable);
    }

    public Double getAvgVacuumDuration(
            OffsetDateTime start, OffsetDateTime end,
            Long databaseId, String aggTable) {
        return trendMapper.getAvgVacuumDuration(start, end, databaseId, aggTable);
    }

    public Long getTotalDeadTuples(
            OffsetDateTime start, OffsetDateTime end,
            Long databaseId, String aggTable) {
        return trendMapper.getTotalDeadTuples(start, end, databaseId, aggTable);
    }

    public Integer getMaxWorkers(Long databaseId) {
        return rawMapper.getMaxWorkers(databaseId);
    }

    public Integer getActiveWorkers(Long databaseId) {
        return rawMapper.getActiveWorkers(databaseId);
    }

    // ========== 차트 데이터 (✅ aggTable 추가) ==========

    public List<VacuumMaintenanceDto.VacuumTrendRaw> getDeadTupleTrend(
            OffsetDateTime start, OffsetDateTime end, int buckets,
            Long databaseId, String aggTable) {
        return trendMapper.getDeadTupleTrend(start, end, buckets, databaseId, aggTable);
    }

    public List<VacuumMaintenanceDto.VacuumTrendRaw> getAutovacuumTrend(
            OffsetDateTime start, OffsetDateTime end, int buckets,
            Long databaseId, String aggTable) {
        return trendMapper.getAutovacuumTrend(start, end, buckets, databaseId, aggTable);
    }

    public List<VacuumMaintenanceDto.VacuumTrendRaw> getLatencyTrend(
            OffsetDateTime start, OffsetDateTime end, int buckets,
            Long databaseId, String aggTable) {
        return trendMapper.getLatencyTrend(start, end, buckets, databaseId, aggTable);
    }

    // ========== 세션 데이터 (변경 없음 - raw 테이블 사용) ==========

    public List<VacuumMaintenanceDto.VacuumSessionRaw> getCurrentVacuumSessions(
            Long databaseId, String tableName) {
        return rawMapper.getCurrentVacuumSessions(databaseId, tableName);
    }

    public List<Integer> getSessionProgressHistory(Long databaseId, String tableName, int limit) {
        return rawMapper.getSessionProgressHistory(databaseId, tableName, limit);
    }
}