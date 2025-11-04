package com.dajanggan.domain.vacuum.repository;


import com.dajanggan.domain.vacuum.dto.VacuumRawDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Vacuum Raw Metrics Mapper
 * - vacuum_raw_metrics 테이블 접근
 */
@Mapper
public interface VacuumRawMapper {

    /**
     * 현재 실행 중인 Vacuum 세션
     */
    List<VacuumRawDto> getCurrentVacuumSessions();

    /**
     * 세션 진행률 히스토리
     */
    List<Integer> getSessionProgressHistory(
            @Param("databaseId") String databaseId,
            @Param("limit") int limit
    );

    /**
     * 최대 Worker 수
     */
    Integer getMaxWorkers();

    /**
     * 현재 활성 Worker 수
     */
    Integer getActiveWorkers();
}
