package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.domain.VacuumRawMetrics;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface VacuumRawMetricsMapper {

    /**
     * 특정 시간 범위의 Xmin Horizon 데이터 조회
     */
    List<VacuumRawMetrics> findXminHorizonData(
            @Param("databaseId") String databaseId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * 최근 데이터 조회 (Xmin Horizon Age 계산용)
     */
    List<VacuumRawMetrics> findRecentMetrics(
            @Param("databaseId") String databaseId,
            @Param("since") LocalDateTime since
    );

    /**
     * Blocker Xmin Horizon 기준으로 최신 데이터 조회
     */
    List<VacuumRawMetrics> findLatestWithBlockerXmin(@Param("databaseId") String databaseId);

    /**
     * Dead Tuple 카운트가 높은 테이블 조회
     */
    List<VacuumRawMetrics> findTablesWithHighDeadTuples(
            @Param("databaseId") String databaseId,
            @Param("threshold") Long threshold
    );
}
