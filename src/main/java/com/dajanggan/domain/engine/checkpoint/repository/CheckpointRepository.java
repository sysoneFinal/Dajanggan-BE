package com.dajanggan.domain.engine.checkpoint.repository;

import com.dajanggan.domain.engine.checkpoint.domain.CheckpointRaw;
import com.dajanggan.domain.engine.checkpoint.dto.CheckpointRawDto;
import com.dajanggan.domain.engine.checkpoint.dto.TimeSeriesDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Checkpoint Repository
 * - MyBatis Mapper 인터페이스
 */
@Mapper
public interface CheckpointRepository {

    /**
     * pg_stat_bgwriter에서 현재 Checkpoint 통계 조회
     * - 배치에서 1분마다 호출
     */
    Map<String, Object> selectCurrentBgwriterStats(@Param("instanceId") Long instanceId);

    /**
     * Checkpoint Raw 데이터 삽입
     */
    void insertCheckpointRaw(CheckpointRaw checkpointRaw);

    /**
     * 최근 24시간 시계열 데이터 조회 (2시간 단위 집계)
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     */
    List<TimeSeriesDto> selectTimeSeriesData(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * 최근 5분 평균 통계 조회
     */
    CheckpointRawDto selectRecentStats(
            @Param("instanceId") Long instanceId,
            @Param("minutes") int minutes
    );

    /**
     * 최근 24시간 전체 통계 조회
     */
    CheckpointRawDto select24HourStats(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * 이전 수집 시점의 통계 조회 (diff 계산용)
     */
    CheckpointRawDto selectPreviousStats(
            @Param("instanceId") Long instanceId,
            @Param("minutes") int minutes
    );

    /**
     * 최근 Checkpoint Raw 데이터 1건 조회
     * - 배치에서 차이값 계산 시 사용
     */
    CheckpointRaw selectLatestCheckpointRaw(@Param("instanceId") Long instanceId);

    /**
     * Checkpoint 리스트 조회 (필터링 + 페이징)
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @param types 타입 필터 (timed, requested)
     * @param offset 페이징 오프셋
     * @param limit 페이징 리밋
     */
    List<CheckpointRawDto> selectCheckpointList(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("types") List<String> types,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit
    );

    /**
     * Checkpoint 리스트 총 개수 조회 (필터링)
     */
    Long countCheckpointList(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("types") List<String> types
    );

}
