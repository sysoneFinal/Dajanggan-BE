package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.VacuumRiskDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Vacuum Risk Mapper
 */
@Mapper
public interface VacuumRiskMapper {

    /**
     * Blockers per Hour 조회 (24시간)
     */
    List<VacuumRiskDto.BlockersPerHourRaw> getBlockersPerHour(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("buckets") int buckets
    );

    /**
     * Top-N Bloat Tables 조회
     */
    List<VacuumRiskDto.TopBloatRaw> getTopBloatTables(@Param("limit") int limit);

    /**
     * Vacuum Blockers 조회
     */
    List<VacuumRiskDto.VacuumBlockerDetailRaw> getVacuumBlockers();

    /**
     * Wraparound Progress 조회
     */
    List<VacuumRiskDto.WraparoundProgressRaw> getWraparoundProgress();
}