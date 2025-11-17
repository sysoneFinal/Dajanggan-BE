package com.dajanggan.domain.system.cpu.repository;

import com.dajanggan.domain.system.cpu.domain.CpuAgg;
import com.dajanggan.domain.system.cpu.domain.CpuAgg5m;
import com.dajanggan.domain.system.cpu.domain.CpuAgg30m;
import com.dajanggan.domain.system.cpu.domain.CpuRaw;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface CpuMapper {

    /**
     * 활성 인스턴스 ID 목록 조회
     */
    List<Long> selectActiveInstanceIds();

    /**
     * 이전 Raw 데이터 조회 (가장 최근 1개)
     */
    CpuRaw selectPreviousRaw(@Param("instanceId") Long instanceId);

    /**
     * CPU Raw 데이터 삽입
     */
    void insertRaw(CpuRaw cpuRaw);

    /**
     * CPU Agg 데이터 삽입
     */
    void insertAgg(CpuAgg cpuAgg);

    /**
     * CPU Agg 5분 데이터 삽입
     */
    void insertAgg5m(CpuAgg5m cpuAgg5m);

    /**
     * CPU Agg 30분 데이터 삽입
     */
    void insertAgg30m(CpuAgg30m cpuAgg30m);

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
     * CPU Agg 5분 데이터 조회 (시간 범위)
     */
    List<CpuAgg5m> selectCpuAgg5mByTimeRange(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * CPU Agg 30분 데이터 조회 (시간 범위)
     */
    List<CpuAgg30m> selectCpuAgg30mByTimeRange(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );
}