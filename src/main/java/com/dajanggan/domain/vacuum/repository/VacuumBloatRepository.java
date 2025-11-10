package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.domain.VacuumRawMetrics;
import com.dajanggan.domain.vacuum.domain.VacuumTrendMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class VacuumBloatRepository {

    private final VacuumBloatMapper bloatMapper;

    // ========== Raw Metrics ==========

    public List<VacuumRawMetrics> findXminHorizonData(
            Long databaseId, OffsetDateTime startTime, OffsetDateTime endTime) {
        return bloatMapper.findXminHorizonData(databaseId, startTime, endTime);
    }

    public List<VacuumRawMetrics> findRecentMetrics(
            Long databaseId, OffsetDateTime since) {
        return bloatMapper.findRecentMetrics(databaseId, since);
    }

    public List<VacuumRawMetrics> findLatestWithBlockerXmin(Long databaseId) {
        return bloatMapper.findLatestWithBlockerXmin(databaseId);
    }

    public List<VacuumRawMetrics> findTablesWithHighDeadTuples(
            Long databaseId, Long threshold) {
        return bloatMapper.findTablesWithHighDeadTuples(databaseId, threshold);
    }

    // ========== Trend Metrics ==========

    public List<VacuumTrendMetrics> findBloatTrendData(
            Long databaseId, OffsetDateTime startDate) {
        return bloatMapper.findBloatTrendData(databaseId, startDate);
    }

    public List<Map<String, Object>> findBloatDistribution(
            Long databaseId, OffsetDateTime since) {
        return bloatMapper.findBloatDistribution(databaseId, since);
    }

    public Long calculateTotalBloat(Long databaseId, OffsetDateTime since) {
        return bloatMapper.calculateTotalBloat(databaseId, since);
    }

    public Long countCriticalTables(Long databaseId, OffsetDateTime since) {
        return bloatMapper.countCriticalTables(databaseId, since);
    }

    public List<VacuumTrendMetrics> findDailyMetrics(
            Long databaseId, OffsetDateTime startDate) {
        return bloatMapper.findDailyMetrics(databaseId, startDate);
    }

    public List<VacuumTrendMetrics> findTablesWithHighWraparoundProgress(
            Long databaseId, Double threshold) {
        return bloatMapper.findTablesWithHighWraparoundProgress(databaseId, threshold);
    }

    public List<VacuumTrendMetrics> findLatestMetrics(Long databaseId) {
        return bloatMapper.findLatestMetrics(databaseId);
    }
}