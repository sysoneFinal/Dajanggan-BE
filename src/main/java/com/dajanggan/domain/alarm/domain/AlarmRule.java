package com.dajanggan.domain.alarm.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * AlarmRule 엔티티
 *
 * 테이블: alarm_rule
 *
 * 주요 책임:
 * - 알람 규칙 정보 관리
 * - 메트릭 임계값 설정
 * - 집계 방식 및 연산자 관리
 * - 레벨별 임계값 설정 (INFO, WARN, CRITICAL)
 *
 * 관계:
 * - Instance (N:1) - 어떤 인스턴스의 규칙인지
 * - Database (N:1) - 어떤 데이터베이스의 규칙인지
 *
 * <pre>
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-13  김민서    1. 최초작성
 * </pre>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmRule {

    private Long alarmRuleId;
    private Long databaseId;
    private Long instanceId;
    private String instanceName;
    private String databaseName;
    private String metricType;
    private String operator;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Builder.Default
    private Boolean enabled = true;

    /**
     * 레벨별 임계값 설정 (JSON)
     */
    private String levels;

    // ========== 비즈니스 로직 메서드 ==========

    /**
     * 규칙 활성화
     */
    public void enable() {
        this.enabled = true;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 규칙 비활성화
     */
    public void disable() {
        this.enabled = false;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 활성화 여부 확인
     *
     * @return 활성화되어 있으면 true
     */
    public boolean isActive() {
        return Boolean.TRUE.equals(this.enabled);
    }

    /**
     * 레벨 설정 업데이트
     *
     * @param levelsJson 새로운 레벨 설정 (JSON)
     */
    public void updateLevels(String levelsJson) {
        this.levels = levelsJson;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 집계 방식 및 연산자 업데이트
     *
     * @param operator 연산자
     */
    public void updateAggregationAndOperator(String operator) {

        if (operator != null) {
            this.operator = operator;
        }
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * Vacuum 관련 메트릭인지 확인
     *
     * @return Vacuum 메트릭이면 true
     */
    public boolean isVacuumMetric() {
        if (metricType == null) return false;
        return metricType.contains("vacuum") || metricType.contains("dead_tuples");
    }

    /**
     * 세션 관련 메트릭인지 확인
     *
     * @return 세션 메트릭이면 true
     */
    public boolean isSessionMetric() {
        if (metricType == null) return false;
        return metricType.contains("session") || metricType.contains("query") || metricType.contains("lock");
    }
}