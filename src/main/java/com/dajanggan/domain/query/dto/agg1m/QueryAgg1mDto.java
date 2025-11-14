package com.dajanggan.domain.query.dto.agg1m;

import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 1분 단위 쿼리 집계 DTO
 * 실시간 모니터링용 (쿼리 성능, 실행 횟수 중심)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryAgg1mDto {

    private Long instanceId;
    private Long databaseId;
    private OffsetDateTime collectedAt;

    private Integer totalQueries;           // 전체 쿼리 수
    private Integer selectQueries;          // SELECT 쿼리 수
    private Integer insertQueries;          // INSERT 쿼리 수
    private Integer updateQueries;          // UPDATE 쿼리 수
    private Integer deleteQueries;          // DELETE 쿼리 수
    private Integer otherQueries;           // 기타 쿼리 수

    private Double avgExecutionTimeMs;      // 평균 실행 시간
    private Double maxExecutionTimeMs;      // 최대 실행 시간
    private Double avgPlanningTimeMs;       // 평균 계획 시간

    private Long totalIoBlocks;             // 총 IO 블록 수
    private Double avgIoBlocks;             // 평균 IO 블록 수

    private Integer slowQueryCount;         // 슬로우 쿼리 수 (임계값 초과)

    private OffsetDateTime createdAt;
}