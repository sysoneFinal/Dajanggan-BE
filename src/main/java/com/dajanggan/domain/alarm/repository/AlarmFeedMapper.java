package com.dajanggan.domain.alarm.repository;

import com.dajanggan.domain.alarm.domain.AlarmFeed;
import com.dajanggan.domain.alarm.dto.AlarmFeedDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * AlarmFeed Repository
 *
 * 테이블: alarm_feed, alarm_metric_history, alarm_related_objects
 *
 * 주요 책임:
 * - 알람 피드 CRUD
 * - 메트릭 히스토리 관리
 * - 관련 객체 관리
 * - 읽음/확인 상태 관리
 *
 *
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-13  김민서    1. 최초작성
 *
 */
@Mapper
public interface AlarmFeedMapper {

    // ========================================================================
    // 조회 (DTO 반환)
    // ========================================================================

    /**
     * 알람 목록 조회
     *
     * 반환 타입: AlarmFeedDto.AlarmListRaw (DTO)
     * 용도: GET /api/alarms/feeds
     *
     * @param instanceId 인스턴스 ID (선택)
     * @param databaseId 데이터베이스 ID (선택)
     * @param severityLevel 심각도 (선택)
     * @param isRead 읽음 여부 (선택)
     * @return 알람 목록 (최대 100개, 최신순)
     */
    List<AlarmFeedDto.AlarmListRaw> selectAlarmList(
            @Param("instanceId") Long instanceId,
            @Param("databaseId") Long databaseId,
            @Param("severityLevel") String severityLevel,
            @Param("isRead") Boolean isRead
    );

    /**
     * 알람 상세 조회
     *
     * 반환 타입: AlarmFeed (Entity)
     * 용도: GET /api/alarms/feeds/{id}
     *
     * 주의: Service 내부에서만 사용, DTO로 변환 필요
     *
     * @param alarmFeedId 알람 피드 ID
     * @return 알람 피드 (없으면 null)
     */
    AlarmFeed selectAlarmDetail(@Param("alarmFeedId") Long alarmFeedId);

    /**
     * 레이턴시 데이터 조회 (24시간)
     *
     * 반환 타입: AlarmFeedDto.LatencyDataRaw (DTO)
     * 용도: 알람 상세 화면 차트
     *
     * 성능 최적화:
     * - DATE_TRUNC 사용 (인덱스 활용 가능)
     * - 24시간 제한
     *
     * @param alarmFeedId 알람 피드 ID
     * @return 시간별 평균 레이턴시
     */
    List<AlarmFeedDto.LatencyDataRaw> selectLatencyData(
            @Param("alarmFeedId") Long alarmFeedId
    );

    /**
     * 관련 객체 조회
     *
     * 반환 타입: AlarmFeedDto.RelatedObjectRaw (DTO)
     * 용도: 알람과 관련된 세션/쿼리 표시
     *
     * @param alarmFeedId 알람 피드 ID
     * @return 관련 객체 목록 (메트릭 값 내림차순)
     */
    List<AlarmFeedDto.RelatedObjectRaw> selectRelatedObjects(
            @Param("alarmFeedId") Long alarmFeedId
    );

    /**
     * 미확인 알람 개수 조회
     *
     * 용도: 뱃지 표시, 알림 카운트
     *
     * @param instanceId 인스턴스 ID
     * @return 읽지 않은 알람 개수
     */
    int countUnreadAlarms(@Param("instanceId") Long instanceId);

    /**
     * 트래킹 ID로 최신 Feed ID 조회
     *
     * 조건: 해결되지 않은 Feed만
     * 용도: 알람 해제 처리
     *
     * @param alarmTrackingId 알람 추적 ID
     * @return 최신 Feed ID (없으면 null)
     */
    Long selectLatestFeedIdByTrackingId(@Param("alarmTrackingId") Long alarmTrackingId);

    /**
     * 마지막 알람 발생 시간 조회 (쿨다운 체크용)
     *
     * 조건: 같은 레벨, 해결되지 않은 Feed만
     * 용도: 알람 재발생 방지
     *
     * @param alarmRuleId 알람 규칙 ID
     * @param severityLevel 심각도
     * @return 마지막 발생 시간 (없으면 null)
     */
    OffsetDateTime selectLastFiredAtByRuleId(
            @Param("alarmRuleId") Long alarmRuleId,
            @Param("severityLevel") String severityLevel
    );

