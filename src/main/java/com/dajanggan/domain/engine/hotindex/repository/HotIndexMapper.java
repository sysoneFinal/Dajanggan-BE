package com.dajanggan.domain.engine.hotindex.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
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
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
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
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
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
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
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
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("statusList") List<String> statusList
    );
}