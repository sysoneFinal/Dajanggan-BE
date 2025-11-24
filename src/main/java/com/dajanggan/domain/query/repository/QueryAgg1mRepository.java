package com.dajanggan.domain.query.repository;

import com.dajanggan.domain.query.dto.agg1m.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 1분 집계 데이터 Repository
 * - QueryOverview 및 ExecutionStatus 페이지의 실시간 모니터링 데이터 제공
 * - query_metrics_agg_1m 테이블 접근
 *
 * @author 이해든
 */
@Mapper
public interface QueryAgg1mRepository {

    /**
     * 1분 집계 데이터 저장
     *
     * @param metrics 1분 단위로 집계된 쿼리 메트릭 목록
     */
    void insertAgg1m(@Param("metrics") List<QueryAgg1mDto> metrics);

    /**
     * 요약 데이터 조회 (최근 5분 집계)
     * QueryOverview 페이지의 요약 카드용
     *
     * @param params databaseId, instanceId 포함
     * @return 집계된 쿼리 요약 정보
     */
    QuerySummaryDto findRecentSummary(Map<String, Object> params);

    /**
     * 트렌드 데이터 조회 (최근 N시간)
     * QueryOverview 페이지의 TPS/QPS 차트용
     *
     * @param params databaseId, instanceId, hours 포함
     * @return 시간대별 집계 데이터 목록
     */
    List<QueryAgg1mDto> findTrendData(Map<String, Object> params);

    /**
     * Top Query 조회 (리소스별)
     * CPU, 메모리, I/O 사용량 상위 쿼리 조회
     *
     * @param params databaseId, instanceId, sortBy, limit 포함
     * @return 리소스 사용량 상위 쿼리 목록
     */
    List<QueryAgg1mDto> findTopQueries(Map<String, Object> params);

    /**
     * 쿼리 타입별 추이 조회
     * ExecutionStatus 페이지의 쿼리 타입 차트용
     *
     * @param params databaseId, instanceId, hours 포함
     * @return 시간대별 쿼리 타입 통계
     */
    List<QueryTypeTrendDto> getQueryTypeTrend(Map<String, Object> params);

    /**
     * 평균 실행 시간 추이 조회
     * ExecutionStatus 페이지의 실행 시간 차트용
     *
     * @param params databaseId, instanceId, hours 포함
     * @return 시간대별 평균 실행 시간 통계
     */
    List<AvgExecutionTimeTrendDto> getAvgExecutionTimeTrend(Map<String, Object> params);

    /**
     * I/O 블록 추이 조회
     * ExecutionStatus 페이지의 I/O 차트용
     *
     * @param params databaseId, instanceId, hours 포함
     * @return 시간대별 I/O 블록 통계
     */
    List<IoBlockTrendDto> getIoBlockTrend(Map<String, Object> params);

    /**
     * 슬로우 쿼리 추이 조회
     * ExecutionStatus 페이지의 슬로우 쿼리 차트용
     *
     * @param params databaseId, instanceId, hours 포함
     * @return 시간대별 슬로우 쿼리 발생 건수
     */
    List<SlowQueryTrendDto> getSlowQueryTrend(Map<String, Object> params);
}