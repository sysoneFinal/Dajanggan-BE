package com.dajanggan.domain.engine.hottable.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface HotTableMapper {

    /**
     * Top 테이블 조회 (크기별)
     * @param databaseId 데이터베이스 ID
     * @return Top 5 테이블 (크기별)
     */
    List<Map<String, Object>> selectTopTablesBySize(@Param("databaseId") Long databaseId);

    /**
     * Top 테이블 조회 (스캔별)
     * @param databaseId 데이터베이스 ID
     * @return Top 5 테이블 (스캔별)
     */
    List<Map<String, Object>> selectTopTablesByScan(@Param("databaseId") Long databaseId);

    /**
     * Top 테이블 조회 (Bloat별)
     * @param databaseId 데이터베이스 ID
     * @return Top 5 테이블 (Bloat별)
     */
    List<Map<String, Object>> selectTopTablesByBloat(@Param("databaseId") Long databaseId);

    /**
     * 테이블 활동 시계열 데이터 조회
     * @param databaseId 데이터베이스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 테이블 활동 시계열 데이터
     */
    List<Map<String, Object>> selectTableActivityTimeSeries(
            @Param("databaseId") Long databaseId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * 캐시 히트율 시계열 데이터 조회
     * @param databaseId 데이터베이스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 캐시 히트율 시계열 데이터
     */
    List<Map<String, Object>> selectCacheHitRatioTimeSeries(
            @Param("databaseId") Long databaseId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * Bloat 상태 조회
     * @param databaseId 데이터베이스 ID
     * @return Bloat 상태 데이터
     */
    List<Map<String, Object>> selectBloatStatus(@Param("databaseId") Long databaseId);

    /**
     * Vacuum 상태 조회
     * @param databaseId 데이터베이스 ID
     * @return Vacuum 상태 데이터
     */
    List<Map<String, Object>> selectVacuumStatus(@Param("databaseId") Long databaseId);

    /**
     * 최근 통계 조회
     * @param databaseId 데이터베이스 ID
     * @return 최근 통계 데이터
     */
    Map<String, Object> selectRecentStats(@Param("databaseId") Long databaseId);

    /**
     * HotTable 리스트 데이터 조회
     * @param databaseId 데이터베이스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @param statusList 상태 필터 리스트
     * @return HotTable 리스트 데이터
     */
    List<Map<String, Object>> selectHotTableList(
            @Param("databaseId") Long databaseId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("statusList") List<String> statusList
    );
}