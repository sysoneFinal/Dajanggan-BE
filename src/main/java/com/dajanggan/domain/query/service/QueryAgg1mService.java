package com.dajanggan.domain.query.service;

import com.dajanggan.domain.query.dto.agg1m.*;
import com.dajanggan.domain.query.repository.QueryAgg1mRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class QueryAgg1mService {

    private final QueryAgg1mRepository queryAgg1mRepository;

    public QueryAgg1mService(QueryAgg1mRepository queryAgg1mRepository) {
        this.queryAgg1mRepository = queryAgg1mRepository;
    }

    /** 쿼리 타입별 추이 */
    public List<QueryTypeTrendDto> findQueryTypeTrend(Map<String, Object> params) {
        log.debug("findQueryTypeTrend 호출 - params: {}", params);
        List<QueryTypeTrendDto> result = queryAgg1mRepository.getQueryTypeTrend(params);
        log.debug("findQueryTypeTrend 결과 개수: {}", result != null ? result.size() : 0);
        return result;
    }

    /** 평균 실행 시간 추이 */
    public List<AvgExecutionTimeTrendDto> findAvgExecutionTimeTrend(Map<String, Object> params) {
        log.debug("findAvgExecutionTimeTrend 호출 - params: {}", params);
        List<AvgExecutionTimeTrendDto> result = queryAgg1mRepository.getAvgExecutionTimeTrend(params);
        log.debug("findAvgExecutionTimeTrend 결과 개수: {}", result != null ? result.size() : 0);
        return result;
    }

    /** IO 블록 추이 */
    public List<IoBlockTrendDto> findIoBlockTrend(Map<String, Object> params) {
        log.debug("findIoBlockTrend 호출 - params: {}", params);
        List<IoBlockTrendDto> result = queryAgg1mRepository.getIoBlockTrend(params);
        log.debug("findIoBlockTrend 결과 개수: {}", result != null ? result.size() : 0);
        return result;
    }

    /** 슬로우 쿼리 추이 */
    public List<SlowQueryTrendDto> findSlowQueryTrend(Map<String, Object> params) {
        log.debug("findSlowQueryTrend 호출 - params: {}", params);
        List<SlowQueryTrendDto> result = queryAgg1mRepository.getSlowQueryTrend(params);
        log.debug("findSlowQueryTrend 결과 개수: {}", result != null ? result.size() : 0);
        return result;
    }
}