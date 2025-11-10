package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.VacuumMaintenanceDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface VacuumTrendMapper {

    // KPI 지표
    Double getAvgDelaySeconds(
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("databaseId") Long databaseId
    );

    Double getAvgVacuumDuration(
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("databaseId") Long databaseId
    );

    Long getTotalDeadTuples(
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("databaseId") Long databaseId
    );

    // 차트 데이터
    List<VacuumMaintenanceDto.VacuumTrendRaw> getDeadTupleTrend(
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("buckets") int buckets,
            @Param("databaseId") Long databaseId
    );

    List<VacuumMaintenanceDto.VacuumTrendRaw> getAutovacuumTrend(
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("buckets") int buckets,
            @Param("databaseId") Long databaseId
    );

    List<VacuumMaintenanceDto.VacuumTrendRaw> getLatencyTrend(
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("buckets") int buckets,
            @Param("databaseId") Long databaseId
    );
}