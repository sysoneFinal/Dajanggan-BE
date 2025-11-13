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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlarmRuleService {

    private final AlarmRuleMapper alarmRuleMapper;
    private final ObjectMapper objectMapper;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 알림 규칙 목록 조회
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
     * 알림 규칙 상세 조회
     */
    public AlarmRuleDto.DetailResponse getRuleDetail(Long alarmRuleId) {
        AlarmRule rule = alarmRuleMapper.selectRuleDetail(alarmRuleId);
        if (rule == null) {
            throw new IllegalArgumentException("알림 규칙을 찾을 수 없습니다: " + alarmRuleId);
        }

        AlarmRuleDto.Levels levels = null;
        if (rule.getLevels() != null) {
            try {
                levels = objectMapper.readValue(rule.getLevels(), AlarmRuleDto.Levels.class);
            } catch (Exception e) {
                log.error("levels JSON 파싱 실패", e);
                // 파싱 실패시 기본값
                AlarmRuleDto.ThresholdLevel defaultLevel = new AlarmRuleDto.ThresholdLevel(
                        BigDecimal.valueOf(100000), 1, 2, 15
                );
                levels = new AlarmRuleDto.Levels(defaultLevel, defaultLevel, defaultLevel);
            }
        } else {
            // levels가 null인 경우 기본값
            AlarmRuleDto.ThresholdLevel defaultLevel = new AlarmRuleDto.ThresholdLevel(
                    BigDecimal.valueOf(100000), 1, 2, 15
            );
            levels = new AlarmRuleDto.Levels(defaultLevel, defaultLevel, defaultLevel);
        }

        String updatedAt = rule.getUpdatedAt() != null
                ? rule.getUpdatedAt().format(FORMATTER)
                : "";

        return AlarmRuleDto.DetailResponse.builder()
                .alarmRuleId(rule.getAlarmRuleId())
                .enabled(rule.getEnabled())
                .instanceId(rule.getInstanceId())
                .databaseId(rule.getDatabaseId())
                .instanceName(rule.getInstanceName() != null ? rule.getInstanceName() : "Unknown")
                .databaseName(rule.getDatabaseName() != null ? rule.getDatabaseName() : "Unknown")
                .section(getSectionFromMetric(rule.getMetricType()))
                .metricType(rule.getMetricType())
                .aggregationType(rule.getAggregationType())
                .updatedAt(updatedAt)
                .levels(levels)
                .build();
    }
    /**
     * 알림 규칙 생성
     */
    @Transactional
    public Long createRule(AlarmRuleDto.CreateRequest request) {
        // levels를 JSON 문자열로 변환
        String levelsJson = null;
        if (request.getLevels() != null) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                levelsJson = objectMapper.writeValueAsString(request.getLevels());
            } catch (Exception e) {
                log.error("levels JSON 변환 실패", e);
                throw new RuntimeException("levels JSON 변환 실패", e);
            }
        }

        // 기본값 설정 (levels가 null인 경우)
        if (levelsJson == null) {
            levelsJson = "{\"notice\":{\"threshold\":100000,\"minDurationMin\":1,\"occurCount\":2,\"windowMin\":15},"
                    + "\"warning\":{\"threshold\":500000,\"minDurationMin\":5,\"occurCount\":2,\"windowMin\":15},"
                    + "\"critical\":{\"threshold\":1000000,\"minDurationMin\":10,\"occurCount\":1,\"windowMin\":10}}";
        }

        AlarmRule rule = AlarmRule.builder()
                .instanceId(request.getInstanceId())
                .databaseId(request.getDatabaseId())
                .metricType(request.getMetricType())
                .aggregationType(request.getAggregationType())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .levels(levelsJson)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        alarmRuleMapper.insertRule(rule);

        return rule.getAlarmRuleId();
    }
    /**
     * 알림 규칙 수정
     */
    @Transactional
    public void updateRule(AlarmRuleDto.UpdateRequest request) {
        AlarmRule existing = alarmRuleMapper.selectRuleDetail(request.getAlarmRuleId());
        if (existing == null) {
            throw new IllegalArgumentException("알림 규칙을 찾을 수 없습니다: " + request.getAlarmRuleId());
        }

        // ⭐ levels를 JSON 문자열로 변환
        String levelsJson = null;
        if (request.getLevels() != null) {
            try {
                levelsJson = objectMapper.writeValueAsString(request.getLevels());
            } catch (Exception e) {
                log.error("levels JSON 변환 실패", e);
                throw new RuntimeException("levels JSON 변환 실패", e);
            }
        }

        // ⭐ updateRuleLevels 호출 (updateRule이 아닌!)
        AlarmRule rule = AlarmRule.builder()
                .alarmRuleId(request.getAlarmRuleId())
                .enabled(request.getEnabled() != null ? request.getEnabled() : existing.getEnabled())
                .aggregationType(request.getAggregationType() != null ? request.getAggregationType() : existing.getAggregationType())
                .levels(levelsJson)
                .build();

        int updated = alarmRuleMapper.updateRuleLevels(rule);
        if (updated == 0) {
            throw new IllegalArgumentException("알림 규칙 수정에 실패했습니다: " + request.getAlarmRuleId());
        }

        log.info("알림 규칙 수정 완료 - alarmRuleId: {}", request.getAlarmRuleId());
    }

    /**
     * 알림 규칙 삭제
     */
    @Transactional
    public void deleteRule(Long alarmRuleId) {
        int deleted = alarmRuleMapper.deleteRule(alarmRuleId);
        if (deleted == 0) {
            throw new IllegalArgumentException("알림 규칙을 찾을 수 없습니다: " + alarmRuleId);
        }
    }

    /**
     * 알림 규칙 활성화/비활성화
     */
    @Transactional
    public void toggleRuleEnabled(Long alarmRuleId, Boolean enabled) {
        int updated = alarmRuleMapper.updateRuleEnabled(alarmRuleId, enabled);
        if (updated == 0) {
            throw new IllegalArgumentException("알림 규칙을 찾을 수 없습니다: " + alarmRuleId);
        }
    }

    /**
     * Metric으로부터 Section 추출
     */
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