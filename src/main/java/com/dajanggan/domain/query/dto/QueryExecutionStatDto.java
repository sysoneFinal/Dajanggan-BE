package com.dajanggan.domain.query.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * ExecutionStatus용 쿼리별 집계 통계 DTO
 *
 * 기능:
 * - 개별 쿼리문별로 실행 횟수, 평균/총 시간 등을 집계
 * - 쿼리 해시(MD5)를 기준으로 동일 쿼리 그룹화
 * - ExecutionStatus 화면에서 쿼리 성능 분석용
 *
 * @author 이해든
 */
@Data
public class QueryExecutionStatDto {

    // 쿼리 고유 ID (MD5 해시)
    private String queryHash;

    // 짧은 쿼리문 (80자 제한)
    private String shortQuery;

    // 전체 쿼리문
    private String fullQuery;

    // 실행 횟수
    private Integer executionCount;

    // 평균 실행 시간 (ms)
    private Double avgTimeMs;

    // 총 실행 시간 (ms)
    private Double totalTimeMs;

    // 호출 수 (= executionCount)
    private Integer callCount;

    // 쿼리 타입 (SELECT, INSERT, UPDATE, DELETE 등)
    private String queryType;

    // 마지막 실행 시간
    private LocalDateTime lastExecutedAt;
}