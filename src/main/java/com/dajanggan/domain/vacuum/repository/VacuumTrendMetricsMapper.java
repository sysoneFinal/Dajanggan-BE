package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.domain.VacuumTrendMetrics;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface VacuumTrendMetricsMapper {

    /**
     * 최근 N일간의 Bloat Trend 데이터 조회
     */
    List<VacuumTrendMetrics> findBloatTrendData(
            @Param("databaseId") String databaseId,
            @Param("startDate") LocalDateTime startDate
    );

    /**
     * Bloat Ratio 기준 테이블 분포 조회
     */
    List<Map<String, Object>> findBloatDistribution(
            @Param("databaseId") String databaseId,
            @Param("since") LocalDateTime since
    );

    /**
     * 총 Bloat 크기 계산
     */
    Long calculateTotalBloat(
            @Param("databaseId") String databaseId,
            @Param("since") LocalDateTime since
    );

    /**
     * Critical 테이블 수 조회 (bloatRatio >= 0.15)
     */
    Long countCriticalTables(
            @Param("databaseId") String databaseId,
            @Param("since") LocalDateTime since
    );

    /**
     * 최근 데이터 조회 (일별 집계)
     */
    List<VacuumTrendMetrics> findDailyMetrics(
            @Param("databaseId") String databaseId,
            @Param("startDate") LocalDateTime startDate
    );

    /**
     * Wraparound 진행률이 높은 테이블 조회
     */
    List<VacuumTrendMetrics> findTablesWithHighWraparoundProgress(
            @Param("databaseId") String databaseId,
            @Param("threshold") Double threshold
    );

    /**
     * 최신 데이터만 조회 (데이터베이스별 최신 레코드)
     */
    List<VacuumTrendMetrics> findLatestMetrics(
            @Param("databaseId") String databaseId
    );
}