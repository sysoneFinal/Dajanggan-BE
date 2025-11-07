package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.domain.VacuumRawMetrics;
import com.dajanggan.domain.vacuum.domain.VacuumTrendMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Vacuum Bloat Repository
 * - Bloat 페이지 전용 Repository
 */
@Repository
@RequiredArgsConstructor
public class VacuumBloatRepository {

    private final VacuumBloatMapper bloatMapper;

    // ========== Raw Metrics ==========

    /**
     * Xmin Horizon 데이터 조회
     */
    public List<VacuumRawMetrics> findXminHorizonData(
            String databaseId, LocalDateTime startTime, LocalDateTime endTime) {
        return bloatMapper.findXminHorizonData(databaseId, startTime, endTime);
    }

    /**
     * 최근 메트릭 조회
     */
    public List<VacuumRawMetrics> findRecentMetrics(
            String databaseId, LocalDateTime since) {
        return bloatMapper.findRecentMetrics(databaseId, since);
    }

    /**
     * Blocker Xmin이 있는 최신 데이터 조회
     */
    public List<VacuumRawMetrics> findLatestWithBlockerXmin(String databaseId) {
        return bloatMapper.findLatestWithBlockerXmin(databaseId);
    }

    /**
     * Dead Tuple이 많은 테이블 조회
     */
    public List<VacuumRawMetrics> findTablesWithHighDeadTuples(
            String databaseId, Long threshold) {
        return bloatMapper.findTablesWithHighDeadTuples(databaseId, threshold);
    }

    // ========== Trend Metrics ==========

    /**
     * Bloat Trend 데이터 조회
     */
    public List<VacuumTrendMetrics> findBloatTrendData(
            String databaseId, LocalDateTime startDate) {
        return bloatMapper.findBloatTrendData(databaseId, startDate);
    }

    /**
     * Bloat 분포 조회
     */
    public List<Map<String, Object>> findBloatDistribution(
            String databaseId, LocalDateTime since) {
        return bloatMapper.findBloatDistribution(databaseId, since);
    }

    /**
     * 총 Bloat 크기 계산
     */
    public Long calculateTotalBloat(String databaseId, LocalDateTime since) {
        return bloatMapper.calculateTotalBloat(databaseId, since);
    }

    /**
     * Critical 테이블 수 조회
     */
    public Long countCriticalTables(String databaseId, LocalDateTime since) {
        return bloatMapper.countCriticalTables(databaseId, since);
    }

    /**
     * 일별 메트릭 조회
     */
    public List<VacuumTrendMetrics> findDailyMetrics(
            String databaseId, LocalDateTime startDate) {
        return bloatMapper.findDailyMetrics(databaseId, startDate);
    }

    /**
     * Wraparound 진행률이 높은 테이블 조회
     */
    public List<VacuumTrendMetrics> findTablesWithHighWraparoundProgress(
            String databaseId, Double threshold) {
        return bloatMapper.findTablesWithHighWraparoundProgress(databaseId, threshold);
    }

    /**
     * 최신 메트릭 조회
     */
    public List<VacuumTrendMetrics> findLatestMetrics(String databaseId) {
        return bloatMapper.findLatestMetrics(databaseId);
    }
}