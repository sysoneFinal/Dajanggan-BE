package com.dajanggan.domain.instance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 데이터베이스 응답 DTO
 *
 * 사용처:
 * 
 *   데이터베이스 목록 조회 API 응답
 *   인스턴스와 데이터베이스 조합 조회
 * 
 *
 * 설계 원칙:
 * 
 *   불변 객체 (Getter만, Setter 제거)
 *   Builder 패턴으로 생성
 *   민감한 정보 제외
 * 
 *
 *     ----------  ------  --------------------------------------------------
 *      작업일자      작성자    Description
 *      ----------  ------  --------------------------------------------------
 *      2025-11-04  김민서    1. 최초작성자
 *      2025-11-06  김민서    2. 상태 추가
 *
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseResponse {

    private Long databaseId;
    private Long instanceId;
    private String databaseName;
    private String status;
    private Integer connections;
    private String sizeBytes;
    private String cacheHitRate;
    private OffsetDateTime updatedAt;
}