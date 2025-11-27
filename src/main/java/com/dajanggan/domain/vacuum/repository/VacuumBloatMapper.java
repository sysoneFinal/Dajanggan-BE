package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.agg.VacuumAgg5mDto;
import com.dajanggan.domain.vacuum.dto.raw.VacuumRawMetricDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * VacuumBloat Mapper
 *
 * 테이블: vacuum_raw_metrics, vacuum_agg_5m
 *
 * 주요 책임:
 * - Bloat 데이터 조회
 * - Xmin Horizon 조회
 * - 테이블별 Bloat 추이 조회
 *
 *
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-18  김민서    1. 최초작성
 */
@Mapper
public interface VacuumBloatMapper {

    // ========================================================================
    // Raw 데이터 조회
    // ========================================================================

    /**
     * Xmin Horizon 데이터 조회
     *
     * 용도: Xmin Horizon Monitor 차트
     *
     * @param databaseId 데이터베이스 ID
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return Xmin Horizon 데이터 목록
     */
    List<VacuumRawMetricDto> findXminHorizonData(
            @Param("databaseId") Long databaseId,
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * 테이블 목록 조회
     *
     * 용도: 테이블 선택 드롭다운
     *
     * @param databaseId 데이터베이스 ID
     * @param instanceId 인스턴스 ID
     * @return 테이블명 목록
     */
    List<String> findTableList(
            @Param("databaseId") Long databaseId,
            @Param("instanceId") Long instanceId
    );

    /**
     * 특정 테이블의 최신 메트릭 조회
     *
     * 용도: KPI 데이터
     *
     * @param databaseId 데이터베이스 ID
     * @param instanceId 인스턴스 ID
     * @param tableName 테이블명
     * @return 최신 메트릭 (없으면 null)
     */
    VacuumRawMetricDto findLatestByTable(
            @Param("databaseId") Long databaseId,
            @Param("instanceId") Long instanceId,
            @Param("tableName") String tableName
    );

    /**
     * 특정 테이블의 Bloat 추이 조회
     *
     * 용도: Bloat Trend 차트
     *
     * @param databaseId 데이터베이스 ID
     * @param instanceId 인스턴스 ID
     * @param tableName 테이블명
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return Bloat 추이 데이터
     */
    List<VacuumRawMetricDto> findTableBloatTrend(
            @Param("databaseId") Long databaseId,
            @Param("instanceId") Long instanceId,
            @Param("tableName") String tableName,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * Index Bloat 데이터 조회
     *
     * 용도: Index Bloat Trend 차트 (30일)
     *
     * @param databaseId 데이터베이스 ID
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return Index Bloat 데이터
     */
    List<VacuumRawMetricDto> findIndexBloatData(
            @Param("databaseId") Long databaseId,
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    // ========================================================================
    // 집계 데이터 조회 (5분 집계)
    // ========================================================================

    /**
     * Bloat KPI 집계
     *
     * 용도: 대시보드 KPI (평균값)
     *
     * @param databaseId 데이터베이스 ID
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return KPI 집계 데이터
     */
    VacuumAgg5mDto getBloatKpiSummary(
            @Param("databaseId") Long databaseId,
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * Bloat Trend 조회 (집계 데이터)
     *
     * 용도: Bloat Trend 차트
     *
     * @param databaseId 데이터베이스 ID
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return Bloat 추이 집계 데이터
     */
    List<VacuumAgg5mDto> getBloatTrend(
            @Param("databaseId") Long databaseId,
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );
}
