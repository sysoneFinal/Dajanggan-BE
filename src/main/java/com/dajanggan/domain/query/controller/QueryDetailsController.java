package com.dajanggan.domain.query.controller;

import com.dajanggan.domain.query.dto.QueryDetailsDto;
import com.dajanggan.domain.query.service.QueryDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Tag(name = "Query-Details", description = "쿼리 디테일 페이지 관련 API")
@RequestMapping("/api/query/details")
@RestController
public class QueryDetailsController {

    private final QueryDetailService queryDetailService;

    public QueryDetailsController(QueryDetailService queryDetailService) {
        this.queryDetailService = queryDetailService;
    }

    /** 쿼리 detail 페이지 전체 조회 (단일 DB용) */
    @Operation(summary = "쿼리 디테일 조회", description = "쿼리 details의 모든 지표를 불러옵니다. (단일 DB)")
    @GetMapping
    public ResponseEntity<QueryDetailsDto> getQueryDetailsMetric(
            @RequestParam("instanceId") Long instanceId,
            @RequestParam("databaseId") Long databaseId) {

        log.info("QueryDetails API 호출 - instanceId: {}, databaseId: {}", instanceId, databaseId);

        Map<String, Object> params = new HashMap<>();
        params.put("instanceId", instanceId);
        params.put("databaseId", databaseId);

        QueryDetailsDto response = queryDetailService.getQueryDetail(params);
        return ResponseEntity.ok(response);
    }
}