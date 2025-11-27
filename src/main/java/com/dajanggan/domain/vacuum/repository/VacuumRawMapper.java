// 작성자: 김민서
package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.VacuumMaintenanceDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VacuumRawMapper {
    List<VacuumMaintenanceDto.VacuumSessionRaw> getCurrentVacuumSessions(
            @Param("databaseId") Long databaseId,
            @Param("tableName") String tableName
    );

}