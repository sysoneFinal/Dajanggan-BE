package com.dajanggan.domain.system.disk.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface DiskIoMapper {

    /**
     * 디스크 사용률 조회
     * @param instanceId 인스턴스 ID
     * @return 디스크 사용률 데이터
     */
    Map<String, Object> selectDiskUsage(@Param("instanceId") Long instanceId);

    /**
     * 프로세스별 I/O 시계열 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 프로세스별 I/O 시계열 데이터
     */
    List<Map<String, Object>> selectProcessIOTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * Queue Depth 시계열 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return Queue Depth 시계열 데이터
     */
    List<Map<String, Object>> selectQueueDepthTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * I/O Latency 시계열 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return I/O Latency 시계열 데이터
     */
    List<Map<String, Object>> selectIoLatencyTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * Throughput 시계열 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return Throughput 시계열 데이터
     */
    List<Map<String, Object>> selectThroughputTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * Evictions 시계열 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return Evictions 시계열 데이터
     */
    List<Map<String, Object>> selectEvictionsTimeSeries(
            @Param("instanceId") Long instanceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * WAL Bytes 시계열 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return WAL Bytes 시계열 데이터
     */
    List<Map<String, Object>> selectWalBytesTimeSeries(
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
     * Disk I/O 리스트 데이터 조회
     * @param instanceId 인스턴스 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @param statusList 상태 필터 리스트
     * @return Disk I/O 리스트 데이터
     */
    List<Map<String, Object>> selectDiskIoList(
            @Param("instanceId") Long instanceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("statusList") List<String> statusList
    );
}