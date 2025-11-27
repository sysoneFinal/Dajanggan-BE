package com.dajanggan.domain.instance.controller;

import com.dajanggan.domain.instance.dto.*;
import com.dajanggan.domain.instance.service.InstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * 인스턴스 컨트롤러
 *
 * 주요 책임:
 * 
 *   인스턴스 CRUD API 제공
 *   PostgreSQL 연결 테스트 API 제공
 *   인스턴스와 데이터베이스 목록 조합 조회
 *   HTTP 요청/응답 처리 및 상태 코드 관리
 * 
 *
 * 엔드포인트:
 * 
 *   POST /api/instances - 인스턴스 생성
 *   POST /api/instances/test-connection - 연결 테스트
 *   GET /api/instances/{id} - 단건 조회
 *   GET /api/instances - 목록 조회 (쿼리 파라미터로 포함 여부 결정)
 *   PUT /api/instances/{id} - 수정
 *   DELETE /api/instances/{id} - 삭제
 *
 *  ----------  ------  --------------------------------------------------
 *  작업일자      작성자    Description
 *  ----------  ------  --------------------------------------------------
 *  2025-11-04  김민서    1. 최초작성자
 *
 */
@Tag(name = "Instance", description = "PostgreSQL 인스턴스 관리 API")
@RestController
@RequestMapping("/api/instances")
@RequiredArgsConstructor
@Slf4j
public class InstanceController {

    private final InstanceService instanceService;

    // ========== 생성 (Create) ==========

