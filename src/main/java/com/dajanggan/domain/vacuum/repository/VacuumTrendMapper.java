package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.VacuumMaintenanceDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface VacuumTrendMapper {

    // KPI 지표
    Double getAvgDelaySeconds(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    Double getAvgVacuumDuration(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    Long getTotalDeadTuples(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    // 차트 데이터 - VacuumTrendRaw 반환
    List<VacuumMaintenanceDto.VacuumTrendRaw> getDeadTupleTrend(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("buckets") int buckets
    );

    List<VacuumMaintenanceDto.VacuumTrendRaw> getAutovacuumTrend(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("buckets") int buckets
    );

    List<VacuumMaintenanceDto.VacuumTrendRaw> getLatencyTrend(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("buckets") int buckets
    );
}