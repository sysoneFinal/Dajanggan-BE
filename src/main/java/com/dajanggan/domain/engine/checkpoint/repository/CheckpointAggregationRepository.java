package com.dajanggan.domain.engine.checkpoint.repository;

import com.dajanggan.domain.engine.checkpoint.domain.CheckpointAgg;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Checkpoint Aggregation Repository
 * - 집계 데이터 관련 MyBatis Mapper
 */
@Mapper
public interface CheckpointAggregationRepository {

    /**
     * 시간별 집계 데이터 조회
     * - checkpoint_raw에서 지정된 시간 범위의 데이터를 집계
     * 
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 집계 결과
     */
    Map<String, Object> selectHourlyAggregation(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * 집계 데이터 저장
     * 
     * @param checkpointAgg 집계 데이터
     */
    void insertCheckpointAgg(CheckpointAgg checkpointAgg);

    /**
     * 중복 집계 데이터 확인
     * - 같은 시간대에 이미 집계된 데이터가 있는지 확인
     * 
     * @param instanceId 인스턴스 ID
     * @param collectedAt 집계 시간
     * @return 존재 여부
     */
    boolean existsAggregation(
            @Param("instanceId") Long instanceId,
            @Param("collectedAt") OffsetDateTime collectedAt
    );
}
