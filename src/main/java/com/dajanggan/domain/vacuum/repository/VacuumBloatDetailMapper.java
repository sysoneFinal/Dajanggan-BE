package com.dajanggan.domain.vacuum.repository;

import com.dajanggan.domain.vacuum.domain.VacuumTrendMetrics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface VacuumBloatDetailMapper {

    /**
     * 특정 테이블의 최신 메트릭 조회
     */
    VacuumTrendMetrics findLatestMetricsByTable(
            @Param("databaseId") Long databaseId,
            @Param("tableName") String tableName
    );

    /**
     * 특정 테이블의 Bloat % 트렌드 조회 (30일)
     */
    List<VacuumTrendMetrics> findBloatTrendByTable(
            @Param("databaseId") Long databaseId,
            @Param("tableName") String tableName,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * 특정 테이블의 Dead Tuples 트렌드 조회 (30일)
     */
    List<VacuumTrendMetrics> findDeadTuplesTrendByTable(
            @Param("databaseId") Long databaseId,
            @Param("tableName") String tableName,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * 특정 테이블의 인덱스별 Bloat 트렌드 조회 (30일)
     *
     * 반환 Map 구조:
     * - index_name: String
     * - bloat_ratio: Double
     * - collected_at: LocalDateTime
     */
    List<Map<String, Object>> findIndexBloatTrendByTable(
            @Param("databaseId") Long databaseId,
            @Param("tableName") String tableName,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * 데이터베이스 내 테이블 목록 조회
     */
    List<String> findTableList(@Param("databaseId") Long databaseId);
}