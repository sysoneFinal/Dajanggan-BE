package com.dajanggan.domain.engine.hottable.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface HotTableMapper {

    // instanceId -> databaseId 매핑
    Long selectDatabaseIdByInstanceId(@Param("instanceId") Long instanceId);

    // 캐시 히트율
    Map<String, Object> selectTopCacheHitTable(@Param("databaseId") Long databaseId);

    // vacuum delay 시계열
    List<Map<String, Object>> selectVacuumDelayTimeSeries(@Param("databaseId") Long databaseId,
                                                          @Param("startTime") LocalDateTime startTime,
                                                          @Param("endTime") LocalDateTime endTime);

    // dead tuple 시계열
    List<Map<String, Object>> selectDeadTupleTimeSeries(@Param("databaseId") Long databaseId,
                                                        @Param("startTime") LocalDateTime startTime,
                                                        @Param("endTime") LocalDateTime endTime);

    // 전체 dead tuple
    List<Map<String, Object>> selectTotalDeadTupleSeries(@Param("databaseId") Long databaseId,
                                                         @Param("startTime") LocalDateTime startTime,
                                                         @Param("endTime") LocalDateTime endTime);

    // Top Query
    List<Map<String, Object>> selectTopQueryTables(@Param("databaseId") Long databaseId,
                                                   @Param("startTime") LocalDateTime startTime,
                                                   @Param("endTime") LocalDateTime endTime);

    // Top DML
    List<Map<String, Object>> selectTopDmlTables(@Param("databaseId") Long databaseId,
                                                 @Param("startTime") LocalDateTime startTime,
                                                 @Param("endTime") LocalDateTime endTime);

    // 최근 통계
    Map<String, Object> selectRecentStats(@Param("databaseId") Long databaseId);
}
