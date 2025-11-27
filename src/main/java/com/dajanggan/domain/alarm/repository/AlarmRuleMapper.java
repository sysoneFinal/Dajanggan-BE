package com.dajanggan.domain.alarm.repository;

import com.dajanggan.domain.alarm.domain.AlarmRule;
import com.dajanggan.domain.alarm.dto.AlarmRuleDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AlarmRule Repository
 *
 * 테이블: alarm_rule
 *
 * 주요 책임:
 * - 알람 규칙 CRUD
 * - 활성 규칙 조회
 * - 중복 규칙 검증
 *
 *
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-13  김민서    1. 최초작성
 *
 */
@Mapper
public interface AlarmRuleMapper {

    // ========================================================================
    // 조회
    // ========================================================================

    /**
     * 알람 규칙 목록 조회
     *
     * 반환 타입: AlarmRuleDto.RuleListRaw (DTO)
     * 용도: GET /api/alarms/rules
     *
     * @param instanceId 인스턴스 ID (선택)
     * @param databaseId 데이터베이스 ID (선택)
     * @param metricType 메트릭 타입 (선택)
     * @param enabled 활성화 여부 (선택)
     * @return 규칙 목록
     */
    List<AlarmRuleDto.RuleListRaw> selectRuleList(
            @Param("instanceId") Long instanceId,
            @Param("databaseId") Long databaseId,
            @Param("metricType") String metricType,
            @Param("enabled") Boolean enabled
    );

    /**
     * 알람 규칙 상세 조회
     *
     * 반환 타입: AlarmRule (Entity)
     * 용도: GET /api/alarms/rules/{id}
     *
     * 주의: Service 내부에서만 사용, DTO로 변환 필요
     *
     * @param alarmRuleId 알람 규칙 ID
     * @return 알람 규칙 (없으면 null)
     */
    AlarmRule selectRuleDetail(@Param("alarmRuleId") Long alarmRuleId);

    /**
     * 활성화된 규칙 조회
     *
     * 반환 타입: AlarmRule (Entity)
     * 용도: 메트릭 수집 시 체크할 규칙 조회
     *
     * 조건:
     * - enabled = true
     * - instanceId, databaseId, metricType 일치
     *
     * @param instanceId 인스턴스 ID
     * @param databaseId 데이터베이스 ID
     * @param metricType 메트릭 타입
     * @return 활성 규칙 목록
     */
    List<AlarmRule> findActiveRules(
            @Param("instanceId") Long instanceId,
            @Param("databaseId") Long databaseId,
            @Param("metricType") String metricType
    );

    /**
     * 중복 규칙 개수 확인
     *
     * 용도: 규칙 생성 시 중복 방지
     *
     * 조건: instanceId + databaseId + metricType 동일
     *
     * @param instanceId 인스턴스 ID
     * @param databaseId 데이터베이스 ID
     * @param metricType 메트릭 타입
     * @return 중복 개수 (0이면 중복 없음)
     */
    int countDuplicateRule(
            @Param("instanceId") Long instanceId,
            @Param("databaseId") Long databaseId,
            @Param("metricType") String metricType
    );

    // ========================================================================
    // 생성
    // ========================================================================

    /**
     * 알람 규칙 생성
     *
     * 용도: POST /api/alarms/rules
     *
     * 주의: useGeneratedKeys로 alarmRuleId 자동 설정
     *
     * @param alarmRule 알람 규칙 Entity
     * @return 영향받은 행 수
     */
    int insertRule(AlarmRule alarmRule);

    // ========================================================================
    // 수정
    // ========================================================================

    /**
     * 알람 규칙 레벨 수정
     *
     * 용도: PUT /api/alarms/rules/{id}
     *
     * 수정 항목:
     * - enabled
     * - aggregationType
     * - operator
     * - levels (JSON)
     *
     * @param alarmRule 알람 규칙 Entity
     * @return 영향받은 행 수
     */
    int updateRuleLevels(AlarmRule alarmRule);

    /**
     * 알람 규칙 활성화/비활성화
     *
     * 용도: PATCH /api/alarms/rules/{id}/enabled
     *
     * @param alarmRuleId 알람 규칙 ID
     * @param enabled 활성화 여부
     * @return 영향받은 행 수
     */
    int updateRuleEnabled(
            @Param("alarmRuleId") Long alarmRuleId,
            @Param("enabled") Boolean enabled
    );

    // ========================================================================
    // 삭제
    // ========================================================================

    /**
     * 알람 규칙 삭제
     *
     * 용도: DELETE /api/alarms/rules/{id}
     *
     * 주의: 관련 Tracking도 함께 삭제됨 (CASCADE)
     *
     * @param alarmRuleId 알람 규칙 ID
     * @return 영향받은 행 수
     */
    int deleteRule(@Param("alarmRuleId") Long alarmRuleId);
}
