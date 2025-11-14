package com.dajanggan.domain.query.repository;

import com.dajanggan.domain.query.dto.agg1m.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

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

}