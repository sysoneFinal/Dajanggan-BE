package com.dajanggan.domain.query.dto;

import com.dajanggan.domain.query.domain.QueryMetricsRaw;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 쿼리 메트릭스 응답 DTO
 * instance_id 포함
 *
 * @author 이해든
 */
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

    public QueryMetricsRawDto() {
    }

    /**
     * Entity를 DTO로 변환하는 정적 팩토리 메서드
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

    // Getter/Setter
    public Long getQueryMetricId() {
        return queryMetricId;
    }

    public void setQueryMetricId(Long queryMetricId) {
        this.queryMetricId = queryMetricId;
    }

    public Long getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(Long instanceId) {
        this.instanceId = instanceId;
    }

    public Long getDatabaseId() {
        return databaseId;
    }

    public void setDatabaseId(Long databaseId) {
        this.databaseId = databaseId;
    }

    public OffsetDateTime getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(OffsetDateTime collectedAt) {
        this.collectedAt = collectedAt;
    }

    public String getQueryId() {
        return queryId;
    }

    public void setQueryId(String queryId) {
        this.queryId = queryId;
    }

    public String getQueryHash() {
        return queryHash;
    }

    public void setQueryHash(String queryHash) {
        this.queryHash = queryHash;
    }

    public String getQueryText() {
        return queryText;
    }

    public void setQueryText(String queryText) {
        this.queryText = queryText;
    }

    public String getShortQuery() {
        return shortQuery;
    }

    public void setShortQuery(String shortQuery) {
        this.shortQuery = shortQuery;
    }

    public String getQueryType() {
        return queryType;
    }

    public void setQueryType(String queryType) {
        this.queryType = queryType;
    }

    public Integer getExecutionCount() {
        return executionCount;
    }

    public void setExecutionCount(Integer executionCount) {
        this.executionCount = executionCount;
    }

    public Long getIoBlocks() {
        return ioBlocks;
    }

    public void setIoBlocks(Long ioBlocks) {
        this.ioBlocks = ioBlocks;
    }

    public String getExplainPlan() {
        return explainPlan;
    }

    public void setExplainPlan(String explainPlan) {
        this.explainPlan = explainPlan;
    }

    public BigDecimal getPlanningTimeMs() {
        return planningTimeMs;
    }

    public void setPlanningTimeMs(BigDecimal planningTimeMs) {
        this.planningTimeMs = planningTimeMs;
    }

    public BigDecimal getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(BigDecimal executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public BigDecimal getCpuUsagePercent() {
        return cpuUsagePercent;
    }

    public void setCpuUsagePercent(BigDecimal cpuUsagePercent) {
        this.cpuUsagePercent = cpuUsagePercent;
    }

    public BigDecimal getMemoryUsageMb() {
        return memoryUsageMb;
    }

    public void setMemoryUsageMb(BigDecimal memoryUsageMb) {
        this.memoryUsageMb = memoryUsageMb;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getClientAddr() {
        return clientAddr;
    }

    public void setClientAddr(String clientAddr) {
        this.clientAddr = clientAddr;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getCpuRank() {
        return cpuRank;
    }

    public void setCpuRank(Integer cpuRank) {
        this.cpuRank = cpuRank;
    }

    public Integer getMemoryRank() {
        return memoryRank;
    }

    public void setMemoryRank(Integer memoryRank) {
        this.memoryRank = memoryRank;
    }
}