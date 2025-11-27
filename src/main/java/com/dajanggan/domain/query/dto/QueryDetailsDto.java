package com.dajanggan.domain.query.dto;

import com.dajanggan.domain.query.dto.agg1m.*;
import com.dajanggan.domain.query.dto.agg5m.*;
import lombok.*;

import java.util.List;

/**
 * 쿼리 상세 정보 통합 DTO
 *
 * 기능:
 * - QueryDetails 페이지에 필요한 모든 집계 데이터를 통합
 * - 요약 정보, Top 슬로우 쿼리, 트렌드 차트 데이터 포함
 * - 1분/5분 단위 집계 데이터 조합
 *
 * 작성자: 이해든
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class QueryDetailsDto {

    // 쿼리 요약 정보 (TPS, QPS, 평균 실행시간 등)
    private QuerySummaryDto querySummary;

    // Top 5 슬로우 쿼리
    private TopSlowQueryDto topSlowQueries;

    // 쿼리 타입별 추이 (SELECT, INSERT, UPDATE, DELETE)
    private List<QueryTypeTrendDto> queryTypeTrend;

    // 평균 실행 시간 추이
    private List<AvgExecutionTimeTrendDto> avgExecutionTimeTrend;

    // I/O 블록 추이
    private List<IoBlockTrendDto> ioBlockTrend;

    // 슬로우 쿼리 발생 추이
    private List<SlowQueryTrendDto> slowQueryTrend;

    // 슬로우 쿼리 상세 리스트
    private List<SlowQueryListDto> slowQueryList;
}