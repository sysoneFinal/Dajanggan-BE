package com.dajanggan.domain.query.repository;

import com.dajanggan.domain.query.dto.agg1m.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 1분 집계 데이터 Repository
 *
 * @author 이해든
 */
@Mapper
public interface QueryAgg1mRepository {

    /** 1분 집계 데이터 저장 */
    void insertAgg1m(@Param("metrics") List<QueryAgg1mDto> metrics);

    /** 쿼리 타입별 추이 */
    List<QueryTypeTrendDto> getQueryTypeTrend(Map<String, Object> params);

    /** 평균 실행 시간 추이 */
    List<AvgExecutionTimeTrendDto> getAvgExecutionTimeTrend(Map<String, Object> params);

    /** IO 블록 추이 */
    List<IoBlockTrendDto> getIoBlockTrend(Map<String, Object> params);

    /** 슬로우 쿼리 추이 */
    List<SlowQueryTrendDto> getSlowQueryTrend(Map<String, Object> params);

    /** 🆕 요약 데이터 조회 (최근 5분 집계) - QueryOverview용 */
    QuerySummaryDto findRecentSummary(Map<String, Object> params);

    /** 🆕 트렌드 데이터 조회 (최근 N시간) - QueryOverview 차트용 */
    List<QueryAgg1mDto> findTrendData(Map<String, Object> params);

    /** 🆕 Top Query 조회 (리소스별) */
    List<QueryAgg1mDto> findTopQueries(Map<String, Object> params);
}