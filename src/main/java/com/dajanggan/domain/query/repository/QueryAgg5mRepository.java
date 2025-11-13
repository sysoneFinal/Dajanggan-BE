package com.dajanggan.domain.query.repository;

import com.dajanggan.domain.query.dto.agg5m.*;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface QueryAgg5mRepository {

    /** 요약카드 (단일 DB용 - Details 페이지) */
    QuerySummaryDto findLatestSummary(Map<String, Object> params);

    /** 상위 슬로우 쿼리 Top 5 */
    TopSlowQueryDto findTopSlowQueries(Map<String, Object> params);

    /** 슬로우 쿼리 리스트 최신 10개 */
    List<SlowQueryListDto> findSlowQueryList(Map<String, Object> params);

}