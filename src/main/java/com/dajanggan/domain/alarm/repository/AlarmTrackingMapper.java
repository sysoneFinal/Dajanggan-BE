package com.dajanggan.domain.alarm.repository;

import com.dajanggan.domain.alarm.domain.AlarmTracking;
import com.dajanggan.domain.alarm.dto.AlarmTrackingDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AlarmTrackingMapper {

    // 트래킹 상태 조회
    List<AlarmTrackingDto.TrackingStatusRaw> selectTrackingStatus(
            @Param("instanceId") Long instanceId,
            @Param("status") String status
    );

    // 트래킹 상세 조회
    AlarmTracking selectTrackingDetail(@Param("alarmTrackingId") Long alarmTrackingId);

    // 트래킹 생성
    int insertTracking(AlarmTracking alarmTracking);

    // 트래킹 업데이트
    int updateTracking(AlarmTracking alarmTracking);

    // 트래킹 상태 변경
    int updateTrackingStatus(
            @Param("alarmTrackingId") Long alarmTrackingId,
            @Param("status") String status
    );
}