    /**
     * 해결되지 않은 Feed 조회 (중복 체크용)
     *
     * 조건: ruleId + instanceId + databaseId + level 일치
     * 용도: 중복 Feed 생성 방지
     *
     * @param alarmRuleId 알람 규칙 ID
     * @param instanceId 인스턴스 ID
     * @param databaseId 데이터베이스 ID
     * @param severityLevel 심각도
     * @return Feed ID (없으면 null)
     */
    Long selectUnresolvedFeedIdByRule(
            @Param("alarmRuleId") Long alarmRuleId,
            @Param("instanceId") Long instanceId,
            @Param("databaseId") Long databaseId,
            @Param("severityLevel") String severityLevel
    );

    // ========================================================================
    // 생성
    // ========================================================================

    /**
     * 알람 피드 생성
     *
     * 주의: useGeneratedKeys로 alarmFeedId 자동 설정
     *
     * @param alarmFeed 알람 피드 Entity
     */
    void insertAlarmFeed(AlarmFeed alarmFeed);

    /**
     * 메트릭 히스토리 저장
     *
     * 용도: 차트 데이터 수집
     *
     * @param alarmFeedId 알람 피드 ID
     * @param metricValue 메트릭 값
     * @param recordedAt 기록 시간
     */
    void insertMetricHistory(
            @Param("alarmFeedId") Long alarmFeedId,
            @Param("metricValue") BigDecimal metricValue,
            @Param("recordedAt") OffsetDateTime recordedAt
    );

    /**
     * 관련 객체 저장
     *
     * 용도: 알람 발생 시 관련 세션/쿼리 저장
     *
     * @param alarmFeedId 알람 피드 ID
     * @param alarmRuleId 알람 규칙 ID
     * @param objectType 객체 타입 (session, query 등)
     * @param objectName 객체 이름
     * @param metricValue 메트릭 값
     * @param status 상태
     */
    void insertRelatedObject(
            @Param("alarmFeedId") Long alarmFeedId,
            @Param("alarmRuleId") Long alarmRuleId,
            @Param("objectType") String objectType,
            @Param("objectName") String objectName,
            @Param("metricValue") BigDecimal metricValue,
            @Param("status") String status
    );

    // ========================================================================
    // 수정
    // ========================================================================

    /**
     * 알람 읽음 처리
     *
     * 용도: PATCH /api/alarms/feeds/{id}/read
     *
     * @param alarmFeedId 알람 피드 ID
     * @return 영향받은 행 수
     */
    int markAsRead(@Param("alarmFeedId") Long alarmFeedId);

    /**
     * 알람 확인 처리
     *
     * 용도: 사용자가 알람을 확인했음을 표시
     *
     * @param alarmFeedId 알람 피드 ID
     * @return 영향받은 행 수
     */
    int updateAlarmAcknowledged(@Param("alarmFeedId") Long alarmFeedId);

    /**
     * 알람 해제 처리
     *
     * 용도: 임계값 정상화 시 알람 자동 해제
     *
     * @param alarmFeedId 알람 피드 ID
     */
    void resolveByFeedId(@Param("alarmFeedId") Long alarmFeedId);

    // ========================================================================
    // 삭제
    // ========================================================================

    /**
     * 알람 삭제
     *
     * 용도: DELETE /api/alarms/feeds/{id}
     *
     * 주의: 삭제 전에 관련 데이터 먼저 삭제 필요
     * 1. deleteMetricHistoryByFeedId
     * 2. deleteRelatedObjectsByFeedId
     * 3. deleteAlarm
     *
     * @param alarmFeedId 알람 피드 ID
     * @return 영향받은 행 수
     */
    int deleteAlarm(@Param("alarmFeedId") Long alarmFeedId);

    /**
     * 메트릭 히스토리 삭제
     *
     * 용도: 알람 삭제 전 관련 데이터 정리
     *
     * @param alarmFeedId 알람 피드 ID
     * @return 영향받은 행 수
     */
    int deleteMetricHistoryByFeedId(@Param("alarmFeedId") Long alarmFeedId);

    /**
     * 관련 객체 삭제
     *
     * 용도: 알람 삭제 전 관련 데이터 정리
     *
     * @param alarmFeedId 알람 피드 ID
     * @return 영향받은 행 수
     */
    int deleteRelatedObjectsByFeedId(@Param("alarmFeedId") Long alarmFeedId);
}
