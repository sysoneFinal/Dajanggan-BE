package com.dajanggan.domain.query.repository;

import com.dajanggan.domain.query.dto.raw.QueryRawMetricDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

@Mapper
public interface QueryRawRepository {

    /**
     * 원시 쿼리 메트릭 데이터를 모니터링 DB에 저장
     * (MyBatis 사용 - 모니터링 DB에 INSERT)
     */
    void insertQueryMetrics(@Param("metrics") List<QueryRawMetricDto> metrics);

    /**
     * 대상 PostgreSQL 인스턴스의 쿼리 메트릭 조회
     * (JdbcTemplate 사용 - 동적으로 다른 DB 조회)
     */
    List<QueryRawMetricDto> getQueryMetrics(JdbcTemplate jdbcTemplate);
}