package com.dajanggan.domain.engine.hottable.repository;

import com.dajanggan.domain.engine.hottable.domain.HotTableAgg;
import com.dajanggan.domain.engine.hottable.domain.HotTableAgg5m;
import com.dajanggan.domain.engine.hottable.domain.HotTableRaw;
import com.dajanggan.domain.instance.domain.Database;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface HotTableMapper {

    /**
     * Top 테이블 조회 (크기별)
     * @param instanceId 인스턴스 ID
     * @param databaseId 데이터베이스 ID
     * @return Top 5 테이블 (크기별)
     */
    List<Map<String, Object>> selectTopTablesBySize(
            @Param("instanceId") Long instanceId,
            @Param("databaseId") Long databaseId
    );

    /**
     * Top 테이블 조회 (스캔별)
     * @param instanceId 인스턴스 ID
     * @param databaseId 데이터베이스 ID
     * @return Top 5 테이블 (스캔별)
     */
    List<Map<String, Object>> selectTopTablesByScan(
            @Param("instanceId") Long instanceId,
            @Param("databaseId") Long databaseId
    );

    /**
     * Top 테이블 조회 (Bloat별)
     * @param instanceId 인스턴스 ID
     * @param databaseId 데이터베이스 ID
     * @return Top 5 테이블 (Bloat별)
     */
    List<Map<String, Object>> selectTopTablesByBloat(
            @Param("instanceId") Long instanceId,
            @Param("databaseId") Long databaseId
    );

    /**
     * 테이블 활동 시계열 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param databaseId 데이터베이스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 테이블 활동 시계열 데이터
     */
    List<Map<String, Object>> selectTableActivityTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("databaseId") Long databaseId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * 캐시 히트율 시계열 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param databaseId 데이터베이스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 캐시 히트율 시계열 데이터
     */
    List<Map<String, Object>> selectCacheHitRatioTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("databaseId") Long databaseId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * Bloat 상태 조회
     * @param instanceId 인스턴스 ID
     * @param databaseId 데이터베이스 ID
     * @return Bloat 상태 데이터
     */
    List<Map<String, Object>> selectBloatStatus(
            @Param("instanceId") Long instanceId,
            @Param("databaseId") Long databaseId
    );

    /**
     * Vacuum 상태 조회
     * @param instanceId 인스턴스 ID
     * @param databaseId 데이터베이스 ID
     * @return Vacuum 상태 데이터
     */
    List<Map<String, Object>> selectVacuumStatus(
            @Param("instanceId") Long instanceId,
            @Param("databaseId") Long databaseId
    );

    /**
     * 최근 통계 조회
     * @param instanceId 인스턴스 ID
     * @param databaseId 데이터베이스 ID
     * @return 최근 통계 데이터
     */
    Map<String, Object> selectRecentStats(
            @Param("instanceId") Long instanceId,
            @Param("databaseId") Long databaseId
    );

    /**
     * HotTable 리스트 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param databaseId 데이터베이스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @param statusList 상태 필터 리스트
     * @return HotTable 리스트 데이터
     */
    List<Map<String, Object>> selectHotTableList(
            @Param("instanceId") Long instanceId,
            @Param("databaseId") Long databaseId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("statusList") List<String> statusList
    );

    // ========== 데이터 수집용 메서드 ==========

    /**
     * 활성화된 데이터베이스 목록 조회
     * @return 데이터베이스 리스트
     */
    List<Database> selectActiveDatabases();

    /**
     * 특정 테이블의 이전 Raw 데이터 조회 (증분 계산용)
     * @param databaseId 데이터베이스 ID
     * @param schemaName 스키마명
     * @param tableName 테이블명
     * @return 이전 Raw 데이터
     */
    HotTableRaw selectPreviousRawByTable(
            @Param("databaseId") Long databaseId,
            @Param("schemaName") String schemaName,
            @Param("tableName") String tableName
    );

    /**
     * Raw 데이터 일괄 삽입
     * @param rawList Raw 데이터 리스트
     */
    void insertRawBatch(List<HotTableRaw> rawList);

    /**
     * Agg 데이터 일괄 삽입
     * @param aggList Agg 데이터 리스트
     */
    void insertAggBatch(List<HotTableAgg> aggList);

    /**
     * 5분 집계를 위한 1분 집계 데이터 조회
     * @param databaseId 데이터베이스 ID
     * @param schemaName 스키마명
     * @param tableName 테이블명
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 1분 집계 데이터 리스트
     */
    List<HotTableAgg> selectPreviousAgg1m(
            @Param("databaseId") Long databaseId,
            @Param("schemaName") String schemaName,
            @Param("tableName") String tableName,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * 5분 집계 데이터 삽입
     * @param agg5m 5분 집계 데이터
     */
    void insertAgg5m(HotTableAgg5m agg5m);
}