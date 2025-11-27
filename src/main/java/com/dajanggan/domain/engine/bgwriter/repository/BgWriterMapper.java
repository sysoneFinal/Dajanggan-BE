// 작성자 : 김동현
package com.dajanggan.domain.engine.bgwriter.repository;

import com.dajanggan.domain.engine.bgwriter.domain.BgWriterRaw;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface BgWriterMapper {

    /**
     * Clean Rate 시계열 데이터 조회
     */
    List<Map<String, Object>> selectCleanRateTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("intervalMinutes") Integer intervalMinutes
    );

    /**
     * Maxwritten Clean 시계열 데이터 조회
     */
    List<Map<String, Object>> selectMaxwrittenCleanTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("intervalMinutes") Integer intervalMinutes
    );

    /**
     * BGWriter vs Checkpoint 비교 데이터 조회
     */
    List<Map<String, Object>> selectBgwriterVsCheckpointTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("intervalMinutes") Integer intervalMinutes
    );

    /**
     * Buffer 재사용률 시계열 데이터 조회
     */
    List<Map<String, Object>> selectBufferReuseRateTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("intervalMinutes") Integer intervalMinutes
    );

    /**
     * Buffer Flush 비율 시계열 데이터 조회
     */
    List<Map<String, Object>> selectBufferFlushRatioTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("intervalMinutes") Integer intervalMinutes
    );

    /**
     * 최근 통계 조회
     */
    Map<String, Object> selectRecentStats(@Param("instanceId") Long instanceId);

    /**
     * 최근 통계 조회 (15분, bgwriter_agg_1m 사용)
     */
    Map<String, Object> selectRecentStats15m(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * BGWriter 리스트 데이터 조회
     */
    List<Map<String, Object>> selectBgWriterList(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("statusList") List<String> statusList
    );

    // ========== 데이터 수집용 메서드 ==========

    /**
     * 활성화된 인스턴스 ID 목록 조회
     */
    List<Long> selectActiveInstanceIds();

    /**
     * 이전 Raw 데이터 조회 (증분 계산용)
     */
    BgWriterRaw selectPreviousRaw(@Param("instanceId") Long instanceId, @Param("collectedAt") OffsetDateTime collectedAt);

    /**
     * 이전 1분 집계 데이터 조회 (5분 집계 계산용)
     */
    List<com.dajanggan.domain.engine.bgwriter.domain.BgWriterAgg1m> selectPreviousAgg1m(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * Raw 데이터 삽입
     */
    void insertRaw(BgWriterRaw raw);

    /**
     * 1분 집계 데이터 삽입
     */
    void insertAgg1m(com.dajanggan.domain.engine.bgwriter.domain.BgWriterAgg1m agg1m);

    /**
     * 5분 집계 데이터 삽입
     */
    void insertAgg5m(com.dajanggan.domain.engine.bgwriter.domain.BgWriterAgg5m agg5m);
}
