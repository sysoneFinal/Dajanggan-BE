package com.dajanggan.domain.query.repository;

import com.dajanggan.domain.query.dto.raw.QueryRawMetricDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * 원시 쿼리 메트릭 Repository
 * - 대상 PostgreSQL 인스턴스에서 pg_stat_statements 기반 메트릭 수집
 * - MyBatis(모니터링 DB 저장)와 JdbcTemplate(대상 DB 조회) 혼용
 *
 * 작성자: 이해든
 */
@Mapper
public interface QueryRawRepository {

    /**
     * 원시 쿼리 메트릭 데이터를 모니터링 DB에 저장
     * MyBatis 사용 - 모니터링 DB에 INSERT
     *
     * @param metrics 저장할 쿼리 메트릭 DTO 목록
     */
    void insertQueryMetrics(@Param("metrics") List<QueryRawMetricDto> metrics);

    /**
     * 대상 PostgreSQL 인스턴스의 쿼리 메트릭 조회
     * JdbcTemplate 사용 - 동적으로 다른 DB 조회
     *
     * @param jdbcTemplate 대상 DB에 연결된 JdbcTemplate
     * @return pg_stat_statements에서 수집한 쿼리 메트릭 목록
     */
    List<QueryRawMetricDto> getQueryMetrics(JdbcTemplate jdbcTemplate);
}