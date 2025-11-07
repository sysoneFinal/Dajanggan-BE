package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.VacuumMaintenanceDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VacuumRawMapper {

    /**
     * 최대 Autovacuum Worker 수 조회
     */
    Integer getMaxWorkers();

    /**
     * 현재 활성 Worker 수 조회
     */
    Integer getActiveWorkers();

    /**
     * 현재 실행 중인 Vacuum 세션 조회
     */
    List<VacuumMaintenanceDto.VacuumSessionRaw> getCurrentVacuumSessions();

    /**
     * 특정 세션의 진행률 히스토리 조회
     * @param databaseId 데이터베이스 ID
     * @param limit 최대 조회 개수
     */
    List<Integer> getSessionProgressHistory(
            @Param("databaseId") String databaseId,
            @Param("limit") int limit
    );
}