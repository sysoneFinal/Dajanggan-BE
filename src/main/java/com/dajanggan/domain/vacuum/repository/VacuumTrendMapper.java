package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.VacuumTrendDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vacuum Trend Metrics Mapper
 * - vacuum_trend_metrics 테이블 접근
 */
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

    // 차트 데이터
    List<VacuumTrendDto> getDeadTupleTrend(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("buckets") int buckets
    );

    List<VacuumTrendDto> getAutovacuumTrend(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("buckets") int buckets
    );

    List<VacuumTrendDto> getLatencyTrend(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("buckets") int buckets
    );
}
