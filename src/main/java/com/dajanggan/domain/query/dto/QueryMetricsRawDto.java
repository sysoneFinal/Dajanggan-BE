package com.dajanggan.domain.query.dto;

import com.dajanggan.domain.query.domain.QueryMetricsRaw;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 쿼리 메트릭스 응답 DTO
 *
 * 기능:
 * - QueryMetricsRaw 엔티티를 API 응답용 DTO로 변환
 * - instance_id 포함하여 다중 인스턴스 지원
 * - 정적 팩토리 메서드(from)로 Entity -> DTO 변환
 *
 * 작성자: 이해든
 */
@Data
@NoArgsConstructor
public class QueryMetricsRawDto {

    private Long queryMetricId;
    private Long instanceId;
    private Long databaseId;
    private OffsetDateTime collectedAt;
    private String queryId;
    private String queryHash;
    private String queryText;
    private String shortQuery;
    private String queryType;
    private Integer executionCount;
    private Long ioBlocks;
    private String explainPlan;
    private BigDecimal planningTimeMs;
    private BigDecimal executionTimeMs;
    private BigDecimal cpuUsagePercent;
    private BigDecimal memoryUsageMb;
    private String username;
    private String applicationName;
    private String clientAddr;
    private String state;
    private OffsetDateTime createdAt;
    private Integer cpuRank;
    private Integer memoryRank;

    /**
     * Entity를 DTO로 변환하는 정적 팩토리 메서드
     *
     * @param entity QueryMetricsRaw 엔티티
     * @return QueryMetricsRawDto 또는 null
     */
    public static QueryMetricsRawDto from(QueryMetricsRaw entity) {
        if (entity == null) {
            return null;
        }

        QueryMetricsRawDto dto = new QueryMetricsRawDto();
        dto.setQueryMetricId(entity.getQueryMetricId());
        dto.setInstanceId(entity.getInstanceId());
        dto.setDatabaseId(entity.getDatabaseId());
        dto.setCollectedAt(entity.getCollectedAt());
        dto.setQueryId(entity.getQueryId());
        dto.setQueryHash(entity.getQueryHash());
        dto.setQueryText(entity.getQueryText());
        dto.setShortQuery(entity.getShortQuery());
        dto.setQueryType(entity.getQueryType());
        dto.setExecutionCount(entity.getExecutionCount());
        dto.setIoBlocks(entity.getIoBlocks());
        dto.setExplainPlan(entity.getExplainPlan());
        dto.setPlanningTimeMs(entity.getPlanningTimeMs());
        dto.setExecutionTimeMs(entity.getExecutionTimeMs());
        dto.setCpuUsagePercent(entity.getCpuUsagePercent());
        dto.setMemoryUsageMb(entity.getMemoryUsageMb());
        dto.setUsername(entity.getUsername());
        dto.setApplicationName(entity.getApplicationName());
        dto.setClientAddr(entity.getClientAddr());
        dto.setState(entity.getState());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setCpuRank(entity.getCpuRank());
        dto.setMemoryRank(entity.getMemoryRank());

        return dto;
    }
}