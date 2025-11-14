package com.dajanggan.domain.query.dto;

import com.dajanggan.domain.query.dto.agg1m.*;
import com.dajanggan.domain.query.dto.agg5m.*;
import lombok.*;

import java.util.List;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class QueryDetailsDto {

    private QuerySummaryDto querySummary;                   // 5분 쿼리 요약
    private TopSlowQueryDto topSlowQueries;                 // Top 5 슬로우 쿼리
    private List<QueryTypeTrendDto> queryTypeTrend;         // 쿼리 타입별 추이
    private List<AvgExecutionTimeTrendDto> avgExecutionTimeTrend; // 평균 실행 시간 추이
    private List<IoBlockTrendDto> ioBlockTrend;             // IO 블록 추이
    private List<SlowQueryTrendDto> slowQueryTrend;         // 슬로우 쿼리 추이
    private List<SlowQueryListDto> slowQueryList;           // 슬로우 쿼리 리스트

}