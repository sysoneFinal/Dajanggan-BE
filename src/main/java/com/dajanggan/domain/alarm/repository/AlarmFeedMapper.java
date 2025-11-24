package com.dajanggan.domain.alarm.repository;

import com.dajanggan.domain.alarm.domain.AlarmFeed;
import com.dajanggan.domain.alarm.dto.AlarmFeedDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface AlarmFeedMapper {

    // 알림 목록 조회
    List<AlarmFeedDto.AlarmListRaw> selectAlarmList(
            @Param("instanceId") Long instanceId,
            @Param("databaseId") Long databaseId,
            @Param("severityLevel") String severityLevel,
            @Param("isRead") Boolean isRead
    );

    // 알림 상세 조회
    AlarmFeed selectAlarmDetail(@Param("alarmFeedId") Long alarmFeedId);

    // Latency 데이터 조회 (24시간)
    List<AlarmFeedDto.LatencyDataRaw> selectLatencyData(
            @Param("alarmFeedId") Long alarmFeedId
    );

    // 관련 객체 조회
    List<AlarmFeedDto.RelatedObjectRaw> selectRelatedObjects(
            @Param("alarmFeedId") Long alarmFeedId
    );

    // 알림 읽음 처리
    int updateAlarmRead(@Param("alarmFeedId") Long alarmFeedId);

    // 알림 확인 처리
    int updateAlarmAcknowledged(@Param("alarmFeedId") Long alarmFeedId);

    // 알림 삭제
    int deleteAlarm(@Param("alarmFeedId") Long alarmFeedId);

    // 알람 메트릭 히스토리 삭제 (알람 삭제 전에 호출)
    int deleteMetricHistoryByFeedId(@Param("alarmFeedId") Long alarmFeedId);

    // 알람 관련 객체 삭제 (알람 삭제 전에 호출)
    int deleteRelatedObjectsByFeedId(@Param("alarmFeedId") Long alarmFeedId);

    // 미확인 알림 개수
    int countUnreadAlarms(@Param("instanceId") Long instanceId);

    // AlarmFeed 생성 (알람 발생 시)
    void insertAlarmFeed(AlarmFeed alarmFeed);

    // 메트릭 히스토리 저장
    void insertMetricHistory(
            @Param("alarmFeedId") Long alarmFeedId,
            @Param("metricValue") BigDecimal metricValue,
            @Param("recordedAt") OffsetDateTime recordedAt
    );

    void updateAlarmFeed(AlarmFeed alarmFeed);

    // 히스토리 ID로 해제 처리
    void resolveByFeedId(@Param("alarmFeedId") Long alarmFeedId);

     // 관련 객체 저장
    void insertRelatedObject(
            @Param("alarmFeedId") Long alarmFeedId,
            @Param("alarmRuleId") Long alarmRuleId,  // 추가
            @Param("objectType") String objectType,
            @Param("objectName") String objectName,
            @Param("metricValue") BigDecimal metricValue,
            @Param("status") String status
    );

    Long selectLatestFeedIdByTrackingId(Long alarmTrackingId);

    // 마지막 알람 발생 시간 조회 (쿨다운 체크용 - 같은 레벨만)
    OffsetDateTime selectLastFiredAtByRuleId(
            @Param("alarmRuleId") Long alarmRuleId,
            @Param("severityLevel") String severityLevel
    );

    // ruleId, instanceId, databaseId, level 기준으로 해결되지 않은 Feed 조회 (중복 체크용)
    Long selectUnresolvedFeedIdByRule(
            @Param("alarmRuleId") Long alarmRuleId,
            @Param("instanceId") Long instanceId,
            @Param("databaseId") Long databaseId,
            @Param("severityLevel") String severityLevel
    );
}
