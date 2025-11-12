package com.dajanggan.domain.alarm.service;

import com.dajanggan.domain.alarm.domain.AlarmRule;
import com.dajanggan.domain.alarm.dto.AlarmRuleDto;
import com.dajanggan.domain.alarm.repository.AlarmRuleMapper;
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
                        .targetInstanceId(raw.getInstanceId())
                        .targetDatabaseId(raw.getDatabaseId())
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

        // 임시로 기본값 설정 (실제로는 DB에서 가져오거나 JSON 파싱 필요)
        AlarmRuleDto.ThresholdLevel defaultLevel = new AlarmRuleDto.ThresholdLevel(
                rule.getThresholdValue() != null ? rule.getThresholdValue() : BigDecimal.ZERO,
                5, 1, 10
        );

        AlarmRuleDto.Levels levels = new AlarmRuleDto.Levels(
                defaultLevel,
                defaultLevel,
                defaultLevel
        );

        String updatedAt = rule.getUpdatedAt() != null
                ? rule.getUpdatedAt().format(FORMATTER)
                : "";

        return AlarmRuleDto.DetailResponse.builder()
                .alarmRuleId(rule.getAlarmRuleId())
                .enabled(rule.getEnabled())
                .targetInstanceId(rule.getInstanceId())
                .targetDatabaseId(rule.getDatabaseId())
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
        // levels에서 가장 높은 임계값 사용 (critical)
        BigDecimal thresholdValue = request.getLevels() != null
                && request.getLevels().getCritical() != null
                ? request.getLevels().getCritical().getThreshold()
                : BigDecimal.valueOf(1000000);

        AlarmRule rule = AlarmRule.builder()
                .instanceId(request.getTargetInstanceId())
                .databaseId(request.getTargetDatabaseId())
                .metricType(request.getMetricType())
                .timeWindow("15m")
                .aggregationType(request.getAggregationType())
                .operator(">")
                .thresholdValue(thresholdValue)
                .severityLevel("CRITICAL")
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        alarmRuleMapper.insertRule(rule);

        // TODO: levels 정보를 별도 테이블이나 JSON 컬럼에 저장

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

        BigDecimal thresholdValue = request.getLevels() != null
                && request.getLevels().getCritical() != null
                ? request.getLevels().getCritical().getThreshold()
                : existing.getThresholdValue();

        AlarmRule rule = AlarmRule.builder()
                .alarmRuleId(request.getAlarmRuleId())
                .enabled(request.getEnabled() != null ? request.getEnabled() : existing.getEnabled())
                .aggregationType(request.getAggregationType() != null ? request.getAggregationType() : existing.getAggregationType())
                .thresholdValue(thresholdValue)
                .updatedAt(OffsetDateTime.now())
                .build();

        int updated = alarmRuleMapper.updateRule(rule);
        if (updated == 0) {
            throw new IllegalArgumentException("알림 규칙 수정에 실패했습니다: " + request.getAlarmRuleId());
        }

        log.info("알림 규칙 수정 완료 - alarmRuleId: {}", request.getAlarmRuleId());
        // TODO: levels 업데이트
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