package com.dajanggan.domain.system.memory.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface MemoryMapper {

    /**
     * Memory 사용률 조회
     * @param instanceId 인스턴스 ID
     * @return Memory 사용률 데이터
     */
    Map<String, Object> selectMemoryUtilization(@Param("instanceId") Long instanceId);

    /**
     * Buffer Hit 비율 조회
     * @param instanceId 인스턴스 ID
     * @return Buffer Hit 비율 데이터
     */
    Map<String, Object> selectBufferHitRatio(@Param("instanceId") Long instanceId);

    /**
     * Shared Buffer 사용량 조회
     * @param instanceId 인스턴스 ID
     * @return Shared Buffer 사용량 데이터
     */
    Map<String, Object> selectSharedBufferUsage(@Param("instanceId") Long instanceId);

    /**
     * Eviction Rate 시계열 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return Eviction Rate 시계열 데이터
     */
    List<Map<String, Object>> selectEvictionRateTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * Fsync Rate 시계열 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return Fsync Rate 시계열 데이터
     */
    List<Map<String, Object>> selectFsyncRateTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * Dirty Buffer 추세 시계열 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return Dirty Buffer 추세 시계열 데이터
     */
    List<Map<String, Object>> selectDirtyBufferTrendTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * Eviction vs Flush 비교 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return Eviction vs Flush 시계열 데이터
     */
    List<Map<String, Object>> selectEvictionFlushRatioTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * 상위 버퍼 사용 객체 조회
     * @param instanceId 인스턴스 ID
     * @param limit 조회 개수
     * @return 상위 버퍼 사용 객체 리스트
     */
    List<Map<String, Object>> selectTopBufferObjects(
            @Param("instanceId") Long instanceId,
            @Param("limit") int limit
    );

    /**
     * 요약 통계 조회
     * @param instanceId 인스턴스 ID
     * @return 요약 통계 데이터
     */
    Map<String, Object> selectSummaryStats(@Param("instanceId") Long instanceId);

    /**
     * Memory 리스트 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param typeList 타입 필터 리스트
     * @param statusList 상태 필터 리스트
     * @return Memory 리스트 데이터
     */
    List<Map<String, Object>> selectMemoryList(
            @Param("instanceId") Long instanceId,
            @Param("typeList") List<String> typeList,
            @Param("statusList") List<String> statusList
    );
}