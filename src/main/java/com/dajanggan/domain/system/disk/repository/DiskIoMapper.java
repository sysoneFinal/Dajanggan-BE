package com.dajanggan.domain.system.disk.repository;

import com.dajanggan.domain.system.disk.domain.DiskIoAgg;
import com.dajanggan.domain.system.disk.domain.DiskIoRaw;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface DiskIoMapper {

    /**
     * 활성 인스턴스 ID 목록 조회
     */
    List<Long> selectActiveInstanceIds();

    /**
     * 이전 Raw 데이터 조회 (backend_type별로 가장 최근 1개)
     */
    List<DiskIoRaw> selectPreviousRawByBackendType(@Param("instanceId") Long instanceId);

    /**
     * Disk I/O Raw 데이터 삽입
     */
    void insertRaw(DiskIoRaw diskIoRaw);

    /**
     * Disk I/O Raw 데이터 일괄 삽입
     */
    void insertRawBatch(@Param("list") List<DiskIoRaw> list);

    /**
     * Disk I/O Agg 데이터 삽입
     */
    void insertAgg(DiskIoAgg diskIoAgg);

    /**
     * Disk I/O Agg 데이터 일괄 삽입
     */
    void insertAggBatch(@Param("list") List<DiskIoAgg> list);

    /**
     * Disk I/O Agg 5분 데이터 삽입
     */
    void insertAgg5m(com.dajanggan.domain.system.disk.domain.DiskIoAgg5m diskIoAgg5m);

    /**
     * Disk I/O Agg 30분 데이터 삽입
     */
    void insertAgg30m(com.dajanggan.domain.system.disk.domain.DiskIoAgg30m diskIoAgg30m);

    /**
     * 디스크 사용률 조회
     */
    Map<String, Object> selectDiskUsage(@Param("instanceId") Long instanceId);

    /**
     * 프로세스별 I/O 시계열 데이터 조회
     */
    List<Map<String, Object>> selectProcessIOTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * I/O Latency 시계열 데이터 조회
     */
    List<Map<String, Object>> selectIoLatencyTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * I/O Latency 시계열 데이터 조회 (LIMIT)
     */
    List<Map<String, Object>> selectIoLatencyTimeSeriesWithLimit(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("limit") Integer limit
    );

    /**
     * Throughput 시계열 데이터 조회
     */
    List<Map<String, Object>> selectThroughputTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * 최근 통계 조회
     */
    Map<String, Object> selectRecentStats(@Param("instanceId") Long instanceId);

    /**
     * Disk I/O 리스트 데이터 조회
     */
    List<Map<String, Object>> selectDiskIoList(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("statusList") List<String> statusList
    );

    // ========================================
    // 5분 집계 테이블 조회 (disk_io_agg_5m) - 6시간 차트용
    // ========================================

    /**
     * I/O Latency 시계열 데이터 조회 (5분 집계, 6시간)
     */
    List<Map<String, Object>> selectIoLatency5mTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * I/O Latency 시계열 데이터 조회 (5분 집계, 6시간, LIMIT)
     */
    List<Map<String, Object>> selectIoLatency5mTimeSeriesWithLimit(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("limit") Integer limit
    );

    /**
     * Throughput 시계열 데이터 조회 (5분 집계, 6시간)
     */
    List<Map<String, Object>> selectThroughput5mTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * Process I/O 시계열 데이터 조회 (5분 집계, 6시간)
     */
    List<Map<String, Object>> selectProcessIO5mTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    // ========================================
    // 30분 집계 테이블 조회 (disk_io_agg_30m) - 24시간 차트용
    // ========================================

    /**
     * I/O Latency 시계열 데이터 조회 (30분 집계, 24시간)
     */
    List<Map<String, Object>> selectIoLatency30mTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * Throughput 시계열 데이터 조회 (30분 집계, 24시간)
     */
    List<Map<String, Object>> selectThroughput30mTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * Process I/O 시계열 데이터 조회 (30분 집계, 24시간)
     */
    List<Map<String, Object>> selectProcessIO30mTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * Checkpoint vs Backend Write 시계열 데이터 조회 (30분 집계, 24시간)
     */
    List<Map<String, Object>> selectCheckpointVsBackend30mTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * Checkpoint vs Backend Write 시계열 데이터 조회 (30분 집계, 24시간, LIMIT)
     */
    List<Map<String, Object>> selectCheckpointVsBackend30mTimeSeriesWithLimit(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("limit") Integer limit
    );

    /**
     * Backend Fsync Rate 시계열 데이터 조회 (30분 집계, 24시간)
     */
    List<Map<String, Object>> selectBackendFsync30mTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * Backend Fsync Rate 시계열 데이터 조회 (30분 집계, 24시간, LIMIT)
     */
    List<Map<String, Object>> selectBackendFsync30mTimeSeriesWithLimit(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("limit") Integer limit
    );

    /**
     * Physical vs Cache Read 시계열 데이터 조회 (30분 집계, 24시간)
     */
    List<Map<String, Object>> selectPhysicalVsCache30mTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * Physical vs Cache Read 시계열 데이터 조회 (30분 집계, 24시간, LIMIT)
     */
    List<Map<String, Object>> selectPhysicalVsCache30mTimeSeriesWithLimit(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("limit") Integer limit
    );

    // ========================================
    // Queue Depth & Evictions & WAL Bytes
    // ========================================

    /**
     * Queue Depth 시계열 데이터 조회 (1시간)
     * OS Metrics에서 조회하거나 별도 처리 필요
     */
    List<Map<String, Object>> selectQueueDepthTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * Evictions 시계열 데이터 조회 (1시간)
     */
    List<Map<String, Object>> selectEvictionsTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * WAL Bytes 시계열 데이터 조회 (1시간)
     */
    List<Map<String, Object>> selectWalBytesTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    // ========================================
    // Top 20 리스트 조회
    // ========================================

    /**
     * 높은 Fsync 발생 시간대 Top 20
     */
    List<Map<String, Object>> selectHighFsyncTop20(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("statusList") List<String> statusList
    );

    /**
     * 낮은 Cache Hit Ratio 시간대 Top 20
     */
    List<Map<String, Object>> selectLowCacheHitTop20(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("statusList") List<String> statusList
    );
}