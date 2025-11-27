package com.dajanggan.domain.query.dto.agg5m;

import lombok.*;
import java.time.OffsetDateTime;

/**
 * 5분 단위 쿼리 요약 집계 DTO
 *
 * 기능:
 * - 5분마다 쿼리 성능 지표를 집계한 데이터
 * - 상단 요약 카드용 (전체 쿼리 수, 평균 실행시간, 슬로우 쿼리 수)
 * - Top 5 슬로우 쿼리 정보 포함
 * - query_metrics_agg_5m 테이블과 매핑
 *
 * 작성자: 이해든
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryAgg5mDto {

    // 기본 정보
    private Long instanceId;
    private Long databaseId;
    private OffsetDateTime collectedAt;

    // 집계 통계
    private Integer totalQueries;
    private Double avgExecutionTimeMs;
    private Integer slowQueryCount;
    private Long totalIoBlocks;

    // Top 5 슬로우 쿼리
    private String topSlowQuery1;
    private Double topSlowQuery1Time;
    private String topSlowQuery2;
    private Double topSlowQuery2Time;
    private String topSlowQuery3;
    private Double topSlowQuery3Time;
    private String topSlowQuery4;
    private Double topSlowQuery4Time;
    private String topSlowQuery5;
    private Double topSlowQuery5Time;

    private OffsetDateTime createdAt;
}