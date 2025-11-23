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

    /**
     * 특정 테이블의 마지막 vacuum 세션 duration 조회
     * (session_started_at과 collected_at의 차이 또는 elapsed_seconds)
     */
    Integer findLastVacuumDuration(
            @Param("databaseId") Long databaseId,
            @Param("tableName") String tableName
    );
}