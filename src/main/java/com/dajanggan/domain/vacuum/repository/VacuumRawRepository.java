package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.dto.raw.VacuumRawMetricDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Vacuum Raw Metrics Repository
 */
@Mapper
public interface VacuumRawRepository {

    /**
     * Vacuum 메트릭 일괄 저장
     */
    void insertVacuumMetrics(@Param("metrics") List<VacuumRawMetricDto> metrics);
}