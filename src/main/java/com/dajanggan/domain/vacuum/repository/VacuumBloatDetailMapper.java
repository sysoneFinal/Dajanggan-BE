package com.dajanggan.domain.vacuum.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface VacuumBloatDetailMapper {

    /**
     * 데이터베이스 내 테이블 목록 조회
     * - 최근 30일 내 데이터가 있는 테이블만 반환
     *
     * @param databaseId 데이터베이스 ID
     * @return 테이블 이름 목록
     */
    List<String> findTableList(@Param("databaseId") Long databaseId);

    /**
     * 특정 테이블의 최신 메트릭 조회 (KPI용)
     *
     * 반환 Map 구조:
     * - table_name: String
     * - collected_at: LocalDateTime
     * - bloat_ratio: BigDecimal (0~1 범위)
     * - bloat_bytes: Long
     * - dead_tuple_total: Long
     * - table_size: Long (relsize_total_bytes)
     * - last_vacuum: LocalDateTime
     *
     * @param databaseId 데이터베이스 ID
     * @param tableName 테이블 이름
     * @return 최신 메트릭 Map
     */
    Map<String, Object> findLatestMetricsByTable(
            @Param("databaseId") Long databaseId,
            @Param("tableName") String tableName
    );

    /**
     * 특정 테이블의 Bloat % 트렌드 조회 (일별 평균)
     *
     * 반환 Map 구조:
     * - collected_at: LocalDateTime (일별)
     * - bloat_ratio: BigDecimal (0~1 범위)
     *
     * @param databaseId 데이터베이스 ID
     * @param tableName 테이블 이름
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     * @return Bloat 트렌드 데이터
     */
    List<Map<String, Object>> findBloatTrendByTable(
            @Param("databaseId") Long databaseId,
            @Param("tableName") String tableName,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate
    );

    /**
     * 특정 테이블의 Dead Tuples 트렌드 조회 (일별 평균)
     *
     * 반환 Map 구조:
     * - collected_at: LocalDateTime (일별)
     * - dead_tuple_total: Long
     *
     * @param databaseId 데이터베이스 ID
     * @param tableName 테이블 이름
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     * @return Dead Tuples 트렌드 데이터
     */
    List<Map<String, Object>> findDeadTuplesTrendByTable(
            @Param("databaseId") Long databaseId,
            @Param("tableName") String tableName,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate
    );

    /**
     * 특정 테이블의 인덱스별 Bloat 트렌드 조회 (일별 평균)
     *
     * ⚠️ 현재는 index_raw_metrics 테이블이 없어서 빈 결과 반환
     * TODO: 인덱스 메트릭 수집 후 구현
     *
     * 반환 Map 구조 (향후):
     * - index_name: String
     * - collected_at: LocalDateTime (일별)
     * - bloat_ratio: BigDecimal (0~1 범위)
     *
     * @param databaseId 데이터베이스 ID
     * @param tableName 테이블 이름
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     * @return 인덱스 Bloat 트렌드 데이터
     */
    List<Map<String, Object>> findIndexBloatTrendByTable(
            @Param("databaseId") Long databaseId,
            @Param("tableName") String tableName,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate
    );
}