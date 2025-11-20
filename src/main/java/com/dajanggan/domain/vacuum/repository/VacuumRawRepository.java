package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.raw.VacuumRawMetricDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * Vacuum Raw Metrics Repository
 */
@Mapper
public interface VacuumRawRepository {

    /**
     * Vacuum 메트릭 일괄 저장
     */
    void insertVacuumMetrics(@Param("metrics") List<VacuumRawMetricDto> metrics);

    /**
     * 이전 수집 시점의 last_vacuum/last_autovacuum 값 조회
     * (테이블별로 가장 최근 수집된 값)
     */
    List<Map<String, Object>> findPreviousVacuumTimes(
            @Param("databaseId") Long databaseId
    );
}