package com.dajanggan.domain.engine.bgwriter.repository;

import com.dajanggan.domain.engine.bgwriter.domain.BgWriterAgg;
import com.dajanggan.domain.engine.bgwriter.domain.BgWriterRaw;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface BgWriterMapper {

    /**
     * Backend Flush 비율 조회
     * @param instanceId 인스턴스 ID
     * @return Backend Flush 비율 데이터
     */
    Map<String, Object> selectBackendFlushRatio(@Param("instanceId") Long instanceId);

    /**
     * Clean Rate 시계열 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return Clean Rate 시계열 데이터
     */
    List<Map<String, Object>> selectCleanRateTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * Buffer Flush 비율 시계열 데이터 조회 (Backend vs Clean)
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return Buffer Flush 비율 시계열 데이터
     */
    List<Map<String, Object>> selectBufferFlushRatioTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * Maxwritten Clean 시계열 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return Maxwritten Clean 시계열 데이터
     */
    List<Map<String, Object>> selectMaxwrittenCleanTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * BGWriter vs Checkpoint 비교 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return BGWriter vs Checkpoint 시계열 데이터
     */
    List<Map<String, Object>> selectBgwriterVsCheckpointTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * Buffer 재사용률 시계열 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return Buffer 재사용률 시계열 데이터
     */
    List<Map<String, Object>> selectBufferReuseRateTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * 최근 통계 조회
     * @param instanceId 인스턴스 ID
     * @return 최근 통계 데이터
     */
    Map<String, Object> selectRecentStats(@Param("instanceId") Long instanceId);

    /**
     * BGWriter 리스트 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @param statusList 상태 필터 리스트
     * @return BGWriter 리스트 데이터
     */
    List<Map<String, Object>> selectBgWriterList(
            @Param("instanceId") Long instanceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("statusList") List<String> statusList
    );

    // ========== 데이터 수집용 메서드 ==========

    /**
     * 활성화된 인스턴스 ID 목록 조회
     * @return 인스턴스 ID 리스트
     */
    List<Long> selectActiveInstanceIds();

    /**
     * 이전 Raw 데이터 조회 (증분 계산용)
     * @param instanceId 인스턴스 ID
     * @return 이전 Raw 데이터
     */
    BgWriterRaw selectPreviousRaw(@Param("instanceId") Long instanceId);

    /**
     * Raw 데이터 삽입
     * @param raw Raw 데이터
     */
    void insertRaw(BgWriterRaw raw);

    /**
     * Agg 데이터 삽입
     * @param agg Agg 데이터
     */
    void insertAgg(BgWriterAgg agg);
}
