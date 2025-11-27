package com.dajanggan.domain.query.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 쿼리 메트릭스 원시 데이터 엔티티
 *
 * 기능:
 * - query_metrics_raw 테이블과 매핑
 * - PostgreSQL에서 수집된 쿼리 실행 정보 저장
 * - CPU, 메모리, I/O 등 리소스 사용량 추적
 * - TIMESTAMPTZ 타입은 OffsetDateTime으로 매핑
 *
 * 작성자: 이해든
 */
public class QueryMetricsRaw {

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

    public QueryMetricsRaw() {
    }

    public QueryMetricsRaw(Long queryMetricId, Long instanceId, Long databaseId, OffsetDateTime collectedAt,
                           String queryId, String queryHash, String queryText, String shortQuery,
                           String queryType, Integer executionCount, Long ioBlocks,
                           String explainPlan, BigDecimal planningTimeMs, BigDecimal executionTimeMs,
                           BigDecimal cpuUsagePercent, BigDecimal memoryUsageMb, String username,
                           String applicationName, String clientAddr, String state,
                           OffsetDateTime createdAt, Integer cpuRank, Integer memoryRank) {
        this.queryMetricId = queryMetricId;
        this.instanceId = instanceId;
        this.databaseId = databaseId;
        this.collectedAt = collectedAt;
        this.queryId = queryId;
        this.queryHash = queryHash;
        this.queryText = queryText;
        this.shortQuery = shortQuery;
        this.queryType = queryType;
        this.executionCount = executionCount;
        this.ioBlocks = ioBlocks;
        this.explainPlan = explainPlan;
        this.planningTimeMs = planningTimeMs;
        this.executionTimeMs = executionTimeMs;
        this.cpuUsagePercent = cpuUsagePercent;
        this.memoryUsageMb = memoryUsageMb;
        this.username = username;
        this.applicationName = applicationName;
        this.clientAddr = clientAddr;
        this.state = state;
        this.createdAt = createdAt;
        this.cpuRank = cpuRank;
        this.memoryRank = memoryRank;
    }

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

    @Override
    public String toString() {
        return "QueryMetricsRaw{" +
                "queryMetricId=" + queryMetricId +
                ", instanceId=" + instanceId +
                ", databaseId=" + databaseId +
                ", collectedAt=" + collectedAt +
                ", queryId='" + queryId + '\'' +
                ", queryHash='" + queryHash + '\'' +
                ", shortQuery='" + shortQuery + '\'' +
                ", queryType='" + queryType + '\'' +
                ", executionCount=" + executionCount +
                ", ioBlocks=" + ioBlocks +
                ", executionTimeMs=" + executionTimeMs +
                ", cpuUsagePercent=" + cpuUsagePercent +
                ", memoryUsageMb=" + memoryUsageMb +
                ", state='" + state + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}