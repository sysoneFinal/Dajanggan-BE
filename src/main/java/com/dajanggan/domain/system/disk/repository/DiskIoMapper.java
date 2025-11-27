// 작성자 : 김동현
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
    List<DiskIoRaw> selectPreviousRawByBackendType(
            @Param("instanceId") Long instanceId,
            @Param("currentCollectedAt") OffsetDateTime currentCollectedAt);

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
     * Backend Fsync 위젯 조회 (15분)
     */
    Map<String, Object> selectBackendFsyncWidget15m(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * Disk Latency 위젯 조회 (15분)
     */
    Map<String, Object> selectDiskLatencyWidget15m(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

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

    /**
     * Checkpoint vs Backend Write 시계열 데이터 조회 (1분 집계, 15분)
     */
    List<Map<String, Object>> selectCheckpointVsBackend1mTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * Backend Fsync Rate 시계열 데이터 조회 (1분 집계, 15분)
     */
    List<Map<String, Object>> selectBackendFsync1mTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    /**
     * Physical vs Cache Read 시계열 데이터 조회 (1분 집계, 15분)
     */
    List<Map<String, Object>> selectPhysicalVsCache1mTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
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
     * 높은 Fsync 발생 시간대 (페이징)
     */
    List<Map<String, Object>> selectHighFsyncWithPaging(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("statusList") List<String> statusList,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit
    );

    /**
     * 높은 Fsync 총 개수 조회
     */
    Long countHighFsyncList(
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

    /**
     * 낮은 Cache Hit Ratio 시간대 (페이징)
     */
    List<Map<String, Object>> selectLowCacheHitWithPaging(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("statusList") List<String> statusList,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit
    );

    /**
     * 낮은 Cache Hit Ratio 총 개수 조회
     */
    Long countLowCacheHitList(
            @Param("instanceId") Long instanceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("statusList") List<String> statusList
    );
}