package com.dajanggan.domain.alarm.service;

import com.dajanggan.domain.alarm.config.MetricConfig;
import com.dajanggan.domain.alarm.domain.AlarmRule;
import com.dajanggan.domain.alarm.domain.AlarmTracking;
import com.dajanggan.domain.alarm.repository.AlarmRuleMapper;
import com.dajanggan.domain.alarm.repository.AlarmTrackingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * GenericAlarm 수집기
 *
 * 주요 책임:
 * - 메트릭 값 조회
 * - 알람 규칙 체크
 * - 추적 상태 관리
 * - 알람 발생/해제 판단
 *
 * <pre>
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-14  김민서    1. 최초작성
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenericAlarmCollector {

    private final AlarmRuleMapper alarmRuleMapper;
    private final AlarmTrackingMapper alarmTrackingMapper;
    private final AlarmProcessingService processingService;
    private final AlarmThresholdService thresholdService;
    private final MetricConfig metricConfig;

    /**
     * 메트릭 체크
     */
    public void checkMetric(
            Connection conn,
            Long instanceId,
            Long databaseId,
            String metricType
    ) {
        // 1. 활성 규칙 조회
        List<AlarmRule> rules = alarmRuleMapper.findActiveRules(instanceId, databaseId, metricType);
        if (rules.isEmpty()) {
            return;
        }

        // 2. 메트릭 값 조회
        BigDecimal metricValue = queryMetricValue(conn, metricType);
        if (metricValue == null) {
            return;
        }

        // 3. 각 규칙 체크
        for (AlarmRule rule : rules) {
            checkRule(conn, rule, metricValue, metricType);
        }
    }

    /**
     * 규칙 체크
     */
    private void checkRule(
            Connection conn,
            AlarmRule rule,
            BigDecimal metricValue,
            String metricType
    ) {
        // 1. 현재 레벨 결정
        String currentLevel = thresholdService.determineLevel(
                metricValue,
                rule.getLevels(),
                rule.getOperator()
        );

        // 2. 추적 조회 또는 생성
        AlarmTracking tracking = getOrCreateTracking(
                rule.getAlarmRuleId(),
                rule.getInstanceId(),
                rule.getDatabaseId()
        );

        if (currentLevel != null) {
            // 임계값 초과
            handleThresholdExceeded(conn, rule, tracking, metricValue, currentLevel, metricType);
        } else {
            // 임계값 미달
            handleThresholdNormal(tracking, metricType);
        }
    }

    /**
     * 임계값 초과 처리
     */
    private void handleThresholdExceeded(
            Connection conn,
            AlarmRule rule,
            AlarmTracking tracking,
            BigDecimal metricValue,
            String currentLevel,
            String metricType
    ) {
        // 1. 연속 횟수 증가
        tracking.incrementConsecutiveCount();
        tracking.updateCurrentValueAndLevel(metricValue, currentLevel);

        // 2. 레벨 변경 체크
        boolean isLevelChange = tracking.hasLevelChanged(currentLevel);

        // 3. 발생 횟수 체크
        Integer requireCount = thresholdService.getOccurCountForLevel(rule.getLevels(), currentLevel);

        if (tracking.getConsecutiveCount() >= requireCount || isLevelChange) {
            // 알람 발생
            processingService.fireAlarm(
                    rule.getAlarmRuleId(),
                    tracking.getAlarmTrackingId(),
                    metricValue,
                    currentLevel,
                    metricType,
                    isLevelChange
            );
        } else {
            // 대기 중
            tracking.pending();
            alarmTrackingMapper.updateTracking(tracking);
        }
    }

    /**
     * 임계값 정상 처리
     */
    private void handleThresholdNormal(AlarmTracking tracking, String metricType) {
        if (tracking.isFired()) {
            // 알람 해제
            processingService.resolveAlarm(tracking.getAlarmTrackingId(), metricType);
        } else {
            // 연속 횟수 초기화
            tracking.resetConsecutiveCount();
            alarmTrackingMapper.updateTracking(tracking);
        }
    }

    /**
     * 추적 조회 또는 생성
     */
    private AlarmTracking getOrCreateTracking(
            Long alarmRuleId,
            Long instanceId,
            Long databaseId
    ) {
        AlarmTracking tracking = alarmTrackingMapper.selectByRule(alarmRuleId, instanceId, databaseId);

        if (tracking == null) {
            tracking = AlarmTracking.builder()
                    .alarmRuleId(alarmRuleId)
                    .instanceId(instanceId)
                    .databaseId(databaseId)
                    .consecutiveCount(0)
                    .status("PENDING")
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            alarmTrackingMapper.insertTracking(tracking);
        }

        return tracking;
    }

    /**
     * 메트릭 값 조회
     */
    private BigDecimal queryMetricValue(Connection conn, String metricType) {
        String sql = metricConfig.getMetricQuery(metricType);
        if (sql == null) {
            log.warn("메트릭 쿼리 없음: {}", metricType);
            return null;
        }

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getBigDecimal(1);
            }

        } catch (Exception e) {
            log.error("메트릭 조회 실패: metricType={}", metricType, e);
        }

        return null;
    }
}
