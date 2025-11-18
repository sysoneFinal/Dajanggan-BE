package com.dajanggan.domain.query.service;

import com.dajanggan.domain.query.dto.QueryDetailsDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class QueryDetailService {

    private final QueryAgg5mService queryAgg5mService;
    private final QueryAgg1mService queryAgg1mService;

    public QueryDetailService(QueryAgg5mService queryAgg5mService, QueryAgg1mService queryAgg1mService) {
        this.queryAgg5mService = queryAgg5mService;
        this.queryAgg1mService = queryAgg1mService;
    }

    /** 쿼리 디테일 지표 전체 조회 (단일 DB용) */
    public QueryDetailsDto getQueryDetail(Map<String, Object> params) {
        log.info("QueryDetail 조회 시작 - instanceId: {}, databaseId: {}",
                params.get("instanceId"), params.get("databaseId"));

        try {
            QueryDetailsDto result = QueryDetailsDto.builder()
                    .querySummary(queryAgg1mService.findLatestSummary(params))  // 
                    .topSlowQueries(queryAgg5mService.findTopSlowQueries(params))
                    .queryTypeTrend(queryAgg1mService.findQueryTypeTrend(params))
                    .avgExecutionTimeTrend(queryAgg1mService.findAvgExecutionTimeTrend(params))
                    .ioBlockTrend(queryAgg1mService.findIoBlockTrend(params))
                    .slowQueryTrend(queryAgg1mService.findSlowQueryTrend(params))
                    .slowQueryList(queryAgg5mService.findSlowQueryList(params))
                    .build();

            return result;
        } catch (Exception e) {
            log.error("QueryDetail 조회 중 오류 발생", e);
            throw e;
        }
    }
}