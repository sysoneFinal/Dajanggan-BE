package com.dajanggan.domain.query.repository;

import com.dajanggan.domain.query.domain.QueryMetricsRaw;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 쿼리 메트릭스 원시 데이터 Repository
 * MyBatis Mapper 인터페이스
 *
 * @author 이해든
 */
@Mapper
public interface QueryMetricsRawRepository {

    /**
     * 전체 쿼리 메트릭스 조회
     * @return 전체 쿼리 메트릭스 목록
     */
    List<QueryMetricsRaw> findAll();

    /**
     * ID로 쿼리 메트릭스 조회
     * @param queryMetricId 쿼리 메트릭 ID
     * @return 쿼리 메트릭스 엔티티
     */
    QueryMetricsRaw findById(@Param("queryMetricId") Long queryMetricId);

    /**
     * 데이터베이스 ID로 쿼리 메트릭스 목록 조회
     * @param databaseId 데이터베이스 ID
     * @return 해당 데이터베이스의 쿼리 메트릭스 목록
     */
    List<QueryMetricsRaw> findByDatabaseId(@Param("databaseId") Long databaseId);

    /**
     * 🆕 최근 N분간의 쿼리 메트릭스 조회 (실시간 모니터링용)
     * @param databaseId 데이터베이스 ID
     * @param minutes 조회할 시간(분)
     * @return 최근 N분간의 쿼리 메트릭스 목록
     */
    List<QueryMetricsRaw> findRecentByDatabaseId(@Param("databaseId") Long databaseId,
                                                 @Param("minutes") Integer minutes);

    /**
     * 쿼리 타입별 조회
     * @param queryType 쿼리 타입 (SELECT, INSERT, UPDATE, DELETE 등)
     * @return 해당 타입의 쿼리 메트릭스 목록
     */
    List<QueryMetricsRaw> findByQueryType(@Param("queryType") String queryType);

    /**
     * 슬로우 쿼리 조회 (실행 시간 기준)
     * @param thresholdMs 임계값 (밀리초)
     * @return 임계값을 초과한 슬로우 쿼리 목록
     */
    List<QueryMetricsRaw> findSlowQueries(@Param("thresholdMs") Double thresholdMs);

    /**
     * CPU 사용량 상위 N개 조회
     * @param limit 조회할 개수
     * @return CPU 사용량 상위 쿼리 목록
     */
    List<QueryMetricsRaw> findTopByCpuUsage(@Param("limit") Integer limit);

    /**
     * 메모리 사용량 상위 N개 조회
     * @param limit 조회할 개수
     * @return 메모리 사용량 상위 쿼리 목록
     */
    List<QueryMetricsRaw> findTopByMemoryUsage(@Param("limit") Integer limit);

    /**
     * 쿼리 메트릭스 등록
     * @param queryMetrics 등록할 쿼리 메트릭스 엔티티
     * @return 등록된 행 수
     */
    int insert(QueryMetricsRaw queryMetrics);

    /**
     * 쿼리 메트릭스 수정
     * @param queryMetrics 수정할 쿼리 메트릭스 엔티티
     * @return 수정된 행 수
     */
    int update(QueryMetricsRaw queryMetrics);

    /**
     * 쿼리 메트릭스 삭제
     * @param queryMetricId 삭제할 쿼리 메트릭 ID
     * @return 삭제된 행 수
     */
    int deleteById(@Param("queryMetricId") Long queryMetricId);

    /**
     * 전체 쿼리 메트릭스 개수 조회
     * @return 전체 개수
     */
    int count();

    /**
     * 데이터베이스별 쿼리 메트릭스 개수 조회
     * @param databaseId 데이터베이스 ID
     * @return 해당 데이터베이스의 쿼리 메트릭스 개수
     */
    int countByDatabaseId(@Param("databaseId") Long databaseId);
}