package com.dajanggan.domain.query.dto.agg5m;

import lombok.*;
import java.time.OffsetDateTime;

/**
 * 5분 단위 쿼리 요약 집계 DTO
 * - 상단 요약 카드
 * - Top5 슬로우 쿼리 포함
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryAgg5mDto {

    private Long instanceId;
    private Long databaseId;
    private OffsetDateTime collectedAt;

    private Integer totalQueries;
    private Double avgExecutionTimeMs;
    private Integer slowQueryCount;
    private Long totalIoBlocks;

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