package com.dajanggan.domain.session.repository;

import com.dajanggan.domain.session.dto.raw.ActivitySessionDto;
import com.dajanggan.domain.session.dto.raw.SessionRawMetricDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface SessionRawRepository {
    // 원시 데이터 저장
    void insertSessionMetrics(@Param("metrics") List<SessionRawMetricDto> metrics);

    // 활성화 세션 조회
    List<ActivitySessionDto> getActiveSessions();

    /** 현재 락 정보 조회 */
    Map<Integer, String> getCurrentLocks();


}
