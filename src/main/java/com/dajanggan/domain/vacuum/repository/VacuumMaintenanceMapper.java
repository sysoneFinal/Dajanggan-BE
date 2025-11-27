// 작성자: 김민서
package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.VacuumMaintenanceDto;
import com.dajanggan.domain.vacuum.dto.agg.VacuumAgg1mDto;
import com.dajanggan.domain.vacuum.dto.agg.VacuumAgg5mDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
@Repository
public interface VacuumMaintenanceMapper {

    // ========== 1분 집계 (KPI) ==========

    /**
     * 1분 집계 KPI 요약
     */
    VacuumAgg1mDto getKpiFrom1m(
            @Param("databaseId") Long databaseId,
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * 1분 집계 시계열 데이터
     */
    List<VacuumAgg1mDto> getTimeSeriesFrom1m(
            @Param("databaseId") Long databaseId,
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    // ========== 5분 집계 (KPI) ==========

    /**
     * 5분 집계 KPI 요약
     */
    VacuumAgg5mDto getKpiFrom5m(
            @Param("databaseId") Long databaseId,
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * 5분 집계 시계열 데이터
     */
    List<VacuumAgg5mDto> getTimeSeriesFrom5m(
            @Param("databaseId") Long databaseId,
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    // ========== 현재 세션 조회 (Raw) ==========

    /**
     * 현재 실행 중인 Vacuum 세션
     */
    List<VacuumMaintenanceDto.VacuumSessionRaw> getCurrentVacuumSessions(
            @Param("databaseId") Long databaseId,
            @Param("tableName") String tableName
    );

    // ========== 집계 데이터 삽입 (스케줄러용) ==========

    /**
     * 1분 집계 삽입
     */
    int insertAgg1m(
            @Param("databaseId") Long databaseId,
            @Param("instanceId") Long instanceId
    );

    /**
     * 5분 집계 삽입
     */
    int insertAgg5m(VacuumAgg5mDto dto);

    /**
     * 5분 집계 배치 삽입
     */
    int insertAgg5mBatch(@Param("list") List<VacuumAgg5mDto> dtos);

    /**
     * Bloat KPI 요약
     */
    VacuumAgg5mDto getBloatKpiSummary(
            @Param("databaseId") Long databaseId,
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );
}
