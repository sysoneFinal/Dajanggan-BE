package com.dajanggan.domain.engine.checkpoint.repository;

import com.dajanggan.domain.engine.checkpoint.domain.CheckpointAgg;
import com.dajanggan.domain.engine.checkpoint.domain.CheckpointRaw;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface CheckpointMapper {

    /**
     * 요청형 체크포인트 비율 조회
     * @param instanceId 인스턴스 ID
     * @return 요청형 체크포인트 비율 데이터
     */
    Map<String, Object> selectRequestRatio(@Param("instanceId") Long instanceId);

    /**
     * 평균 쓰기 시간 시계열 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 평균 쓰기 시간 시계열 데이터
     */
    List<Map<String, Object>> selectAvgWriteTimeTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * 체크포인트 발생 횟수 시계열 데이터 조회 (Requested vs Timed)
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 체크포인트 발생 횟수 시계열 데이터
     */
    List<Map<String, Object>> selectOccurrenceTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * WAL 생성량 시계열 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return WAL 생성량 시계열 데이터
     */
    List<Map<String, Object>> selectWalGenerationTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * 처리 시간 시계열 데이터 조회 (Sync + Write)
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 처리 시간 시계열 데이터
     */
    List<Map<String, Object>> selectProcessTimeTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * 버퍼 처리량 시계열 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 버퍼 처리량 시계열 데이터
     */
    List<Map<String, Object>> selectBufferTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * 체크포인트 간격 시계열 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 체크포인트 간격 시계열 데이터
     */
    List<Map<String, Object>> selectCheckpointIntervalTimeSeries(
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
     * Checkpoint 리스트 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @param statusList 상태 필터 리스트
     * @return Checkpoint 리스트 데이터
     */
    List<Map<String, Object>> selectCheckpointList(
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
    CheckpointRaw selectPreviousRaw(@Param("instanceId") Long instanceId);

    /**
     * Raw 데이터 삽입
     * @param raw Raw 데이터
     */
    void insertRaw(CheckpointRaw raw);

    /**
     * Agg 데이터 삽입
     * @param agg Agg 데이터
     */
    void insertAgg(CheckpointAgg agg);
}
