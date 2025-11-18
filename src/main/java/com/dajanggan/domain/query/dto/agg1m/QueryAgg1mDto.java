package com.dajanggan.domain.query.dto.agg1m;

import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 1분 단위 쿼리 집계 DTO
 * 실시간 모니터링용 (쿼리 성능, 실행 횟수 중심)
 *
 * @author 이해든
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryAgg1mDto {

    private Long instanceId;
    private Long databaseId;
    private OffsetDateTime collectedAt;

    // 쿼리 개수 통계
    private Integer totalQueries;           // 전체 쿼리 수
    private Integer selectQueries;          // SELECT 쿼리 수
    private Integer insertQueries;          // INSERT 쿼리 수
    private Integer updateQueries;          // UPDATE 쿼리 수
    private Integer deleteQueries;          // DELETE 쿼리 수
    private Integer otherQueries;           // 기타 쿼리 수

    // 실행 시간 통계
    private Double avgExecutionTimeMs;      // 평균 실행 시간 (ms)
    private Double maxExecutionTimeMs;      // 최대 실행 시간 (ms)
    private Double avgPlanningTimeMs;       // 평균 계획 시간 (ms)

    // I/O 통계
    private Long totalIoBlocks;             // 총 IO 블록 수
    private Double avgIoBlocks;             // 평균 IO 블록 수

    // 슬로우 쿼리
    private Integer slowQueryCount;         // 슬로우 쿼리 수 (임계값 초과)

    // 🆕 리소스 사용률 (현재값)
    private Double currentCpuUsagePercent;      // 현재 CPU 사용률 (%)
    private Double currentMemoryUsagePercent;   // 현재 메모리 사용률 (%)
    private Double currentDiskIoUsagePercent;   // 현재 디스크 I/O 사용률 (%)

    // 🆕 Top Query 조회용 필드 (findTopQueries에서 사용)
    private Long queryMetricId;             // 쿼리 메트릭 ID (raw 테이블에서)
    private String queryText;               // 전체 쿼리 텍스트
    private String shortQuery;              // 짧은 쿼리 (요약본)
    private String queryType;               // 쿼리 타입 (SELECT, INSERT 등)
    private Integer executionCount;         // 실행 횟수
    private Double cpuUsagePercent;         // CPU 사용률 (개별 쿼리)
    private Double memoryUsageMb;           // 메모리 사용량 (개별 쿼리)

    // 🆕 Mapper에서 avg 접두사로 반환하는 필드들
    private Double avgCpuUsagePercent;      // CPU 사용률 (평균)
    private Double avgMemoryUsageMb;        // 메모리 사용량 (평균)

    // 시간 정보
    private OffsetDateTime createdAt;
}