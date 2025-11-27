package com.dajanggan.domain.alarm.repository;

import com.dajanggan.domain.alarm.domain.AlarmTracking;
import com.dajanggan.domain.alarm.dto.AlarmTrackingDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AlarmTracking Repository
 *
 * 테이블: alarm_tracking
 *
 * 주요 책임:
 * - 알람 추적 상태 관리
 * - 연속 발생 횟수 추적
 * - 알람 상태 전환 (PENDING, FIRED, RESOLVED)
 *
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-13  김민서    1. 최초작성

 */
@Mapper
public interface AlarmTrackingMapper {

    // ========================================================================
    // 조회 (DTO 반환)
    // ========================================================================

    /**
     * 추적 상태 목록 조회
     *
     * 반환 타입: AlarmTrackingDto.TrackingStatusRaw (DTO)
     * 용도: GET /api/alarms/tracking
     *
     * @param instanceId 인스턴스 ID (선택)
     * @param status 상태 (PENDING, FIRED, RESOLVED) (선택)
     * @return 추적 상태 목록
     */
    List<AlarmTrackingDto.TrackingStatusRaw> selectTrackingStatus(
            @Param("instanceId") Long instanceId,
            @Param("status") String status
    );

    /**
     * 추적 조회 (ID)
     *
     * 반환 타입: AlarmTracking (Entity)
     * 용도: Service 내부 사용
     *
     * @param alarmTrackingId 알람 추적 ID
     * @return 알람 추적 (없으면 null)
     */
    AlarmTracking selectById(@Param("alarmTrackingId") Long alarmTrackingId);

    /**
     * 규칙으로 추적 조회
     *
     * 반환 타입: AlarmTracking (Entity)
     * 용도: 메트릭 수집 시 기존 추적 조회
     *
     * 조건: alarmRuleId + instanceId + databaseId 일치
     *
     * @param alarmRuleId 알람 규칙 ID
     * @param instanceId 인스턴스 ID
     * @param databaseId 데이터베이스 ID
     * @return 알람 추적 (없으면 null)
     */
    AlarmTracking selectByRule(
            @Param("alarmRuleId") Long alarmRuleId,
            @Param("instanceId") Long instanceId,
            @Param("databaseId") Long databaseId
    );

    /**
     * 규칙 ID로 추적 조회
     *
     * 반환 타입: AlarmTracking (Entity)
     * 용도: 규칙 ID로 추적 조회
     *
     * @param alarmRuleId 알람 규칙 ID
     * @return 알람 추적 (없으면 null)
     */
    AlarmTracking findByRuleId(@Param("alarmRuleId") Long alarmRuleId);

    // ========================================================================
    // 생성
    // ========================================================================

    /**
     * 추적 생성
     *
     * 용도: 메트릭 수집 시 자동 생성
     *
     * 주의: useGeneratedKeys로 alarmTrackingId 자동 설정
     *
     * @param alarmTracking 알람 추적 Entity
     */
    void insertTracking(AlarmTracking alarmTracking);

    // ========================================================================
    // 수정
    // ========================================================================

    /**
     * 추적 업데이트
     *
     * 용도: 상태 변경, 연속 횟수 증가 등
     *
     * 수정 항목:
     * - consecutiveCount
     * - currentValue
     * - currentLevel
     * - status
     * - lastCheckedAt
     *
     * @param alarmTracking 알람 추적 Entity
     */
    void updateTracking(AlarmTracking alarmTracking);

    // ========================================================================
    // 삭제
    // ========================================================================

    /**
     * 추적 삭제
     *
     * 용도: 규칙 삭제 시 연관 삭제
     *
     * @param alarmTrackingId 알람 추적 ID
     * @return 영향받은 행 수
     */
    int delete(@Param("alarmTrackingId") Long alarmTrackingId);
}
