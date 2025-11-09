package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.VacuumMaintenanceDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VacuumRawMapper {

    Integer getMaxWorkers(
            @Param("databaseId") Long databaseId
    );

    Integer getActiveWorkers(
            @Param("databaseId") Long databaseId
    );

    List<VacuumMaintenanceDto.VacuumSessionRaw> getCurrentVacuumSessions(
            @Param("databaseId") Long databaseId
    );

    List<Integer> getSessionProgressHistory(
            @Param("databaseId") String databaseId,
            @Param("limit") int limit
    );
}