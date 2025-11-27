package com.dajanggan.domain.query.repository;

import com.dajanggan.domain.query.domain.QuerySuggestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 쿼리 제안 Repository
 * - LLM 기반 쿼리 최적화 제안 저장 및 조회
 * - 캐싱 기능을 통한 중복 제안 방지
 *
 * 작성자: 이해든
 */
@Mapper
public interface QuerySuggestionRepository {

    /**
     * 쿼리 제안 저장
     *
     * @param suggestion 저장할 쿼리 제안
     */
    void insert(QuerySuggestion suggestion);

    /**
     * 쿼리 제안 목록 일괄 저장
     *
     * @param suggestions 저장할 쿼리 제안 목록
     */
    void insertAll(@Param("suggestions") List<QuerySuggestion> suggestions);

    /**
     * 데이터베이스와 쿼리 해시로 제안 조회
     *
     * @param databaseId 데이터베이스 ID
     * @param queryHash 쿼리 해시값
     * @return 해당하는 모든 쿼리 제안 목록
     */
    List<QuerySuggestion> findByDatabaseIdAndQueryHash(
            @Param("databaseId") Long databaseId,
            @Param("queryHash") String queryHash);

    /**
     * 데이터베이스와 쿼리 해시로 최신 제안 조회
     * 캐싱 기능용 - 같은 쿼리에 대한 최신 제안 반환
     *
     * @param databaseId 데이터베이스 ID
     * @param queryHash 쿼리 해시값
     * @return 최신 쿼리 제안 목록 (생성일 기준 내림차순)
     */
    List<QuerySuggestion> findLatestByDatabaseIdAndQueryHash(
            @Param("databaseId") Long databaseId,
            @Param("queryHash") String queryHash);

    /**
     * 최근 제안 조회
     *
     * @param databaseId 데이터베이스 ID
     * @param fromDate 조회 시작 일시
     * @return 지정된 일시 이후의 쿼리 제안 목록
     */
    List<QuerySuggestion> findRecentSuggestions(
            @Param("databaseId") Long databaseId,
            @Param("fromDate") ZonedDateTime fromDate);

    /**
     * 캐시 히트율 통계 조회
     * 캐시된 제안과 신규 제안 비율 분석용
     *
     * @param isFromCache 캐시 여부
     * @return 해당 조건의 제안 개수
     */
    Long countByIsFromCache(@Param("isFromCache") Boolean isFromCache);
}