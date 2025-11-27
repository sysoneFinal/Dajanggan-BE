package com.dajanggan.domain.instance.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 인스턴스와 데이터베이스 목록 복합 DTO
 *
 * 사용처:
 * 
 *   GET /api/instances?include=databases
 *   인스턴스와 하위 데이터베이스를 함께 조회
 * 
 *
 * 특징:
 * 
 *   인스턴스 정보 + 데이터베이스 목록
 *   uptime 자동 계산
 *   N+1 방지를 위한 일괄 조회 대상
 * 
 *
 *     ----------  ------  --------------------------------------------------
 *      작업일자      작성자    Description
 *      ----------  ------  --------------------------------------------------
 *      2025-11-04  김민서    1. 최초작성자
 *
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InstanceWithDatabasesDto {

    // ========== Instance 정보 ==========

    private Long instanceId;
    private String instanceName;
    private String host;
    private Integer port;
    private String userName;
    private String version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long uptimeMs;

    // ========== Database 목록 ==========

    /**
     * 하위 데이터베이스 목록
     *
     * 기본값: 빈 리스트 (null 방지)
     */
    @Builder.Default
    private List<DatabaseResponse> databases = new ArrayList<>();
}