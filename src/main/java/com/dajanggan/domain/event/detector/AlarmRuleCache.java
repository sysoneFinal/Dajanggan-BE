package com.dajanggan.domain.event.detector;

import com.dajanggan.domain.alarm.domain.AlarmRule;
import com.dajanggan.domain.alarm.dto.AlarmRuleDto;
import com.dajanggan.domain.alarm.repository.AlarmRuleMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AlarmRuleCache {

    private static final int CACHE_DURATION_MINUTES = 10;

    // 인스턴스별 알람 룰 캐시
    private final ConcurrentHashMap<Long, CachedRules> ruleCache = new ConcurrentHashMap<>();

    private final AlarmRuleMapper alarmRuleMapper;
    private final ObjectMapper objectMapper;

    public AlarmRuleCache(AlarmRuleMapper alarmRuleMapper, ObjectMapper objectMapper) {
        this.alarmRuleMapper = alarmRuleMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 인스턴스의 모든 활성화된 알람 룰 조회 (캐시 우선)
     */
    public List<RuleWithLevels> getRules(Long instanceId) {
        CachedRules cached = ruleCache.get(instanceId);
        OffsetDateTime now = OffsetDateTime.now();

        if (cached != null && cached.getExpireAt().isAfter(now)) {
            log.debug("Cache hit - Using cached alarm rules for instance: {}", instanceId);
            return cached.getRules();
        }

        log.info("Cache miss - Loading alarm rules from DB for instance: {}", instanceId);
        List<RuleWithLevels> rules = loadRulesFromDb(instanceId);

        ruleCache.put(instanceId, new CachedRules(
                rules,
                now.plusMinutes(CACHE_DURATION_MINUTES)
        ));

        return rules;
    }

    /**
     * 특정 데이터베이스의 알람 룰만 필터링
     */
    public List<RuleWithLevels> getRulesByDatabase(Long instanceId, Long databaseId) {
        List<RuleWithLevels> allRules = getRules(instanceId);

        return allRules.stream()
                .filter(rule -> rule.getDatabaseId() == null || rule.getDatabaseId().equals(databaseId))
                .collect(Collectors.toList());
    }

    /**
     * 특정 메트릭 타입의 알람 룰 조회
     */
    public Optional<RuleWithLevels> getRuleByMetricType(Long instanceId, Long databaseId, String metricType) {
        List<RuleWithLevels> rules = getRulesByDatabase(instanceId, databaseId);

        return rules.stream()
                .filter(rule -> metricType.equals(rule.getMetricType()))
                .findFirst();
    }

    /**
     * DB에서 알람 룰 로드
     */
    private List<RuleWithLevels> loadRulesFromDb(Long instanceId) {
        try {
            // 세션 관련 메트릭 타입들
            List<String> sessionMetricTypes = Arrays.asList(
                    "LOCK_WAIT",
                    "LONG_TRANSACTION",
                    "CONNECTION_HIGH_USAGE",
                    "TOO_MANY_WAITING",
                    "IDLE_IN_TRANSACTION_SURGE"
            );

            List<RuleWithLevels> allRules = new ArrayList<>();

            // 각 메트릭 타입별로 조회 (MyBatis 쿼리가 metricType 필수라서)
            for (String metricType : sessionMetricTypes) {
                List<AlarmRule> rules = alarmRuleMapper.findActiveRules(instanceId, null, metricType);

                for (AlarmRule rule : rules) {
                    RuleWithLevels ruleWithLevels = convertToRuleWithLevels(rule);
                    if (ruleWithLevels != null) {
                        allRules.add(ruleWithLevels);
                    }
                }
            }

            return allRules;
        } catch (Exception e) {
            log.error("Failed to load alarm rules for instance: {}", instanceId, e);
            return Collections.emptyList();
        }
    }

    /**
     * AlarmRule 엔티티를 RuleWithLevels로 변환
     */
    private RuleWithLevels convertToRuleWithLevels(AlarmRule rule) {
        try {
            // JSON 문자열을 Levels 객체로 파싱
            AlarmRuleDto.Levels levels = null;
            if (rule.getLevels() != null && !rule.getLevels().trim().isEmpty()) {
                levels = objectMapper.readValue(rule.getLevels(), AlarmRuleDto.Levels.class);
            }

            return new RuleWithLevels(
                    rule.getAlarmRuleId(),
                    rule.getInstanceId(),
                    rule.getDatabaseId(),
                    rule.getMetricType(),
                    levels
            );
        } catch (Exception e) {
            log.error("Failed to parse levels JSON for rule {}: {}", rule.getAlarmRuleId(), e.getMessage());
            return null;
        }
    }

    /**
     * 캐시 무효화
     */
    public void invalidate(Long instanceId) {
        ruleCache.remove(instanceId);
        log.info("Alarm rule cache invalidated for instance: {}", instanceId);
    }

    /**
     * 전체 캐시 무효화
     */
    public void invalidateAll() {
        ruleCache.clear();
        log.info("All alarm rule cache invalidated");
    }

    /**
     * 만료된 캐시 정리
     */
    public void cleanupExpired() {
        OffsetDateTime now = OffsetDateTime.now();
        int removedCount = 0;

        for (Map.Entry<Long, CachedRules> entry : ruleCache.entrySet()) {
            if (entry.getValue().getExpireAt().isBefore(now)) {
                ruleCache.remove(entry.getKey());
                removedCount++;
            }
        }

        if (removedCount > 0) {
            log.debug("Cleaned up {} expired alarm rule cache entries", removedCount);
        }
    }

    /**
     * 캐시용 간소화된 룰 데이터
     */
    @Data
    @AllArgsConstructor
    public static class RuleWithLevels {
        private Long alarmRuleId;
        private Long instanceId;
        private Long databaseId;
        private String metricType;
        private AlarmRuleDto.Levels levels;
    }

    @Data
    @AllArgsConstructor
    private static class CachedRules {
        private List<RuleWithLevels> rules;
        private OffsetDateTime expireAt;
    }
}