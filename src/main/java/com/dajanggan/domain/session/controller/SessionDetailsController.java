package com.dajanggan.domain.session.controller;

import com.dajanggan.domain.session.dto.SessionDetailsDto;
import com.dajanggan.domain.session.service.SessionAgg5mService;
import com.dajanggan.domain.session.service.SessionDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Tag(name = "Session-Details", description = "세션 디테일 페이지 관련 API")
@RequestMapping("/api/session/details")
@RestController
public class SessionDetailsController {

    private final SessionDetailService sessionDetailService;

    public SessionDetailsController(SessionDetailService sessionDetailService){
        this.sessionDetailService = sessionDetailService;
    }

    /** 세션 detail 페이지 전체 조회 (단일 DB용) */
    @Operation(summary = "세션 디테일 조회", description = "세션 details의 모든 지표를 불러옵니다. (단일 DB)")
    @GetMapping
    public ResponseEntity<SessionDetailsDto> getSessionDetailsMetric(
                @RequestParam("instanceId") Long instanceId, 
                @RequestParam("databaseId") Long databaseId ){

        Map<String , Object> params = new HashMap<>();
        params.put("instanceId", instanceId);
        params.put("databaseId", databaseId);


        SessionDetailsDto response = sessionDetailService.getSessionDetail(params);

        return ResponseEntity.ok(response);
    }
}
