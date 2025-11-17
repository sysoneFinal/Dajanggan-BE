package com.dajanggan.domain.session.repository;

import com.dajanggan.domain.session.dto.raw.SessionRawMetricDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

@Mapper
public interface SessionRawRepository {
    
    /**
     * 원시 세션 메트릭 데이터를 모니터링 DB에 저장
     * (MyBatis 사용 - 모니터링 DB에 INSERT)
     */
    void insertSessionMetrics(@Param("metrics") List<SessionRawMetricDto> metrics);

    /**
     * 대상 PostgreSQL 인스턴스의 활성 세션 조회
     * (JdbcTemplate 사용 - 동적으로 다른 DB 조회)
     */
    List<SessionRawMetricDto> getActiveSessions(JdbcTemplate jdbcTemplate);

    /**
     * 대상 PostgreSQL 인스턴스의 현재 락 정보 조회
     * @return Map<PID, LockMode>
     */
    Map<Integer, String> getCurrentLocks(JdbcTemplate jdbcTemplate);


}

