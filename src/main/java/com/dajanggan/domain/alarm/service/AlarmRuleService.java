package com.dajanggan.domain.alarm.service;

import com.dajanggan.domain.alarm.domain.AlarmRule;
import com.dajanggan.domain.alarm.dto.AlarmRuleDto;
import com.dajanggan.domain.alarm.repository.AlarmRuleMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AlarmRule 서비스
 *
 * 주요 책임:
 * - 알람 규칙 CRUD
 * - 규칙 활성화/비활성화
 * - 레벨 설정 관리
 * - 중복 규칙 검증
 *
 *
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-13  김민서    1. 최초작성
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlarmRuleService {

    private final AlarmRuleMapper alarmRuleMapper;
    private final ObjectMapper objectMapper;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 알람 규칙 목록 조회
     */
    public AlarmRuleDto.ListResponse getRuleList(
            Long instanceId, Long databaseId, String metricType, Boolean enabled
    ) {
        List<AlarmRuleDto.RuleListRaw> rawList = alarmRuleMapper.selectRuleList(
                instanceId, databaseId, metricType, enabled);

        List<AlarmRuleDto.RuleItem> rules = rawList.stream()
                .map(raw -> AlarmRuleDto.RuleItem.builder()
                        .alarmRuleId(raw.getAlarmRuleId())
                        .instanceId(raw.getInstanceId())
                        .databaseId(raw.getDatabaseId())
                        .instanceName(raw.getInstanceName())
                        .databaseName(raw.getDatabaseName())
                        .section(raw.getSection())
                        .metricType(raw.getMetricType())
                        .enabled(raw.getEnabled())
                        .build())
                .collect(Collectors.toList());

        return AlarmRuleDto.ListResponse.builder()
                .rules(rules)
                .totalCount(rules.size())
                .build();
    }

    /**
     * 알람 규칙 상세 조회
     */
    public AlarmRuleDto.DetailResponse getRuleDetail(Long alarmRuleId) {
        AlarmRule rule = alarmRuleMapper.selectRuleDetail(alarmRuleId);
        if (rule == null) {
            throw new IllegalArgumentException("알람 규칙을 찾을 수 없습니다: " + alarmRuleId);
        }

        AlarmRuleDto.Levels levels = parseLevels(rule.getLevels());

        return AlarmRuleDto.DetailResponse.builder()
                .alarmRuleId(rule.getAlarmRuleId())
                .enabled(rule.getEnabled())
                .instanceId(rule.getInstanceId())
                .databaseId(rule.getDatabaseId())
                .instanceName(rule.getInstanceName() != null ? rule.getInstanceName() : "Unknown")
                .databaseName(rule.getDatabaseName() != null ? rule.getDatabaseName() : "Unknown")
                .section(getSectionFromMetric(rule.getMetricType()))
                .metricType(rule.getMetricType())
                .updatedAt(rule.getUpdatedAt() != null ? rule.getUpdatedAt().format(FORMATTER) : "")
                .levels(levels)
                .build();
    }

    /**
     * 알람 규칙 생성
     */
    @Transactional
    public Long createRule(AlarmRuleDto.CreateRequest request) {
        // 중복 체크
        int duplicateCount = alarmRuleMapper.countDuplicateRule(
                request.getInstanceId(),
                request.getDatabaseId(),
                request.getMetricType()
        );

        if (duplicateCount > 0) {
            throw new IllegalArgumentException("동일한 인스턴스, 데이터베이스, 지표를 가진 알람 규칙이 이미 존재합니다.");
        }

        // Levels JSON 변환
        String levelsJson = convertLevelsToJson(request.getLevels());

        AlarmRule rule = AlarmRule.builder()
                .instanceId(request.getInstanceId())
                .databaseId(request.getDatabaseId())
                .metricType(request.getMetricType())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .levels(levelsJson)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        alarmRuleMapper.insertRule(rule);

        return rule.getAlarmRuleId();
    }

    /**
     * 알람 규칙 수정
     */
    @Transactional
    public void updateRule(AlarmRuleDto.UpdateRequest request) {
        AlarmRule existing = alarmRuleMapper.selectRuleDetail(request.getAlarmRuleId());
        if (existing == null) {
            throw new IllegalArgumentException("알람 규칙을 찾을 수 없습니다: " + request.getAlarmRuleId());
        }

        String levelsJson = request.getLevels() != null
                ? convertLevelsToJson(request.getLevels())
                : null;

        AlarmRule rule = AlarmRule.builder()
                .alarmRuleId(request.getAlarmRuleId())
                .enabled(request.getEnabled() != null ? request.getEnabled() : existing.getEnabled())
                .operator(request.getOperator() != null ? request.getOperator() : existing.getOperator())
                .levels(levelsJson)
                .build();

        int updated = alarmRuleMapper.updateRuleLevels(rule);
        if (updated == 0) {
            throw new IllegalArgumentException("알람 규칙 수정에 실패했습니다: " + request.getAlarmRuleId());
        }
    }

    /**
     * 알람 규칙 삭제
     */
    @Transactional
    public void deleteRule(Long alarmRuleId) {
        int deleted = alarmRuleMapper.deleteRule(alarmRuleId);
        if (deleted == 0) {
            throw new IllegalArgumentException("알람 규칙을 찾을 수 없습니다: " + alarmRuleId);
        }
    }

    /**
     * 알람 규칙 활성화/비활성화
     */
    @Transactional
    public void toggleRuleEnabled(Long alarmRuleId, Boolean enabled) {
        int updated = alarmRuleMapper.updateRuleEnabled(alarmRuleId, enabled);
        if (updated == 0) {
            throw new IllegalArgumentException("알람 규칙을 찾을 수 없습니다: " + alarmRuleId);
        }
    }

    // ========== Private Helper 메서드 ==========

    private AlarmRuleDto.Levels parseLevels(String levelsJson) {
        if (levelsJson == null) {
            return getDefaultLevels();
        }

        try {
            return objectMapper.readValue(levelsJson, AlarmRuleDto.Levels.class);
        } catch (Exception e) {
            log.error("levels JSON 파싱 실패", e);
            return getDefaultLevels();
        }
    }

    private String convertLevelsToJson(AlarmRuleDto.Levels levels) {
        if (levels == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(levels);
        } catch (Exception e) {
            log.error("levels JSON 변환 실패", e);
            throw new RuntimeException("levels JSON 변환 실패", e);
        }
    }

    private AlarmRuleDto.Levels getDefaultLevels() {
        AlarmRuleDto.ThresholdLevel defaultLevel = new AlarmRuleDto.ThresholdLevel(
                BigDecimal.valueOf(100000), 1, 2, 15,
                BigDecimal.valueOf(80000), 2, 60
        );
        return new AlarmRuleDto.Levels(defaultLevel, defaultLevel, defaultLevel);
    }

    private String getSectionFromMetric(String metricType) {
        if (metricType == null) return "session";

        if (metricType.contains("vacuum") || metricType.contains("dead_tuples")) {
            return "vacuum";
        } else if (metricType.contains("bloat")) {
            return "bloat";
        } else if (metricType.contains("wal")) {
            return "wal";
        }
        return "session";
    }
}