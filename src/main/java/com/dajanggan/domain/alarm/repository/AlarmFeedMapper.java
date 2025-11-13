package com.dajanggan.domain.alarm.repository;

import com.dajanggan.domain.alarm.domain.AlarmFeed;
import com.dajanggan.domain.alarm.dto.AlarmFeedDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
            @Param("alarmHistoryId") Long alarmHistoryId
    );

    // 관련 객체 조회
    List<AlarmFeedDto.RelatedObjectRaw> selectRelatedObjects(
            @Param("alarmHistoryId") Long alarmHistoryId
    );

    // 알림 읽음 처리
    int updateAlarmRead(@Param("alarmFeedId") Long alarmFeedId);

    // 알림 확인 처리
    int updateAlarmAcknowledged(@Param("alarmFeedId") Long alarmFeedId);

    // 알림 삭제
    int deleteAlarm(@Param("alarmFeedId") Long alarmFeedId);

    // 미확인 알림 개수
    int countUnreadAlarms(@Param("instanceId") Long instanceId);
}
