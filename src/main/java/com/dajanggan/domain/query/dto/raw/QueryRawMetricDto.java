package com.dajanggan.domain.query.dto.raw;

import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 원시 쿼리 메트릭 DTO
 *
 * 기능:
 * - PostgreSQL에서 직접 수집한 쿼리 실행 정보
 * - pg_stat_statements와 pg_stat_activity 조인 데이터
 * - 모니터링 DB에 저장하기 전 중간 데이터 전달용
 *
 * 작성자: 이해든
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryRawMetricDto {

    // 기본 식별 정보
    private Long databaseId;
    private Long instanceId;
    private OffsetDateTime collectedAt;

    // 쿼리 정보
    private String queryId;
    private String queryHash;
    private String queryText;
    private String shortQuery;
    private String queryType;

    // 실행 통계
    private Integer executionCount;
    private Long ioBlocks;

    // 시간 정보
    private BigDecimal planningTimeMs;
    private BigDecimal executionTimeMs;

    // 리소스 사용량
    private BigDecimal cpuUsagePercent;
    private BigDecimal memoryUsageMb;

    // 연결 정보
    private String username;
    private String applicationName;
    private String clientAddr;
    private String databasename;
    private String state;

    private OffsetDateTime createdAt;
}