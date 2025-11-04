package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vacuum Risk Mapper
 */
@Mapper
public interface VacuumRiskMapper {

    /**
     * Blockers per Hour 조회 (24시간)
     */
    List<BlockersPerHourRawDto> getBlockersPerHour(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("buckets") int buckets
    );

    /**
     * Top-3 Bloat Tables 조회
     */
    List<TopBloatRawDto> getTopBloatTables(@Param("limit") int limit);

    /**
     * Vacuum Blockers 조회
     */
    List<VacuumBlockerDetailRawDto> getVacuumBlockers();

    /**
     * Wraparound Progress 조회
     */
    List<WraparoundProgressRawDto> getWraparoundProgress();
}