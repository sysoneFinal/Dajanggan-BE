package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.VacuumRiskDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface VacuumRiskMapper {

    // ✅ aggTable 파라미터 추가
    List<VacuumRiskDto.BlockersPerHourRaw> getBlockersPerHour(
            @Param("databaseId") Long databaseId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("aggTable") String aggTable
    );

    List<VacuumRiskDto.TopBloatRaw> getTopBloatTables(
            @Param("databaseId") Long databaseId,
            @Param("limit") int limit,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("aggTable") String aggTable
    );

    List<VacuumRiskDto.VacuumBlockerDetailRaw> getVacuumBlockers(
            @Param("databaseId") Long databaseId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("aggTable") String aggTable
    );

    List<VacuumRiskDto.WraparoundProgressRaw> getWraparoundProgress(
            @Param("databaseId") Long databaseId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("aggTable") String aggTable
    );
}
