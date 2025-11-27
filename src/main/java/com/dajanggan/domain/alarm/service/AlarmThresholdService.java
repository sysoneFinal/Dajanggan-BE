package com.dajanggan.domain.alarm.service;

import com.dajanggan.domain.alarm.domain.AlarmRule;
import com.dajanggan.domain.alarm.domain.AlarmTracking;
import com.dajanggan.domain.alarm.repository.AlarmFeedMapper;
import com.dajanggan.domain.alarm.service.util.AlarmLevelParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 알람 임계치 체크 서비스
 * - 임계치 비교
 * - 발생 조건 체크
 * - 복구 조건 체크 (히스테리시스)
 * - 쿨다운 체크
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlarmThresholdService {

    private final AlarmLevelParser parser;
    private final AlarmFeedMapper alarmFeedMapper;

    /**
     * 값 비교 (operator 적용)
     * operator: "gt", "gte", "lt", "lte", "eq"
     */
    public boolean compareValue(BigDecimal current, BigDecimal threshold, String operator) {
        if (current == null || threshold == null || operator == null) return false;

        int cmp = current.compareTo(threshold);
        switch (operator) {
            case "gt":
                return cmp > 0;
            case "gte":
                return cmp >= 0;
            case "lt":
                return cmp < 0;
            case "lte":
                return cmp <= 0;
            case "eq":
                return cmp == 0;
            default:
                log.warn("알 수 없는 operator가 전달되었습니다: {}", operator);
                return false;
        }
    }

    /**
     * 임계치 체크
     */
    public String checkThresholds(AlarmRule rule, BigDecimal currentValue) {
        try {
            // JSONB levels 파싱
            Map<String, Map<String, Object>> levels = parser.parseJsonLevels(rule.getLevels());

            BigDecimal criticalThreshold = parser.getThresholdValue(levels, "critical");
            BigDecimal warningThreshold = parser.getThresholdValue(levels, "warning");
            BigDecimal noticeThreshold = parser.getThresholdValue(levels, "notice");

            String operator = rule.getOperator(); // gt, gte, lt, lte, eq

            // 디버깅: 임계치와 비교 결과 로그
            log.debug("임계치 체크: currentValue={}, operator={}, notice={}, warning={}, critical={}",
                    currentValue, operator, noticeThreshold, warningThreshold, criticalThreshold);

            if (criticalThreshold != null && compareValue(currentValue, criticalThreshold, operator)) {
                log.info("🔴 CRITICAL 임계치 초과: currentValue={} {} threshold={}",
                        currentValue, operator, criticalThreshold);
                return "CRITICAL";
            } else if (warningThreshold != null && compareValue(currentValue, warningThreshold, operator)) {
                log.info("⚠️ WARNING 임계치 초과: currentValue={} {} threshold={}",
                        currentValue, operator, warningThreshold);
                return "WARNING";
            } else if (noticeThreshold != null && compareValue(currentValue, noticeThreshold, operator)) {
                log.info("ℹ️ NOTICE 임계치 초과: currentValue={} {} threshold={}",
                        currentValue, operator, noticeThreshold);
                return "NOTICE";
            } else {
                log.debug("임계치 미달: currentValue={} {} 모든 임계치", currentValue, operator);
            }

        } catch (Exception e) {
            log.error("임계치 체크 실패", e);
        }

        return null;
    }

    /**
     * 알람 발생 조건 체크
     * - 윈도우 기반: 최근 windowMin 분 내에 occurCount번 발생해야 함
     * - 지속 시간: minDurationMin 분 이상 지속되어야 함
     * - 레벨 변경 시 무조건 알람 발생 (levelChanged로 처리)
     * - 같은 레벨에서 처음 조건 만족 시 알람 발생
     */
    public boolean shouldFireAlarm(AlarmRule rule, AlarmTracking tracking, String level, String previousLevel) {
        try {
            // 해당 레벨의 발생 조건 확인
            int requiredCount = parser.getOccurCount(rule.getLevels(), level);
            int requiredDuration = parser.getMinDuration(rule.getLevels(), level);
            int windowMin = parser.getWindowMin(rule.getLevels(), level);

            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime firstTriggered = tracking.getFirstTriggeredAt();

            log.info("shouldFireAlarm 상세 체크: ruleId={}, status={}, level={}, previousLevel={}, " +
                            "requiredCount={}, requiredDuration={}, consecutiveCount={}, firstTriggered={}",
                    rule.getAlarmRuleId(), tracking.getStatus(), level, previousLevel,
                    requiredCount, requiredDuration, tracking.getConsecutiveCount(), firstTriggered);

            // 1. 발생 횟수 체크
            if (tracking.getConsecutiveCount() < requiredCount) {
                log.info("발생 횟수 부족: {}/{} (윈도우: {}분)",
                        tracking.getConsecutiveCount(), requiredCount, windowMin);
                return false;
            }

            // 2. 최소 지속 시간 체크
            if (requiredDuration > 0) {
                long durationMinutes = java.time.Duration.between(firstTriggered, now).toMinutes();
                long durationSeconds = java.time.Duration.between(firstTriggered, now).getSeconds();
                log.info("⏱️ 지속 시간 체크: duration={}분 ({}초), required={}분",
                        durationMinutes, durationSeconds, requiredDuration);
                if (durationMinutes < requiredDuration) {
                    log.info("지속 시간 부족: {}분/{}분", durationMinutes, requiredDuration);
                    return false;
                }
            }

            // CRITICAL 수정: FIRED 상태 체크를 먼저 수행
            if ("FIRED".equals(tracking.getStatus())) {
                String currentTrackingLevel = tracking.getCurrentLevel();
                if (currentTrackingLevel != null && level.equals(currentTrackingLevel)) {
                    // FIRED 상태이고 같은 레벨이면 무조건 false 반환
                    log.info("이미 FIRED 상태: level={}, currentTrackingLevel={}, status=FIRED",
                            level, currentTrackingLevel);
                    return false;
                }
            }

            // 3. PENDING 상태에서 조건을 만족하면 FIRED로 전환
            if ("PENDING".equals(tracking.getStatus())) {
                long durationMinutes = requiredDuration > 0 ? java.time.Duration.between(firstTriggered, now).toMinutes() : 0;
                log.info("✅ PENDING → FIRED 전환 조건 충족: level={}, count={}/{}, duration={}분/{}분, status=PENDING",
                        level, tracking.getConsecutiveCount(), requiredCount, durationMinutes, requiredDuration);
                return true;
            }

            // 4. 기타 상태 (RESOLVED 등)
            long durationMinutes = requiredDuration > 0 ? java.time.Duration.between(firstTriggered, now).toMinutes() : 0;
            log.info("✅ 알람 발생 조건 충족: level={}, count={}/{}, duration={}분/{}분, status={}",
                    level, tracking.getConsecutiveCount(), requiredCount, durationMinutes, requiredDuration, tracking.getStatus());
            return true;

        } catch (Exception e) {
            log.error("알람 발생 조건 체크 실패", e);
            return false;
        }
    }

    /**
     * 알람 복구 조건 체크 (히스테리시스)
     * - 복구 임계치는 발생 임계치보다 낮게 설정
     * - 복구 지속 시간도 체크
     */
    public boolean shouldResolveAlarm(
            AlarmRule rule,
            AlarmTracking tracking,
            BigDecimal currentValue,
            String currentLevel
    ) {
        try {
            // 복구 임계치와 지속 시간 가져오기
            BigDecimal resolveThreshold = parser.getResolveThreshold(rule.getLevels(), currentLevel);
            int resolveDuration = parser.getResolveDuration(rule.getLevels(), currentLevel);
            String operator = rule.getOperator();

            // 복구 임계치가 설정되지 않았으면 기본값 사용 (발생 임계치의 80%)
            if (resolveThreshold == null) {
                BigDecimal fireThreshold = parser.getThresholdValue(
                        parser.parseJsonLevels(rule.getLevels()),
                        currentLevel.toLowerCase()
                );
                if (fireThreshold != null) {
                    // 발생 임계치의 80%로 설정 (operator에 따라 반대 방향)
                    if (operator.equals("gt") || operator.equals("gte")) {
                        resolveThreshold = fireThreshold.multiply(new BigDecimal("0.8"));
                    } else {
                        resolveThreshold = fireThreshold.multiply(new BigDecimal("1.2"));
                    }
                    log.debug("복구 임계치 기본값 사용: {} (발생 임계치의 80%)", resolveThreshold);
                } else {
                    // 복구 임계치를 알 수 없으면 즉시 복구 (기존 동작)
                    log.debug("복구 임계치를 알 수 없음: 즉시 복구");
                    return true;
                }
            }

            // 복구 지속 시간 기본값 (설정되지 않았으면 3분)
            if (resolveDuration <= 0) {
                resolveDuration = 3;
            }

            // 1. 복구 임계치 체크 (발생 임계치보다 낮은 값이어야 함)
            // operator 반대 방향으로 체크 (gt면 lt, lt면 gt)
            String resolveOperator = getReverseOperator(operator);
            boolean belowResolveThreshold = compareValue(currentValue, resolveThreshold, resolveOperator);

            if (!belowResolveThreshold) {
                log.debug("복구 임계치 미달: currentValue={} {} resolveThreshold={}",
                        currentValue, resolveOperator, resolveThreshold);
                return false;
            }

            // 2. 복구 지속 시간 체크
            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime firstTriggered = tracking.getFirstTriggeredAt();
            long durationMinutes = java.time.Duration.between(firstTriggered, now).toMinutes();

            if (durationMinutes < resolveDuration) {
                log.debug("복구 지속 시간 부족: {}분/{}분", durationMinutes, resolveDuration);
                return false;
            }

            log.info("✅ 복구 조건 충족: currentValue={} {} resolveThreshold={}, duration={}분/{}분",
                    currentValue, resolveOperator, resolveThreshold, durationMinutes, resolveDuration);
            return true;

        } catch (Exception e) {
            log.error("복구 조건 체크 실패", e);
            // 에러 발생 시 안전하게 복구 허용
            return true;
        }
    }

    /**
     * operator 반대 방향 반환 (히스테리시스용)
     */
    private String getReverseOperator(String operator) {
        if (operator == null) return "lt";
        return switch (operator) {
            case "gt" -> "lt";
            case "gte" -> "lte";
            case "lt" -> "gt";
            case "lte" -> "gte";
            case "eq" -> "eq";  // 같음은 그대로
            default -> "lt";
        };
    }

    /**
     * 쿨다운 체크
     * - 같은 레벨의 알람이 최근 cooldownMin 분 내에 발생했는지 확인
     * - 레벨 변경 시에는 쿨다운 무시 (중요한 변화이므로)
     */
    public boolean isInCooldown(AlarmRule rule, String level) {
        try {
            int cooldownMin = parser.getCooldown(rule.getLevels(), level);
            if (cooldownMin <= 0) {
                return false; // 쿨다운이 설정되지 않았으면 쿨다운 없음
            }

            // 마지막 알람 발생 시간 조회 (같은 레벨만, 해결되지 않은 Feed만)
            OffsetDateTime lastFiredAt = alarmFeedMapper.selectLastFiredAtByRuleId(
                    rule.getAlarmRuleId(), level);
            if (lastFiredAt == null) {
                log.debug("쿨다운 체크: 이전 알람 없음 - ruleId={}, level={}", rule.getAlarmRuleId(), level);
                return false; // 이전 알람이 없으면 쿨다운 없음
            }

            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime cooldownEnd = lastFiredAt.plusMinutes(cooldownMin);

            boolean inCooldown = now.isBefore(cooldownEnd);
            if (inCooldown) {
                long remainingSeconds = java.time.Duration.between(now, cooldownEnd).getSeconds();
                log.info("⏸️ 쿨다운 중: ruleId={}, level={}, lastFiredAt={}, 남은 시간={}초 ({}분)",
                        rule.getAlarmRuleId(), level, lastFiredAt, remainingSeconds, cooldownMin);
            } else {
                log.debug("쿨다운 종료: ruleId={}, level={}, lastFiredAt={}, cooldownEnd={}",
                        rule.getAlarmRuleId(), level, lastFiredAt, cooldownEnd);
            }

            return inCooldown;

        } catch (Exception e) {
            log.error("쿨다운 체크 실패: ruleId={}, level={}", rule.getAlarmRuleId(), level, e);
            return false; // 에러 발생 시 쿨다운 없음으로 처리
        }
    }
}