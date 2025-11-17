package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.VacuumMaintenanceDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface VacuumTrendMapper {

    // ✅ aggTable 파라미터 추가

    Double getAvgDelaySeconds(
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("databaseId") Long databaseId,
            @Param("aggTable") String aggTable
    );

    Double getAvgVacuumDuration(
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("databaseId") Long databaseId,
            @Param("aggTable") String aggTable
    );

    Long getTotalDeadTuples(
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("databaseId") Long databaseId,
            @Param("aggTable") String aggTable
    );

    List<VacuumMaintenanceDto.VacuumTrendRaw> getDeadTupleTrend(
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("buckets") int buckets,
            @Param("databaseId") Long databaseId,
            @Param("aggTable") String aggTable
    );

    List<VacuumMaintenanceDto.VacuumTrendRaw> getAutovacuumTrend(
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("buckets") int buckets,
            @Param("databaseId") Long databaseId,
            @Param("aggTable") String aggTable
    );

    List<VacuumMaintenanceDto.VacuumTrendRaw> getLatencyTrend(
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("buckets") int buckets,
            @Param("databaseId") Long databaseId,
            @Param("aggTable") String aggTable
    );
}