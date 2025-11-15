package com.dajanggan.domain.query.service;

import com.dajanggan.domain.query.dto.agg5m.*;
import com.dajanggan.domain.query.repository.QueryAgg5mRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class QueryAgg5mService {

    private final QueryAgg5mRepository queryAgg5mRepository;

    public QueryAgg5mService(QueryAgg5mRepository queryAgg5mRepository) {
        this.queryAgg5mRepository = queryAgg5mRepository;
    }

    /** 지표: 요약카드 (단일 DB - Details 페이지) */
    public QuerySummaryDto findLatestSummary(Map<String, Object> params) {
        QuerySummaryDto result = queryAgg5mRepository.findLatestSummary(params);
        log.debug("findLatestSummary 결과: {}", result);
        // 데이터가 없을 경우 기본값 반환
        if (result == null) {
            return QuerySummaryDto.builder()
                    .totalQueries(0)
                    .avgExecutionTimeMs(0.0)
                    .slowQueryCount(0)
                    .build();
        }
        return result;
    }

    /** 상위 슬로우 쿼리 Top 5 */
    public TopSlowQueryDto findTopSlowQueries(Map<String, Object> params) {
        TopSlowQueryDto result = queryAgg5mRepository.findTopSlowQueries(params);
        log.debug("findTopSlowQueries 결과: {}", result);
        return result;
    }

    /** 슬로우 쿼리 리스트 */
    public List<SlowQueryListDto> findSlowQueryList(Map<String, Object> params) {
        List<SlowQueryListDto> result = queryAgg5mRepository.findSlowQueryList(params);
        log.debug("findSlowQueryList 결과 개수: {}", result != null ? result.size() : 0);
        return result;
    }
}