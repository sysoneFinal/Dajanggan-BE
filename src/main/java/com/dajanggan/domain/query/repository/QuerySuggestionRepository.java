package com.dajanggan.domain.query.repository;

import com.dajanggan.domain.query.domain.QuerySuggestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 쿼리 제안 Repository - MyBatis 방식 (캐싱 기능 추가)
 *
 * @author 이해든
 */
@Mapper
public interface QuerySuggestionRepository {

    /**
     * 쿼리 제안 저장
     */
    void insert(QuerySuggestion suggestion);

    /**
     * 쿼리 제안 목록 저장
     */
    void insertAll(@Param("suggestions") List<QuerySuggestion> suggestions);

    /**
     * 데이터베이스와 쿼리 해시로 제안 조회
     */
    List<QuerySuggestion> findByDatabaseIdAndQueryHash(
            @Param("databaseId") Long databaseId,
            @Param("queryHash") String queryHash);

    /**
     * 데이터베이스와 쿼리 해시로 최신 제안 조회 (캐싱용)
     * - 같은 queryHash에 대한 최신 제안들을 반환
     * - 생성일 기준 내림차순 정렬
     */
    List<QuerySuggestion> findLatestByDatabaseIdAndQueryHash(
            @Param("databaseId") Long databaseId,
            @Param("queryHash") String queryHash);

    /**
     * 최근 제안 조회
     */
    List<QuerySuggestion> findRecentSuggestions(
            @Param("databaseId") Long databaseId,
            @Param("fromDate") ZonedDateTime fromDate);

    /**
     * 캐시 히트율 통계 조회
     */
    Long countByIsFromCache(@Param("isFromCache") Boolean isFromCache);
}