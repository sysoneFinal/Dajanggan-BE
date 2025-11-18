package com.dajanggan.domain.query.service;

import com.dajanggan.domain.query.dto.QueryMetricsRawDto;

import java.util.List;
import java.util.Map;

/**
 * 쿼리 메트릭스 원시 데이터 서비스 인터페이스
 * 비즈니스 로직 정의
 *
 * @author 이해든
 */
public interface QueryMetricsRawService {

    /**
     * 전체 쿼리 메트릭스 조회
     * @return 전체 쿼리 메트릭스 DTO 목록
     */
    List<QueryMetricsRawDto> getAllQueryMetrics();

    /**
     * ID로 쿼리 메트릭스 조회
     * @param queryMetricId 쿼리 메트릭 ID
     * @return 쿼리 메트릭스 DTO
     */
    QueryMetricsRawDto getQueryMetricById(Long queryMetricId);

    /**
     * 데이터베이스 ID로 쿼리 메트릭스 목록 조회 (전체)
     * @param databaseId 데이터베이스 ID
     * @return 해당 데이터베이스의 쿼리 메트릭스 DTO 목록
     */
    List<QueryMetricsRawDto> getQueryMetricsByDatabaseId(Long databaseId);

    /**
     * 데이터베이스 ID와 기간으로 쿼리 메트릭스 조회
     * @param databaseId 데이터베이스 ID
     * @param days 조회 기간 (일 단위)
     * @return 해당 기간의 쿼리 메트릭스 DTO 목록
     */
    List<QueryMetricsRawDto> getQueryMetricsByDatabaseIdAndDays(Long databaseId, Integer days);

    /**
     * 최근 N분간의 쿼리 메트릭스 조회
     * @param databaseId 데이터베이스 ID
     * @param minutes 조회할 시간(분)
     * @return 최근 N분간의 쿼리 메트릭스 DTO 목록
     */
    List<QueryMetricsRawDto> getRecentMetrics(Long databaseId, Integer minutes);

    /**
     * 쿼리 타입별 조회
     * @param queryType 쿼리 타입
     * @return 해당 타입의 쿼리 메트릭스 DTO 목록
     */
    List<QueryMetricsRawDto> getQueryMetricsByType(String queryType);

    /**
     * 슬로우 쿼리 조회
     * @param thresholdMs 임계값 (밀리초)
     * @return 슬로우 쿼리 DTO 목록
     */
    List<QueryMetricsRawDto> getSlowQueries(Double thresholdMs);

    /**
     * CPU 사용량 상위 N개 조회
     * @param limit 조회할 개수
     * @return CPU 사용량 상위 쿼리 DTO 목록
     */
    List<QueryMetricsRawDto> getTopByCpuUsage(Integer limit);

    /**
     * 메모리 사용량 상위 N개 조회
     * @param limit 조회할 개수
     * @return 메모리 사용량 상위 쿼리 DTO 목록
     */
    List<QueryMetricsRawDto> getTopByMemoryUsage(Integer limit);

    /**
     * 전체 쿼리 메트릭스 개수 조회
     * @return 전체 개수
     */
    int getTotalCount();

    /**
     * 데이터베이스별 쿼리 메트릭스 개수 조회
     * @param databaseId 데이터베이스 ID
     * @return 해당 데이터베이스의 쿼리 메트릭스 개수
     */
    int getCountByDatabaseId(Long databaseId);

    /**
     *  ExecutionStatus용 쿼리별 집계 통계
     * @param databaseId 데이터베이스 ID
     * @param days 조회 기간 (일 단위)
     * @return 쿼리별 집계 통계 목록
     */
    List<Map<String, Object>> getExecutionStats(Long databaseId, Integer days);
}