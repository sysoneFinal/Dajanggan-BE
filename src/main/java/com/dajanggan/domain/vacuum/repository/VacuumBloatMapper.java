package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.domain.VacuumRawMetrics;
import com.dajanggan.domain.vacuum.domain.VacuumTrendMetrics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface VacuumBloatMapper {

    // ========== Raw Metrics ==========

    /**
     * Xmin Horizon 데이터 조회
     */
    List<VacuumRawMetrics> findXminHorizonData(
            @Param("databaseId") Long databaseId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * 최근 메트릭스 조회
     */
    List<VacuumRawMetrics> findRecentMetrics(
            @Param("databaseId") Long databaseId,
            @Param("since") OffsetDateTime since
    );

    /**
     * Blocker Xmin이 있는 최신 데이터 조회
     */
    List<VacuumRawMetrics> findLatestWithBlockerXmin(
            @Param("databaseId") Long databaseId
    );

    /**
     * Dead Tuple이 높은 테이블 조회
     */
    List<VacuumRawMetrics> findTablesWithHighDeadTuples(
            @Param("databaseId") Long databaseId,
            @Param("threshold") Long threshold
    );

    // ========== Trend Metrics ==========

    /**
     * Bloat 트렌드 데이터 조회
     */
    List<VacuumTrendMetrics> findBloatTrendData(
            @Param("databaseId") Long databaseId,
            @Param("startDate") OffsetDateTime startDate
    );

    /**
     * Bloat 분포 조회
     */
    List<Map<String, Object>> findBloatDistribution(
            @Param("databaseId") Long databaseId,
            @Param("since") OffsetDateTime since
    );

    /**
     * 총 Bloat 크기 계산
     */
    Long calculateTotalBloat(
            @Param("databaseId") Long databaseId,
            @Param("since") OffsetDateTime since
    );

    /**
     * Critical 테이블 수 조회
     */
    Long countCriticalTables(
            @Param("databaseId") Long databaseId,
            @Param("since") OffsetDateTime since
    );

    /**
     * 일별 메트릭스 조회
     */
    List<VacuumTrendMetrics> findDailyMetrics(
            @Param("databaseId") Long databaseId,
            @Param("startDate") OffsetDateTime startDate
    );

    /**
     * Wraparound 진행률이 높은 테이블 조회
     */
    List<VacuumTrendMetrics> findTablesWithHighWraparoundProgress(
            @Param("databaseId") Long databaseId,
            @Param("threshold") Double threshold
    );

    /**
     * 최신 메트릭스 조회
     */
    List<VacuumTrendMetrics> findLatestMetrics(
            @Param("databaseId") Long databaseId
    );
}