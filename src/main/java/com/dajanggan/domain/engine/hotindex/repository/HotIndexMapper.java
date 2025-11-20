package com.dajanggan.domain.engine.hotindex.repository;

import com.dajanggan.domain.engine.hotindex.domain.HotIndexAgg;
import com.dajanggan.domain.engine.hotindex.domain.HotIndexAgg5m;
import com.dajanggan.domain.engine.hotindex.domain.HotIndexRaw;
import com.dajanggan.domain.instance.domain.Database;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface HotIndexMapper {

    /**
     * 인덱스 사용 분포 조회
     * @param instanceId 인스턴스 ID
     * @param databaseId 데이터베이스 ID
     * @return 사용 분포 데이터 (사용 중, 미사용, 비효율)
     */
    Map<String, Object> selectUsageDistribution(
            @Param("instanceId") Long instanceId,
            @Param("databaseId") Long databaseId
    );

    /**
     * Top 사용 인덱스 조회 (상위 5개)
     * @param instanceId 인스턴스 ID
     * @param databaseId 데이터베이스 ID
     * @return Top 5 인덱스 (스캔 횟수별)
     */
    List<Map<String, Object>> selectTopUsageIndexes(
            @Param("instanceId") Long instanceId,
            @Param("databaseId") Long databaseId
    );

    /**
     * 비효율 인덱스 Top-5 조회
     * @param instanceId 인스턴스 ID
     * @param databaseId 데이터베이스 ID
     * @return 비효율 인덱스 Top 5
     */
    List<Map<String, Object>> selectInefficientIndexes(
            @Param("instanceId") Long instanceId,
            @Param("databaseId") Long databaseId
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
     * 인덱스 효율성 데이터 조회 (Scatter용)
     * @param instanceId 인스턴스 ID
     * @param databaseId 데이터베이스 ID
     * @return 인덱스별 효율성 데이터
     */
    List<Map<String, Object>> selectIndexEfficiency(
            @Param("instanceId") Long instanceId,
            @Param("databaseId") Long databaseId
    );

    /**
     * 인덱스 접근 추이 시계열 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param databaseId 데이터베이스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 접근 추이 시계열 데이터
     */
    List<Map<String, Object>> selectAccessTrendTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("databaseId") Long databaseId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * 인덱스 스캔 속도 추이 조회
     * @param instanceId 인스턴스 ID
     * @param databaseId 데이터베이스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 스캔 속도 시계열 데이터
     */
    List<Map<String, Object>> selectScanSpeedTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("databaseId") Long databaseId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
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
     * HotIndex 리스트 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param databaseId 데이터베이스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @param statusList 상태 필터 리스트
     * @return HotIndex 리스트 데이터
     */
    List<Map<String, Object>> selectHotIndexList(
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
     * 특정 인덱스의 이전 Raw 데이터 조회 (증분 계산용)
     * @param databaseId 데이터베이스 ID
     * @param schemaName 스키마명
     * @param tableName 테이블명
     * @param indexName 인덱스명
     * @return 이전 Raw 데이터
     */
    HotIndexRaw selectPreviousRawByIndex(
            @Param("databaseId") Long databaseId,
            @Param("schemaName") String schemaName,
            @Param("tableName") String tableName,
            @Param("indexName") String indexName
    );

    /**
     * Raw 데이터 일괄 삽입
     * @param rawList Raw 데이터 리스트
     */
    void insertRawBatch(List<HotIndexRaw> rawList);

    /**
     * Agg 데이터 일괄 삽입
     * @param aggList Agg 데이터 리스트
     */
    void insertAggBatch(List<HotIndexAgg> aggList);

    /**
     * 5분 집계를 위한 1분 집계 데이터 조회
     * @param databaseId 데이터베이스 ID
     * @param schemaName 스키마명
     * @param tableName 테이블명
     * @param indexName 인덱스명
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 1분 집계 데이터 리스트
     */
    List<HotIndexAgg> selectPreviousAgg1m(
            @Param("databaseId") Long databaseId,
            @Param("schemaName") String schemaName,
            @Param("tableName") String tableName,
            @Param("indexName") String indexName,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * 5분 집계 데이터 삽입
     * @param agg5m 5분 집계 데이터
     */
    void insertAgg5m(HotIndexAgg5m agg5m);
}