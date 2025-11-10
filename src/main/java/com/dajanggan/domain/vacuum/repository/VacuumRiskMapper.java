package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.VacuumRiskDto;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface VacuumRiskMapper {

    List<VacuumRiskDto.BlockersPerHourRaw> getBlockersPerHour(
            @Param("databaseId") Long databaseId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    List<VacuumRiskDto.TopBloatRaw> getTopBloatTables(
            @Param("databaseId") Long databaseId,
            @Param("limit") int limit,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    List<VacuumRiskDto.VacuumBlockerDetailRaw> getVacuumBlockers(
            @Param("databaseId") Long databaseId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    List<VacuumRiskDto.WraparoundProgressRaw> getWraparoundProgress(
            @Param("databaseId") Long databaseId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );
}
