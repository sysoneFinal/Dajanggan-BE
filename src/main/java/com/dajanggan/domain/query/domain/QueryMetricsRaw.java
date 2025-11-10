package com.dajanggan.domain.query.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 쿼리 메트릭스 원시 데이터 엔티티
 * query_metrics_raw 테이블과 매핑
 *
 * ⚠️ PostgreSQL TIMESTAMPTZ 타입 매핑을 위해 OffsetDateTime 사용
 *
 * @author 이해든
 */
public class QueryMetricsRaw {

    // 쿼리 지표 고유 ID
    private Long queryMetricId;

    // 데이터베이스ID
    private Long databaseId;

    // 수집 시각 (TIMESTAMPTZ -> OffsetDateTime)
    private OffsetDateTime collectedAt;

    // 쿼리ID
    private String queryId;

    // 쿼리해시
    private String queryHash;

    // 쿼리전문
    private String queryText;

    // 축약쿼리
    private String shortQuery;

    // 쿼리타입
    private String queryType;

    // 실행횟수
    private Integer executionCount;

    // IO 블록수
    private Long ioBlocks;

    // explain_plan
    private String explainPlan;

    // 계획시간
    private BigDecimal planningTimeMs;

    // 실행시간
    private BigDecimal executionTimeMs;

    // CPU 사용량
    private BigDecimal cpuUsagePercent;

    // 메모리사용량
    private BigDecimal memoryUsageMb;

    // 사용자이름
    private String username;

    // 접속_프로그램명
    private String applicationName;

    // 클라이언트IP
    private String clientAddr;

    // 상태
    private String state;

    // 생성일 (TIMESTAMPTZ -> OffsetDateTime)
    private OffsetDateTime createdAt;

    // CPU순위
    private Integer cpuRank;

    // 메모리순위
    private Integer memoryRank;

    // 기본 생성자
    public QueryMetricsRaw() {
    }

    // 전체 생성자
    public QueryMetricsRaw(Long queryMetricId, Long databaseId, OffsetDateTime collectedAt,
                           String queryId, String queryHash, String queryText, String shortQuery,
                           String queryType, Integer executionCount, Long ioBlocks,
                           String explainPlan, BigDecimal planningTimeMs, BigDecimal executionTimeMs,
                           BigDecimal cpuUsagePercent, BigDecimal memoryUsageMb, String username,
                           String applicationName, String clientAddr, String state,
                           OffsetDateTime createdAt, Integer cpuRank, Integer memoryRank) {
        this.queryMetricId = queryMetricId;
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

    // Getter/Setter
    public Long getQueryMetricId() {
        return queryMetricId;
    }

    public void setQueryMetricId(Long queryMetricId) {
        this.queryMetricId = queryMetricId;
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