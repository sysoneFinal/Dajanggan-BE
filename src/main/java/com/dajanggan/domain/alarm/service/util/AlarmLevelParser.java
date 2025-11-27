package com.dajanggan.domain.alarm.service.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 알람 레벨 JSON 파싱 유틸리티
 * - JSONB levels 파싱
 * - 임계치, 발생 횟수, 지속 시간 등 추출
 */
@Component
@Slf4j
public class AlarmLevelParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * JSONB levels 파싱
     */
    public Map<String, Map<String, Object>> parseJsonLevels(String levelsJson) {
        try {
            return objectMapper.readValue(levelsJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("JSONB 파싱 실패: {}", levelsJson, e);
            return Map.of();
        }
    }

    /**
     * 임계치 값 추출
     */
    public BigDecimal getThresholdValue(Map<String, Map<String, Object>> levels, String level) {
        try {
            Map<String, Object> levelConfig = levels.get(level);
            if (levelConfig != null && levelConfig.containsKey("threshold")) {
                Object threshold = levelConfig.get("threshold");
                if (threshold instanceof Number) {
                    return new BigDecimal(threshold.toString());
                }
            }
        } catch (Exception e) {
            log.error("임계치 추출 실패: level={}", level, e);
        }
        return null;
    }

    /**
     * 발생 횟수 추출
     */
    public int getOccurCount(String levelsJson, String level) {
        try {
            Map<String, Map<String, Object>> levels = parseJsonLevels(levelsJson);
            Map<String, Object> levelConfig = levels.get(level.toLowerCase());
            if (levelConfig != null && levelConfig.containsKey("occurCount")) {
                return ((Number) levelConfig.get("occurCount")).intValue();
            }
        } catch (Exception e) {
            log.error("발생 횟수 추출 실패: level={}", level, e);
        }
        return 2; // 기본값
    }

    /**
     * 최소 지속 시간 추출
     */
    public int getMinDuration(String levelsJson, String level) {
        try {
            Map<String, Map<String, Object>> levels = parseJsonLevels(levelsJson);
            Map<String, Object> levelConfig = levels.get(level.toLowerCase());
            if (levelConfig != null && levelConfig.containsKey("minDurationMin")) {
                return ((Number) levelConfig.get("minDurationMin")).intValue();
            }
        } catch (Exception e) {
            log.error("최소 지속 시간 추출 실패: level={}", level, e);
        }
        return 5; // 기본값
    }

    /**
     * 윈도우 시간 추출
     */
    public int getWindowMin(String levelsJson, String level) {
        try {
            Map<String, Map<String, Object>> levels = parseJsonLevels(levelsJson);
            Map<String, Object> levelConfig = levels.get(level.toLowerCase());
            if (levelConfig != null && levelConfig.containsKey("windowMin")) {
                return ((Number) levelConfig.get("windowMin")).intValue();
            }
        } catch (Exception e) {
            log.error("윈도우 시간 추출 실패: level={}", level, e);
        }
        return 15; // 기본값: 15분
    }

    /**
     * 복구 임계치 추출 (히스테리시스)
     */
    public BigDecimal getResolveThreshold(String levelsJson, String level) {
        try {
            Map<String, Map<String, Object>> levels = parseJsonLevels(levelsJson);
            Map<String, Object> levelConfig = levels.get(level.toLowerCase());
            if (levelConfig != null && levelConfig.containsKey("resolveThreshold")) {
                Object threshold = levelConfig.get("resolveThreshold");
                if (threshold instanceof Number) {
                    return new BigDecimal(threshold.toString());
                }
            }
        } catch (Exception e) {
            log.error("복구 임계치 추출 실패: level={}", level, e);
        }
        return null; // 기본값 없음
    }

    /**
     * 복구 지속 시간 추출 (히스테리시스)
     */
    public int getResolveDuration(String levelsJson, String level) {
        try {
            Map<String, Map<String, Object>> levels = parseJsonLevels(levelsJson);
            Map<String, Object> levelConfig = levels.get(level.toLowerCase());
            if (levelConfig != null && levelConfig.containsKey("resolveDurationMin")) {
                return ((Number) levelConfig.get("resolveDurationMin")).intValue();
            }
        } catch (Exception e) {
            log.error("복구 지속 시간 추출 실패: level={}", level, e);
        }
        return 0; // 기본값 없음
    }

    /**
     * 쿨다운 시간 추출
     */
    public int getCooldown(String levelsJson, String level) {
        try {
            Map<String, Map<String, Object>> levels = parseJsonLevels(levelsJson);
            Map<String, Object> levelConfig = levels.get(level.toLowerCase());
            if (levelConfig != null && levelConfig.containsKey("cooldownMin")) {
                return ((Number) levelConfig.get("cooldownMin")).intValue();
            }
        } catch (Exception e) {
            log.error("쿨다운 시간 추출 실패: level={}", level, e);
        }
        return 0; // 기본값: 쿨다운 없음
    }
}