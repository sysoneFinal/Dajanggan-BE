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

    // Raw 데이터 (변경 없음)
    List<VacuumRawMetrics> findXminHorizonData(
            @Param("databaseId") Long databaseId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    List<VacuumRawMetrics> findRecentMetrics(
            @Param("databaseId") Long databaseId,
            @Param("since") OffsetDateTime since
    );

    List<VacuumRawMetrics> findLatestWithBlockerXmin(
            @Param("databaseId") Long databaseId
    );

    List<VacuumRawMetrics> findTablesWithHighDeadTuples(
            @Param("databaseId") Long databaseId,
            @Param("threshold") Long threshold
    );

    // ✅ Trend 데이터 (aggTable 추가)
    List<VacuumTrendMetrics> findBloatTrendData(
            @Param("databaseId") Long databaseId,
            @Param("startDate") OffsetDateTime startDate,
            @Param("aggTable") String aggTable
    );

    List<Map<String, Object>> findBloatDistribution(
            @Param("databaseId") Long databaseId,
            @Param("since") OffsetDateTime since,
            @Param("aggTable") String aggTable
    );

    Long calculateTotalBloat(
            @Param("databaseId") Long databaseId,
            @Param("since") OffsetDateTime since,
            @Param("aggTable") String aggTable
    );

    Long countCriticalTables(
            @Param("databaseId") Long databaseId,
            @Param("since") OffsetDateTime since,
            @Param("aggTable") String aggTable
    );

    List<VacuumTrendMetrics> findDailyMetrics(
            @Param("databaseId") Long databaseId,
            @Param("startDate") OffsetDateTime startDate,
            @Param("aggTable") String aggTable
    );

    List<VacuumTrendMetrics> findTablesWithHighWraparoundProgress(
            @Param("databaseId") Long databaseId,
            @Param("threshold") Double threshold,
            @Param("aggTable") String aggTable
    );

    List<VacuumTrendMetrics> findLatestMetrics(
            @Param("databaseId") Long databaseId,
            @Param("aggTable") String aggTable
    );
}
