package com.dajanggan.domain.query.repository;

import com.dajanggan.domain.query.dto.agg5m.*;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

/**
 * 5분 집계 데이터 Repository
 * - 슬로우 쿼리 분석 및 요약 통계 제공
 * - query_metrics_agg_5m 테이블 접근
 *
 * 작성자: 이해든
 */
@Mapper
public interface QueryAgg5mRepository {

    /**
     * 5분 집계 데이터 저장
     *
     * @param metrics 5분 단위로 집계된 쿼리 메트릭 목록
     */
    void insertAgg5m(@Param("metrics") List<QueryAgg5mDto> metrics);

    /**
     * 상위 슬로우 쿼리 Top 5 조회
     * 실행 시간이 가장 긴 쿼리 5개 반환
     *
     * @param params databaseId, instanceId 포함
     * @return Top 5 슬로우 쿼리 정보
     */
    TopSlowQueryDto findTopSlowQueries(Map<String, Object> params);

    /**
     * 슬로우 쿼리 리스트 최신 10개 조회
     * 최근 발생한 슬로우 쿼리 목록
     *
     * @param params databaseId, instanceId 포함
     * @return 최신 슬로우 쿼리 10개
     */
    List<SlowQueryListDto> findSlowQueryList(Map<String, Object> params);
}