    /**
     * 인스턴스 생성
     *
     * 처리 흐름:
     * 
     *   인스턴스 정보 저장
     *   PostgreSQL 연결 및 데이터베이스 목록 조회
     *   데이터베이스 레코드 생성
     *   기본 대시보드 자동 생성
     * 
     *
     * @param request 인스턴스 생성 요청
     * @return 생성된 인스턴스 정보 (Location 헤더 포함)
     */
    @Operation(
            summary = "인스턴스 생성",
            description = "새로운 PostgreSQL 인스턴스를 등록하고 데이터베이스 목록을 자동으로 동기화합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
            @ApiResponse(responseCode = "500", description = "서버 오류 (DB 연결 실패 등)")
    })
    @PostMapping
    public ResponseEntity<InstanceResponse> createInstance(
            @Valid @RequestBody InstanceCreateRequest request
    ) {
        log.info("인스턴스 생성 API 호출: host={}, port={}",
                request.getHost(), request.getPort());

        InstanceResponse response = instanceService.create(request);

        // Location 헤더 생성 (RESTful 원칙)
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getInstanceId())
                .toUri();

        log.info("인스턴스 생성 완료: instanceId={}", response.getInstanceId());
        return ResponseEntity.created(location).body(response);
    }

    /**
     * PostgreSQL 연결 테스트
     *
     * 인스턴스 생성 전 연결 가능 여부를 확인하는 용도
     *
     * @param request 연결 테스트 요청 (인스턴스 생성 정보와 동일)
     * @return 연결 테스트 결과 (success, message, version, errorCode)
     */
    @Operation(
            summary = "PostgreSQL 연결 테스트",
            description = "인스턴스 생성 전에 PostgreSQL 서버 연결 가능 여부를 테스트합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "테스트 완료 (성공/실패 여부는 응답 본문 확인)"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 데이터")
    })
    @PostMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection(
            @Valid @RequestBody InstanceCreateRequest request
    ) {
        log.info("연결 테스트 API 호출: host={}, port={}",
                request.getHost(), request.getPort());

        Map<String, Object> response = instanceService.testConnection(request);

        log.info("연결 테스트 완료: success={}", response.get("success"));
        return ResponseEntity.ok(response);
    }

    // ========== 조회 (Read) ==========

    /**
     * 인스턴스 단건 조회
     *
     * @param id 인스턴스 ID
     * @return 인스턴스 정보
     */
    @Operation(
            summary = "인스턴스 단건 조회",
            description = "특정 인스턴스의 상세 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "인스턴스를 찾을 수 없음")
    })
    @GetMapping("/{id}")
    public ResponseEntity<InstanceResponse> getInstance(
            @Parameter(description = "인스턴스 ID", required = true)
            @PathVariable Long id
    ) {
        log.debug("인스턴스 조회 API 호출: id={}", id);

        InstanceResponse response = instanceService.findOne(id);

        return ResponseEntity.ok(response);
    }

    /**
     * 인스턴스 목록 조회 (데이터베이스 정보 제외)
     *
     * 쿼리 파라미터: 없음
     *
     * @return 인스턴스 목록
     */
    @Operation(
            summary = "인스턴스 목록 조회",
            description = "모든 인스턴스의 기본 정보를 조회합니다 (데이터베이스 목록 제외)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping(params = "!include")
    public ResponseEntity<List<InstanceResponse>> getInstanceList() {
        log.debug("인스턴스 목록 조회 API 호출");

        List<InstanceResponse> response = instanceService.findAll();

        log.debug("인스턴스 목록 조회 완료: count={}", response.size());
        return ResponseEntity.ok(response);
    }

    /**
     * 인스턴스 목록 조회 (데이터베이스 정보 포함)
     *
     * 쿼리 파라미터: include=databases
     *
     * @return 인스턴스와 데이터베이스 목록
     */
    @Operation(
            summary = "인스턴스 + 데이터베이스 목록 조회",
            description = "모든 인스턴스와 각 인스턴스에 속한 데이터베이스 목록을 함께 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping(params = "include=databases")
    public ResponseEntity<List<InstanceWithDatabasesDto>> getInstanceListWithDatabases() {
        log.debug("인스턴스 + 데이터베이스 목록 조회 API 호출");

        List<InstanceWithDatabasesDto> response = instanceService.findAllWithDatabases();

        log.debug("인스턴스 + 데이터베이스 목록 조회 완료: count={}", response.size());
        return ResponseEntity.ok(response);
    }

    // ========== 수정 (Update) ==========

    /**
     * 인스턴스 수정
     *
     * 연결 정보(호스트, 포트, 사용자명, 비밀번호) 변경 시
     * 데이터베이스 목록이 자동으로 재동기화됨
     *
     * @param id 인스턴스 ID
     * @param request 수정 요청
     * @return 수정된 인스턴스 정보
     */
    @Operation(
            summary = "인스턴스 수정",
            description = "인스턴스 정보를 수정합니다. 연결 정보 변경 시 데이터베이스 목록이 재동기화됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
            @ApiResponse(responseCode = "404", description = "인스턴스를 찾을 수 없음")
    })
    @PutMapping("/{id}")
    public ResponseEntity<InstanceResponse> updateInstance(
            @Parameter(description = "인스턴스 ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody InstanceUpdateRequest request
    ) {
        log.info("인스턴스 수정 API 호출: id={}", id);

        InstanceResponse response = instanceService.update(id, request);

        log.info("인스턴스 수정 완료: id={}", id);
        return ResponseEntity.ok(response);
    }

    // ========== 삭제 (Delete) ==========

    /**
     * 인스턴스 삭제
     *
     * 연관된 데이터베이스 레코드도 함께 삭제됨
     *
     * @param id 인스턴스 ID
     * @return 204 No Content
     */
    @Operation(
            summary = "인스턴스 삭제",
            description = "인스턴스와 연관된 모든 데이터베이스 레코드를 삭제합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "인스턴스를 찾을 수 없음")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInstance(
            @Parameter(description = "인스턴스 ID", required = true)
            @PathVariable Long id
    ) {
        log.info("인스턴스 삭제 API 호출: id={}", id);

        instanceService.delete(id);

        log.info("인스턴스 삭제 완료: id={}", id);
        return ResponseEntity.noContent().build();
    }
}