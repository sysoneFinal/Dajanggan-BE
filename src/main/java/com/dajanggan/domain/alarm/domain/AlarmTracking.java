package com.dajanggan.domain.alarm.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * AlarmTracking 엔티티
 *
 * 테이블: alarm_tracking
 *
 * 주요 책임:
 * - 알람 추적 상태 관리
 * - 연속 발생 횟수 추적
 * - 현재 레벨 및 값 관리
 * - 알람 상태 전환 (PENDING, FIRED, RESOLVED)
 *
 * 관계:
 * - AlarmRule (N:1) - 어떤 규칙을 추적하는지
 * - Instance (N:1) - 어떤 인스턴스인지
 * - Database (N:1) - 어떤 데이터베이스인지
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
public class AlarmTracking {

    private Long alarmTrackingId;
    private Long alarmRuleId;
    private Long instanceId;
    private Long databaseId;
    private OffsetDateTime firstTriggeredAt;
    private OffsetDateTime lastCheckedAt;

    @Builder.Default
    private Integer consecutiveCount = 0;

    private BigDecimal currentValue;
    private String currentLevel;

    @Builder.Default
    private String status = "PENDING";

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // ========== 비즈니스 로직 메서드 ==========

    /**
     * 연속 횟수 증가
     */
    public void incrementConsecutiveCount() {
        this.consecutiveCount = (this.consecutiveCount == null ? 0 : this.consecutiveCount) + 1;
        this.lastCheckedAt = OffsetDateTime.now();
    }

    /**
     * 연속 횟수 초기화
     */
    public void resetConsecutiveCount() {
        this.consecutiveCount = 0;
        this.lastCheckedAt = OffsetDateTime.now();
    }

    /**
     * 알람 발생 상태로 전환
     *
     * @param currentValue 현재 값
     * @param currentLevel 현재 레벨
     */
    public void fire(BigDecimal currentValue, String currentLevel) {
        this.status = "FIRED";
        this.currentValue = currentValue;
        this.currentLevel = currentLevel;
        this.lastCheckedAt = OffsetDateTime.now();

        if (this.firstTriggeredAt == null) {
            this.firstTriggeredAt = OffsetDateTime.now();
        }
    }

    /**
     * 알람 해제 상태로 전환
     */
    public void resolve() {
        this.status = "RESOLVED";
        this.lastCheckedAt = OffsetDateTime.now();
        this.consecutiveCount = 0;
    }

    /**
     * 대기 상태로 전환
     */
    public void pending() {
        this.status = "PENDING";
        this.lastCheckedAt = OffsetDateTime.now();
    }

    /**
     * 현재 값 및 레벨 업데이트
     *
     * @param value 새로운 값
     * @param level 새로운 레벨
     */
    public void updateCurrentValueAndLevel(BigDecimal value, String level) {
        this.currentValue = value;
        this.currentLevel = level;
        this.lastCheckedAt = OffsetDateTime.now();
    }

    /**
     * FIRED 상태인지 확인
     *
     * @return FIRED 상태이면 true
     */
    public boolean isFired() {
        return "FIRED".equals(this.status);
    }

    /**
     * PENDING 상태인지 확인
     *
     * @return PENDING 상태이면 true
     */
    public boolean isPending() {
        return "PENDING".equals(this.status);
    }

    /**
     * RESOLVED 상태인지 확인
     *
     * @return RESOLVED 상태이면 true
     */
    public boolean isResolved() {
        return "RESOLVED".equals(this.status);
    }

    /**
     * 레벨 변경 여부 확인
     *
     * @param newLevel 새로운 레벨
     * @return 레벨이 변경되었으면 true
     */
    public boolean hasLevelChanged(String newLevel) {
        if (this.currentLevel == null) return true;
        return !this.currentLevel.equals(newLevel);
    }

    /**
     * 지속 시간 계산 (분)
     *
     * @return 최초 발생부터 현재까지의 분 단위 시간
     */
    public long calculateDurationMinutes() {
        if (firstTriggeredAt == null) return 0;

        OffsetDateTime endTime = lastCheckedAt != null ? lastCheckedAt : OffsetDateTime.now();
        return java.time.Duration.between(firstTriggeredAt, endTime).toMinutes();
    }
}