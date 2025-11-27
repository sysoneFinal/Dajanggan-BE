package com.dajanggan.domain.instance.controller;

import com.dajanggan.domain.instance.dto.DatabaseResponse;
import com.dajanggan.domain.instance.service.DatabaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 데이터베이스 컨트롤러
 *
 * 주요 책임:
 *
 *   인스턴스별 데이터베이스 목록 조회 API 제공
 *   데이터베이스 메트릭 정보 제공
 *   HTTP 요청/응답 처리
 *
 *
 *엔드포인트:
 *
 *   <GET /api/instances/{instanceId}/databases - 데이터베이스 목록 조회
 *
 *
 *  ----------  ------  --------------------------------------------------
 *  작업일자      작성자    Description
 *  ----------  ------  --------------------------------------------------
 *  2025-11-04  김민서    1. 최초작성자
 *
 */
@Tag(name = "Database", description = "데이터베이스 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/instances/{instanceId}/databases")
@Slf4j
class DatabaseController {

    private final DatabaseService databaseService;

    /**
     * 특정 인스턴스의 데이터베이스 목록 조회
     *
     * 반환 정보:
     * 
     *   데이터베이스 ID 및 이름
     *   현재 연결 수
     *   데이터베이스 크기 (바이트)
     *   캐시 히트율
     *   최종 업데이트 시간
     * 
     *
     * @param instanceId 인스턴스 ID
     * @return 데이터베이스 정보 리스트
     */
    @Operation(
            summary = "데이터베이스 목록 조회",
            description = "특정 인스턴스에 속한 모든 데이터베이스 목록과 메트릭 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "인스턴스를 찾을 수 없음")
    })
    @GetMapping
    public ResponseEntity<List<DatabaseResponse>> getDatabaseList(
            @Parameter(description = "인스턴스 ID", required = true)
            @PathVariable Long instanceId
    ) {
        log.debug("데이터베이스 목록 조회 API 호출: instanceId={}", instanceId);

        List<DatabaseResponse> databases = databaseService.getByInstanceId(instanceId);

        log.debug("데이터베이스 목록 조회 완료: instanceId={}, count={}",
                instanceId, databases.size());

        return ResponseEntity.ok(databases);
    }
}