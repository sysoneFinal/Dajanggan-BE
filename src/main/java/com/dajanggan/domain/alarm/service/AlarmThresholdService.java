package com.dajanggan.domain.alarm.service;

import com.dajanggan.domain.alarm.dto.AlarmRuleDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * AlarmThreshold 서비스
 *
 * 주요 책임:
 * - 임계값 초과 여부 판단
 * - 레벨별 임계값 추출
 * - 연산자 기반 비교 (GT, LT, GTE, LTE, EQ)
 * - Levels JSON 파싱
 *
 * <pre>
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-14  김민서    1. 최초작성
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmThresholdService {

    private final ObjectMapper objectMapper;

    /**
     * 임계값 초과 여부 확인
     *
     * @param currentValue 현재 값
     * @param threshold 임계값
     * @param operator 연산자 (GT, LT, GTE, LTE, EQ)
     * @return 임계값을 초과하면 true
     */
    public boolean isThresholdExceeded(
            BigDecimal currentValue,
            BigDecimal threshold,
            String operator
    ) {
        if (currentValue == null || threshold == null) {
            return false;
        }

        if (operator == null) {
            operator = "GT"; // 기본값
        }

        return switch (operator.toUpperCase()) {
            case "GT" -> currentValue.compareTo(threshold) > 0;
            case "GTE" -> currentValue.compareTo(threshold) >= 0;
            case "LT" -> currentValue.compareTo(threshold) < 0;
            case "LTE" -> currentValue.compareTo(threshold) <= 0;
            case "EQ" -> currentValue.compareTo(threshold) == 0;
            default -> {
                log.warn("알 수 없는 연산자: {}, GT로 처리", operator);
                yield currentValue.compareTo(threshold) > 0;
            }
        };
    }

    /**
     * 레벨별 임계값 가져오기
     *
     * @param levelsJson Levels JSON 문자열
     * @param level 레벨 (info, warn, critical)
     * @return 임계값 (없으면 null)
     */
    public BigDecimal getThresholdForLevel(String levelsJson, String level) {
        if (levelsJson == null || level == null) {
            return null;
        }

        try {
            AlarmRuleDto.Levels levels = objectMapper.readValue(
                    levelsJson, AlarmRuleDto.Levels.class);

            return switch (level.toLowerCase()) {
                case "info" -> levels.getInfo() != null ? levels.getInfo().getThreshold() : null;
                case "warn", "warning" -> levels.getWarn() != null ? levels.getWarn().getThreshold() : null;
                case "critical" -> levels.getCritical() != null ? levels.getCritical().getThreshold() : null;
                default -> null;
            };
        } catch (Exception e) {
            log.error("Levels JSON 파싱 실패: {}", levelsJson, e);
            return null;
        }
    }

    /**
     * 레벨별 해제 임계값 가져오기
     *
     * @param levelsJson Levels JSON 문자열
     * @param level 레벨
     * @return 해제 임계값 (없으면 null)
     */
    public BigDecimal getResolveThresholdForLevel(String levelsJson, String level) {
        if (levelsJson == null || level == null) {
            return null;
        }

        try {
            AlarmRuleDto.Levels levels = objectMapper.readValue(
                    levelsJson, AlarmRuleDto.Levels.class);

            return switch (level.toLowerCase()) {
                case "info" -> levels.getInfo() != null ? levels.getInfo().getResolveThreshold() : null;
                case "warn", "warning" -> levels.getWarn() != null ? levels.getWarn().getResolveThreshold() : null;
                case "critical" -> levels.getCritical() != null ? levels.getCritical().getResolveThreshold() : null;
                default -> null;
            };
        } catch (Exception e) {
            log.error("Levels JSON 파싱 실패: {}", levelsJson, e);
            return null;
        }
    }

    /**
     * 레벨별 최소 지속 시간 가져오기 (분)
     *
     * @param levelsJson Levels JSON 문자열
     * @param level 레벨
     * @return 최소 지속 시간 (분)
     */
    public Integer getMinDurationForLevel(String levelsJson, String level) {
        if (levelsJson == null || level == null) {
            return 1; // 기본값
        }

        try {
            AlarmRuleDto.Levels levels = objectMapper.readValue(
                    levelsJson, AlarmRuleDto.Levels.class);

            return switch (level.toLowerCase()) {
                case "info" -> levels.getInfo() != null ? levels.getInfo().getMinDurationMin() : 1;
                case "warn", "warning" -> levels.getWarn() != null ? levels.getWarn().getMinDurationMin() : 1;
                case "critical" -> levels.getCritical() != null ? levels.getCritical().getMinDurationMin() : 1;
                default -> 1;
            };
        } catch (Exception e) {
            log.error("Levels JSON 파싱 실패: {}", levelsJson, e);
            return 1;
        }
    }

    /**
     * 레벨별 발생 횟수 가져오기
     *
     * @param levelsJson Levels JSON 문자열
     * @param level 레벨
     * @return 발생 횟수
     */
    public Integer getOccurCountForLevel(String levelsJson, String level) {
        if (levelsJson == null || level == null) {
            return 1; // 기본값
        }

        try {
            AlarmRuleDto.Levels levels = objectMapper.readValue(
                    levelsJson, AlarmRuleDto.Levels.class);

            return switch (level.toLowerCase()) {
                case "info" -> levels.getInfo() != null ? levels.getInfo().getOccurCount() : 1;
                case "warn", "warning" -> levels.getWarn() != null ? levels.getWarn().getOccurCount() : 1;
                case "critical" -> levels.getCritical() != null ? levels.getCritical().getOccurCount() : 1;
                default -> 1;
            };
        } catch (Exception e) {
            log.error("Levels JSON 파싱 실패: {}", levelsJson, e);
            return 1;
        }
    }

    /**
     * 현재 레벨 결정
     *
     * @param currentValue 현재 값
     * @param levelsJson Levels JSON
     * @param operator 연산자
     * @return 레벨 (CRITICAL, WARN, INFO, null)
     */
    public String determineLevel(
            BigDecimal currentValue,
            String levelsJson,
            String operator
    ) {
        if (currentValue == null || levelsJson == null) {
            return null;
        }

        // CRITICAL 체크
        BigDecimal criticalThreshold = getThresholdForLevel(levelsJson, "critical");
        if (criticalThreshold != null && isThresholdExceeded(currentValue, criticalThreshold, operator)) {
            return "CRITICAL";
        }

        // WARN 체크
        BigDecimal warnThreshold = getThresholdForLevel(levelsJson, "warn");
        if (warnThreshold != null && isThresholdExceeded(currentValue, warnThreshold, operator)) {
            return "WARN";
        }

        // INFO 체크
        BigDecimal infoThreshold = getThresholdForLevel(levelsJson, "info");
        if (infoThreshold != null && isThresholdExceeded(currentValue, infoThreshold, operator)) {
            return "INFO";
        }

        return null; // 임계값 미달
    }

    /**
     * 해제 조건 확인
     *
     * @param currentValue 현재 값
     * @param levelsJson Levels JSON
     * @param level 현재 레벨
     * @param operator 연산자
     * @return 해제 조건을 만족하면 true
     */
    public boolean isResolveConditionMet(
            BigDecimal currentValue,
            String levelsJson,
            String level,
            String operator
    ) {
        BigDecimal resolveThreshold = getResolveThresholdForLevel(levelsJson, level);
        if (resolveThreshold == null) {
            return false;
        }

        // 연산자 반대로 적용 (GT → LT)
        String reverseOperator = reverseOperator(operator);
        return isThresholdExceeded(currentValue, resolveThreshold, reverseOperator);
    }

    /**
     * 연산자 반전
     */
    private String reverseOperator(String operator) {
        if (operator == null) return "LT";

        return switch (operator.toUpperCase()) {
            case "GT" -> "LT";
            case "GTE" -> "LTE";
            case "LT" -> "GT";
            case "LTE" -> "GTE";
            default -> operator;
        };
    }

    /**
     * Levels JSON 파싱
     *
     * @param levelsJson JSON 문자열
     * @return Levels 객체
     */
    public AlarmRuleDto.Levels parseLevels(String levelsJson) {
        if (levelsJson == null) {
            return getDefaultLevels();
        }

        try {
            return objectMapper.readValue(levelsJson, AlarmRuleDto.Levels.class);
        } catch (Exception e) {
            log.error("Levels JSON 파싱 실패", e);
            return getDefaultLevels();
        }
    }

    /**
     * 기본 Levels 생성
     */
    private AlarmRuleDto.Levels getDefaultLevels() {
        AlarmRuleDto.ThresholdLevel defaultLevel = new AlarmRuleDto.ThresholdLevel(
                BigDecimal.valueOf(100000), 1, 2, 15,
                BigDecimal.valueOf(80000), 2, 60
        );
        return new AlarmRuleDto.Levels(defaultLevel, defaultLevel, defaultLevel);
    }
}