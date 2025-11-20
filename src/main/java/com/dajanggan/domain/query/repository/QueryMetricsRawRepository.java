package com.dajanggan.domain.query.repository;

import com.dajanggan.domain.query.domain.QueryMetricsRaw;
import com.dajanggan.domain.query.dto.QueryMetricsRawDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 쿼리 메트릭스 원시 데이터 Repository
 * MyBatis Mapper 인터페이스
 *
 * ✅ 수정: findByDatabaseIdAndDays 추가
 *
 * @author 이해든
 */
@Mapper
public interface QueryMetricsRawRepository {

    /**
     * 전체 쿼리 메트릭스 조회
     */
    List<QueryMetricsRaw> findAll();

    /**
     * ID로 쿼리 메트릭스 조회
     */
    QueryMetricsRaw findById(@Param("queryMetricId") Long queryMetricId);

    /**
     * 데이터베이스 ID로 쿼리 메트릭스 목록 조회 (전체)
     */
    List<QueryMetricsRaw> findByDatabaseId(@Param("databaseId") Long databaseId);

    /**
     * ✅ 신규: 데이터베이스 ID와 기간으로 조회
     * @param params Map (databaseId, days)
     * @return 쿼리 메트릭스 엔티티 목록 (DTO 아님!)
     */
    List<QueryMetricsRaw> findByDatabaseIdAndDays(Map<String, Object> params);

    /**
     * 최근 N분간의 쿼리 메트릭스 조회
     */
    List<QueryMetricsRaw> findRecentByDatabaseId(@Param("databaseId") Long databaseId,
                                                 @Param("minutes") Integer minutes);

    /**
     * 쿼리 타입별 조회
     */
    List<QueryMetricsRaw> findByQueryType(@Param("queryType") String queryType);

    /**
     * 슬로우 쿼리 조회
     */
    List<QueryMetricsRaw> findSlowQueries(@Param("thresholdMs") Double thresholdMs);

    /**
     * CPU 사용량 상위 N개 조회
     */
    List<QueryMetricsRaw> findTopByCpuUsage(@Param("limit") Integer limit);

    /**
     * 메모리 사용량 상위 N개 조회
     */
    List<QueryMetricsRaw> findTopByMemoryUsage(@Param("limit") Integer limit);
    /**
     *  ExecutionStatus용 쿼리별 집계 통계
     */
    List<Map<String, Object>> findExecutionStats(Map<String, Object> params);
    /**
     * 쿼리 메트릭스 등록
     */
    int insert(QueryMetricsRaw queryMetrics);

    /**
     * 쿼리 메트릭스 수정
     */
    int update(QueryMetricsRaw queryMetrics);

    /**
     * 쿼리 메트릭스 삭제
     */
    int deleteById(@Param("queryMetricId") Long queryMetricId);

    /**
     * 전체 쿼리 메트릭스 개수 조회
     */
    int count();

    /**
     * 데이터베이스별 쿼리 메트릭스 개수 조회
     */
    int countByDatabaseId(@Param("databaseId") Long databaseId);
    /**
     * 시간대별 쿼리 수 분포 조회
     * @param params databaseId, hours 포함
     * @return 시간대별 쿼리 수 목록
     */
    List<Map<String, Object>> findHourlyDistribution(Map<String, Object> params);
}