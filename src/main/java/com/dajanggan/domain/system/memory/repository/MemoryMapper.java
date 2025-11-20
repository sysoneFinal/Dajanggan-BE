package com.dajanggan.domain.system.memory.repository;

import com.dajanggan.domain.system.memory.domain.MemoryAgg;
import com.dajanggan.domain.system.memory.domain.MemoryRaw;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface MemoryMapper {

    // ========================================
    // Scheduler용 쿼리
    // ========================================

    /**
     * 활성 인스턴스 ID 목록 조회
     */
    List<Long> selectActiveInstanceIds();

    /**
     * 이전 Raw 데이터 조회 (relname + database_name별로 가장 최근)
     */
    List<MemoryRaw> selectPreviousRawByRelname(@Param("instanceId") Long instanceId);

    /**
     * Memory Raw 데이터 삽입
     */
    void insertRaw(MemoryRaw memoryRaw);

    /**
     * Memory Raw 데이터 일괄 삽입
     */
    void insertRawBatch(@Param("list") List<MemoryRaw> list);

    /**
     * Memory Agg 데이터 삽입
     */
    void insertAgg(MemoryAgg memoryAgg);

    /**
     * Memory Agg 데이터 일괄 삽입
     */
    void insertAggBatch(@Param("list") List<MemoryAgg> list);

    /**
     * Memory Agg 5분 데이터 삽입
     */
    void insertAgg5m(com.dajanggan.domain.system.memory.domain.MemoryAgg5m memoryAgg5m);

    /**
     * Memory Agg 30분 데이터 삽입
     */
    void insertAgg30m(com.dajanggan.domain.system.memory.domain.MemoryAgg30m memoryAgg30m);

    // ========================================
    // 대시보드 위젯 조회 (5개)
    // ========================================

    /**
     * OS Memory Usage Widget (Redis 데이터 - Service에서 처리)
     */

    /**
     * Swap Usage Widget (Redis 데이터 - Service에서 처리)
     */

    /**
     * Shared Buffer Hit Ratio Widget
     */
    Map<String, Object> selectSharedBufferHitWidget(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * Temp File Usage Widget
     */
    Map<String, Object> selectTempFileUsageWidget(@Param("instanceId") Long instanceId);

    // ========================================
    // 1시간 차트 조회 (memory_agg)
    // ========================================

    /**
     * OS Memory Usage Chart 1h (OS Metric Agg - Service에서 처리)
     */

    /**
     * Buffer Cache Hit Chart 1h
     */
    List<Map<String, Object>> selectBufferCacheHitChart1h(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    // ========================================
    // 6시간 차트 조회 (memory_agg_5m)
    // ========================================

    /**
     * Temp File Chart 6h
     */
    List<Map<String, Object>> selectTempFileChart6h(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * Temp File Chart 6h (LIMIT)
     */
    List<Map<String, Object>> selectTempFileChart6hWithLimit(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("limit") Integer limit
    );

    /**
     * I/O Wait Time Chart 6h
     */
    List<Map<String, Object>> selectIoWaitTimeChart6h(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * I/O Wait Time Chart 6h (LIMIT)
     */
    List<Map<String, Object>> selectIoWaitTimeChart6hWithLimit(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("limit") Integer limit
    );

    // ========================================
    // 24시간 차트 조회 (memory_agg_30m)
    // ========================================

    /**
     * OS Memory Trend Chart 24h (OS Metric Agg - Service에서 처리)
     */

    /**
     * Swap Usage Trend Chart 24h (OS Metric Agg - Service에서 처리)
     */

    // ========================================
    // 리스트 조회 (1개 섹션)
    // ========================================

    /**
     * memory_agg_1m 테이블 전체 레코드 수 확인 (디버깅용)
     */
    Long countMemoryAgg1mByInstance(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * memory_agg_1m 테이블에서 relname IS NOT NULL인 레코드 수 확인 (디버깅용)
     */
    Long countMemoryAgg1mByInstanceWithRelname(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * 섹션 1: 낮은 캐시 히트율 테이블 Top 20
     */
    List<Map<String, Object>> selectLowCacheHitTop20(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("statusList") List<String> statusList,
            @Param("typeList") List<String> typeList
    );
}