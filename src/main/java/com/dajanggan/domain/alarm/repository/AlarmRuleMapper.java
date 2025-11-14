package com.dajanggan.domain.alarm.repository;

import com.dajanggan.domain.alarm.domain.AlarmRule;
import com.dajanggan.domain.alarm.dto.AlarmRuleDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AlarmRuleMapper {

    // 규칙 목록 조회
    List<AlarmRuleDto.RuleListRaw> selectRuleList(
            @Param("instanceId") Long instanceId,
            @Param("databaseId") Long databaseId,
            @Param("metricType") String metricType,
            @Param("enabled") Boolean enabled
    );

    // 규칙 상세 조회
    AlarmRule selectRuleDetail(@Param("alarmRuleId") Long alarmRuleId);

    // 규칙 생성
    int insertRule(AlarmRule alarmRule);

    // 규칙 수정
    int updateRuleLevels(AlarmRule alarmRule);

    // 규칙 삭제
    int deleteRule(@Param("alarmRuleId") Long alarmRuleId);

    // 규칙 활성화/비활성화
    int updateRuleEnabled(
            @Param("alarmRuleId") Long alarmRuleId,
            @Param("enabled") Boolean enabled
    );

    // 활성화된 규칙 조회
    List<AlarmRule> findActiveRules(
            @Param("instanceId") Long instanceId,
            @Param("databaseId") Long databaseId,
            @Param("metricType") String metricType
    );
}