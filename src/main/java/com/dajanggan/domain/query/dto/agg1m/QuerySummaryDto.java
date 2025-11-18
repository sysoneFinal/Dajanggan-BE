package com.dajanggan.domain.query.dto.agg1m;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 쿼리 요약 DTO (1분 집계 기반)
 * - QueryOverview(=Details) 페이지의 요약 카드용
 * - 기존 agg5m.QuerySummaryDto를 대체
 * - 최근 5분간의 1분 집계 데이터를 SUM/AVG하여 계산
 *
 * @author 이해든
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuerySummaryDto {

    private Long instanceId;
    private Long databaseId;

    //  기존 필드 (agg5m 호환)
    private Integer totalQueries;           // 전체 쿼리 수
    private Double avgExecutionTimeMs;      // 평균 실행 시간
    private Integer slowQueryCount;         // 슬로우 쿼리 수

    // 새로운 필드 (agg1m 추가)
    private Integer currentTps;             // 현재 TPS (초당 트랜잭션)
    private Integer currentQps;             // 현재 QPS (초당 쿼리)
    private Integer activeSessions;         // 활성 세션 수 (최근 5분 쿼리 개수)

    // 쿼리 타입별 통계
    private Integer selectCount;            // SELECT 쿼리 수
    private Integer insertCount;            // INSERT 쿼리 수
    private Integer updateCount;            // UPDATE 쿼리 수
    private Integer deleteCount;            // DELETE 쿼리 수

    //  리소스 사용률 추가
    private Double currentCpuUsagePercent;       // 현재 CPU 사용률
    private Double currentMemoryUsagePercent;    // 현재 메모리 사용률
    private Double currentDiskIoUsagePercent;    // 현재 디스크 I/O 사용률
    // 시간 정보
    private OffsetDateTime createdAt;       // 데이터 생성 시각
    private String timeRange;               // 시간 범위 (예: "최근 5분")
}