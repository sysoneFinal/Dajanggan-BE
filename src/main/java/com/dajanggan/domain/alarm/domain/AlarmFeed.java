package com.dajanggan.domain.alarm.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * AlarmFeed 엔티티
 *
 * 테이블: alarm_feed
 *
 * 주요 책임:
 * 
 *   알람 피드 정보 관리
 *   알람 발생/해제 상태 추적
 *   읽음/확인 상태 관리
 * 
 *
 * 관계:
 * 
 *   AlarmRule (N:1) - 어떤 규칙에 의해 발생했는지
 *   AlarmTracking (N:1) - 어떤 추적에 속하는지
 *   Instance (N:1) - 어떤 인스턴스인지
 *   Database (N:1) - 어떤 데이터베이스인지
 * 
 *
 *  
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-13  김민서    1. 최초작성
 * 
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmFeed {

    private Long alarmFeedId;
    private Long alarmRuleId;
    private Long alarmTrackingId;
    private Long instanceId;
    private Long databaseId;
    private String alarmTitle;
    private String severityLevel;
    private String metricType;
    private BigDecimal currentValue;
    private BigDecimal thresholdValue;
    private String message;
    private OffsetDateTime occurredAt;
    private OffsetDateTime resolvedAt;

    @Builder.Default
    private Boolean isResolved = false;

    @Builder.Default
    private Boolean isRead = false;

    @Builder.Default
    private Boolean acknowledged = false;

    private OffsetDateTime acknowledgedAt;

    // ========== 비즈니스 로직 메서드 ==========

    /**
     * 알람 읽음 처리
     */
    public void markAsRead() {
        this.isRead = true;
    }

    /**
     * 알람 확인 처리
     */
    public void acknowledge() {
        this.acknowledged = true;
        this.acknowledgedAt = OffsetDateTime.now();
    }

    /**
     * 알람 해제 처리
     */
    public void resolve() {
        this.isResolved = true;
        this.resolvedAt = OffsetDateTime.now();
    }

    /**
     * 미확인 알람인지 확인
     *
     * @return 읽지 않았으면 true
     */
    public boolean isUnread() {
        return !Boolean.TRUE.equals(this.isRead);
    }

    /**
     * 활성 알람인지 확인
     *
     * @return 해제되지 않았으면 true
     */
    public boolean isActive() {
        return !Boolean.TRUE.equals(this.isResolved);
    }

    /**
     * Critical 레벨인지 확인
     *
     * @return CRITICAL이면 true
     */
    public boolean isCritical() {
        return "CRITICAL".equalsIgnoreCase(this.severityLevel);
    }

    /**
     * 임계치 초과율 계산
     *
     * @return 초과율 (%)
     */
    public BigDecimal calculateExceedanceRate() {
        if (thresholdValue == null || thresholdValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return currentValue
                .subtract(thresholdValue)
                .divide(thresholdValue, 2, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}