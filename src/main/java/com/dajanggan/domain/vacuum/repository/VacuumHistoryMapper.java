package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.VacuumHistoryDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface VacuumHistoryMapper {

    List<VacuumHistoryDto.Raw> getVacuumHistoryList(
            @Param("databaseId") Long databaseId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    Integer getVacuumFrequency(
            @Param("databaseId") Long databaseId,
            @Param("hours") int hours
    );
}