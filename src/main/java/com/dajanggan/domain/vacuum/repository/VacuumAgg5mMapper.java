package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.agg.VacuumAgg5mDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface VacuumAgg5mMapper {

    /**
     * 5분 집계 데이터 삽입 (스케줄러용)
     */
    int insertAgg5m(
            @Param("databaseId") Long databaseId,
            @Param("instanceId") Long instanceId
    );

    /**
     * 시간 범위로 5분 집계 조회
     */
    List<VacuumAgg5mDto> findByTimeRange(
            @Param("databaseId") Long databaseId,
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * KPI 집계 (평균값)
     */
    VacuumAgg5mDto getKpiSummary(
            @Param("databaseId") Long databaseId,
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    int insertAgg5mBatch(@Param("list") List<? extends VacuumAgg5mDto> items);
}