package com.dajanggan.domain.query.controller;

import com.dajanggan.domain.query.dto.QueryDetailsDto;
import com.dajanggan.domain.query.service.QueryDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 쿼리 디테일 페이지 Controller
 * - 쿼리 상세 정보 조회
 *
 * 작성자: 이해든
 */
@Slf4j
@RestController
@RequestMapping("/api/query/details")
@RequiredArgsConstructor
@Tag(name = "Query-Details", description = "쿼리 디테일 페이지 관련 API")
public class QueryDetailsController {

    private final QueryDetailService queryDetailService;

    /**
     * 쿼리 detail 페이지 전체 조회 (단일 DB용)
     * 쿼리 details의 모든 지표를 불러옴
     */
    @GetMapping
    @Operation(summary = "쿼리 디테일 조회", description = "쿼리 details의 모든 지표를 불러옵니다. (단일 DB)")
    public ResponseEntity<QueryDetailsDto> getQueryDetailsMetric(
            @Parameter(description = "인스턴스 ID", required = true)
            @RequestParam("instanceId") Long instanceId,
            @Parameter(description = "데이터베이스 ID", required = true)
            @RequestParam("databaseId") Long databaseId) {

        log.info("QueryDetails API 호출 - instanceId: {}, databaseId: {}", instanceId, databaseId);

        Map<String, Object> params = new HashMap<>();
        params.put("instanceId", instanceId);
        params.put("databaseId", databaseId);

        QueryDetailsDto response = queryDetailService.getQueryDetail(params);
        return ResponseEntity.ok(response);
    }
}