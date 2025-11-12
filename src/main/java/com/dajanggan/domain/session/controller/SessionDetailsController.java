package com.dajanggan.domain.session.controller;

import com.dajanggan.domain.session.dto.SessionActive;
import com.dajanggan.domain.session.dto.SessionDetailsDto;
import com.dajanggan.domain.session.dto.agg5m.DeadLockDetailDto;
import com.dajanggan.domain.session.service.SessionAgg5mService;
import com.dajanggan.domain.session.service.SessionDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
@Tag(name = "Session-Details", description = "세션 디테일 페이지 관련 API")
@RequestMapping("/api/session/details")
@RestController
public class SessionDetailsController {

    private final SessionDetailService sessionDetailService;
    private final SessionAgg5mService sessionAgg5mService;

    public SessionDetailsController(SessionDetailService sessionDetailService, SessionAgg5mService sessionAgg5mService){
        this.sessionDetailService = sessionDetailService;
        this.sessionAgg5mService = sessionAgg5mService;
    }

    /** 세션 detail 페이지 전체 조회 (단일 DB용) */
    @Operation(summary = "세션 디테일 조회", description = "세션 details의 모든 지표를 불러옵니다. (단일 DB)")
    @GetMapping
    public ResponseEntity<SessionDetailsDto> getSessionDetailsMetric(
                @RequestParam("instanceId") Long instanceId, 
                @RequestParam("databaseId") Long databaseId ){

        log.info("SessionDetails API 호출 - instanceId: {}, databaseId: {}", instanceId, databaseId);
        
        Map<String , Object> params = new HashMap<>();
        params.put("instanceId", instanceId);
        params.put("databaseId", databaseId);

        SessionDetailsDto response = sessionDetailService.getSessionDetail(params);
        return ResponseEntity.ok(response);
    }

    /** 데드락 상세 정보 조회 */
    @Operation(summary = "데드락 상세 정보 조회", description = "데드락 상세 정보를 조회합니다 (모달용)")
    @GetMapping("/deadLock")
    public ResponseEntity<DeadLockDetailDto> getDeadLockDetail(
                                                        @RequestParam Long databaseId,
                                                        @RequestParam Integer pid) {
            log.info("데드락 상세 조회 요청 - databaseId: {}, pid: {}", databaseId, pid);
            
            Map<String, Object> params = new HashMap<>();
            params.put("databaseId", databaseId);
            params.put("pid", pid);

            DeadLockDetailDto response = sessionAgg5mService.findDeadLockDetail(params);

            return ResponseEntity.ok(response);
        }
    }
