package com.dajanggan.domain.system.cpu.repository;

import com.dajanggan.domain.system.cpu.domain.CpuAgg;
import com.dajanggan.domain.system.cpu.domain.CpuRaw;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface CpuMapper {

    /**
     * CPU Raw 데이터 조회 (시간 범위)
     */
    List<CpuRaw> selectCpuRawByTimeRange(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * CPU Agg 데이터 조회 (시간 범위)
     */
    List<CpuAgg> selectCpuAggByTimeRange(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * CPU 리스트 조회 (필터링)
     */
    List<CpuAgg> selectCpuListWithFilter(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("statusList") List<String> statusList
    );

    /**
     * 최근 CPU 통계 조회
     */
    CpuAgg selectRecentCpuStats(@Param("instanceId") Long instanceId);

    /**
     * CPU Raw 데이터 삽입
     */
    void insertCpuRaw(CpuRaw cpuRaw);

    /**
     * CPU Agg 데이터 삽입
     */
    void insertCpuAgg(CpuAgg cpuAgg);
}