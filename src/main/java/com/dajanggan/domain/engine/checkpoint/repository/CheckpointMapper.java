package com.dajanggan.domain.engine.checkpoint.repository;

import com.dajanggan.domain.engine.checkpoint.domain.CheckpointAgg1m;
import com.dajanggan.domain.engine.checkpoint.domain.CheckpointAgg5m;
import com.dajanggan.domain.engine.checkpoint.domain.CheckpointRaw;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface CheckpointMapper {

    // ========== 조회 메서드 (시간 범위에 따라 자동으로 1m/5m 테이블 선택) ==========

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
     * @param intervalMinutes 집계 간격 (1 또는 5)
     * @return 평균 쓰기 시간 시계열 데이터
     */
    List<Map<String, Object>> selectAvgWriteTimeTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("intervalMinutes") int intervalMinutes
    );

    /**
     * 체크포인트 발생 횟수 시계열 데이터 조회 (Requested vs Timed)
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @param intervalMinutes 집계 간격 (1 또는 5)
     * @return 체크포인트 발생 횟수 시계열 데이터
     */
    List<Map<String, Object>> selectOccurrenceTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("intervalMinutes") int intervalMinutes
    );

    /**
     * WAL 생성량 시계열 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @param intervalMinutes 집계 간격 (1 또는 5)
     * @return WAL 생성량 시계열 데이터
     */
    List<Map<String, Object>> selectWalGenerationTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("intervalMinutes") int intervalMinutes
    );

    /**
     * 처리 시간 시계열 데이터 조회 (Sync + Write)
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @param intervalMinutes 집계 간격 (1 또는 5)
     * @return 처리 시간 시계열 데이터
     */
    List<Map<String, Object>> selectProcessTimeTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("intervalMinutes") int intervalMinutes
    );

    /**
     * 버퍼 처리량 시계열 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @param intervalMinutes 집계 간격 (1 또는 5)
     * @return 버퍼 처리량 시계열 데이터
     */
    List<Map<String, Object>> selectBufferTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("intervalMinutes") int intervalMinutes
    );

    /**
     * 체크포인트 간격 시계열 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @param intervalMinutes 집계 간격 (1 또는 5)
     * @return 체크포인트 간격 시계열 데이터
     */
    List<Map<String, Object>> selectCheckpointIntervalTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("intervalMinutes") int intervalMinutes
    );

    /**
     * 최근 통계 조회 (1분 집계 테이블 사용)
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
     * @param intervalMinutes 집계 간격 (1 또는 5)
     * @return Checkpoint 리스트 데이터
     */
    List<Map<String, Object>> selectCheckpointList(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("statusList") List<String> statusList,
            @Param("typeList") List<String> typeList,
            @Param("intervalMinutes") int intervalMinutes
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
     * 이전 1분 집계 데이터 조회 (5분 집계 계산용)
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 1분 집계 데이터 리스트
     */
    List<CheckpointAgg1m> selectPreviousAgg1m(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * Raw 데이터 삽입
     * @param raw Raw 데이터
     */
    void insertRaw(CheckpointRaw raw);

    /**
     * 1분 집계 데이터 삽입
     * @param agg1m 1분 집계 데이터
     */
    void insertAgg1m(CheckpointAgg1m agg1m);

    /**
     * 5분 집계 데이터 삽입
     * @param agg5m 5분 집계 데이터
     */
    void insertAgg5m(CheckpointAgg5m agg5m);
}
