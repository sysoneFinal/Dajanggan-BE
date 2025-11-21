package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.agg.VacuumAgg5mDto;
import com.dajanggan.domain.vacuum.dto.raw.VacuumRawMetricDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface VacuumBloatMapper {

    // ========== Raw 데이터 ==========

    /**
     * Xmin Horizon 데이터 조회 (시간별)
     */
    List<VacuumRawMetricDto> findXminHorizonData(
            @Param("databaseId") Long databaseId,
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * 테이블 목록 조회
     */
    List<String> findTableList(
            @Param("databaseId") Long databaseId,
            @Param("instanceId") Long instanceId
    );

    /**
     * 특정 테이블의 최신 메트릭
     */
    VacuumRawMetricDto findLatestByTable(
            @Param("databaseId") Long databaseId,
            @Param("instanceId") Long instanceId,
            @Param("tableName") String tableName
    );

    /**
     * 특정 테이블의 Bloat 트렌드
     */
    List<VacuumRawMetricDto> findTableBloatTrend(
            @Param("databaseId") Long databaseId,
            @Param("instanceId") Long instanceId,
            @Param("tableName") String tableName,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * Index Bloat 데이터 조회 (30일 추이용)
     */
    List<VacuumRawMetricDto> findIndexBloatData(
            @Param("databaseId") Long databaseId,
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    // ========== 집계 데이터 (5분 집계) ==========
    /**
     * Bloat KPI 집계
     */
    VacuumAgg5mDto getBloatKpiSummary(
            @Param("databaseId") Long databaseId,
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * Bloat Trend (집계 데이터)
     */
    List<VacuumAgg5mDto> getBloatTrend(
            @Param("databaseId") Long databaseId,
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );
}

