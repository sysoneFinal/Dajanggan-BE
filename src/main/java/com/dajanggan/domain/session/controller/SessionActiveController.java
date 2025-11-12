package com.dajanggan.domain.session.controller;

import com.dajanggan.domain.session.dto.SessionActive;
import com.dajanggan.domain.session.service.SessionActiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Session-Active", description = "세션 활성화 페이지 관련 API")
@RequestMapping("/api/session/active")
@RestController
public class SessionActiveController {

    private final SessionActiveService sessionActiveService;

    public SessionActiveController(SessionActiveService sessionActiveService) {
        this.sessionActiveService = sessionActiveService;
    }

    /** 필터링 옵션 조회 */
    @Operation(summary = "필터 옵션 조회", description = "Active Session 필터링에 사용할 옵션들을 조회합니다")
    @GetMapping("/filter-options")
    public ResponseEntity<Map<String, Object>> getFilterOptions(
            @RequestParam Long instanceId
    ) {
        Map<String, Object> result = new HashMap<>();

        result.put("databases", sessionActiveService.getDistinctDatabases(instanceId));
        result.put("states", sessionActiveService.getDistinctStates(instanceId));
        result.put("waitEventTypes", sessionActiveService.getDistinctWaitEventTypes(instanceId));
        result.put("queryTypes", List.of("SELECT", "INSERT", "UPDATE", "DELETE", "DDL", "VACUUM", "OTHER"));

        return ResponseEntity.ok(result);
    }

    /** 요약 카드 (최근 수집 시점) */
    @Operation(summary = "세션 요약 카드", description = "최근 수집 시점의 Active/Waiting/Idle 세션 통계를 반환합니다.")
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getRecentSummary(@RequestParam Long instanceId) {
        return ResponseEntity.ok(sessionActiveService.getRecentSummary(instanceId));
    }

    /** Active Session 리스트 조회 */
    @Operation(summary = "Active Session 리스트 조회", description = "필터링과 함께 Active Session 리스트를 조회합니다.")
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getSessionActiveList(
            @RequestParam Long instanceId,
            @RequestParam(required = false) String dbNames,
            @RequestParam(required = false) String states,
            @RequestParam(required = false) String waitEventTypes,
            @RequestParam(required = false) String queryTypes,
            @RequestParam(required = false) String username,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "14") int size
    ) {
        Map<String, Object> filters = new HashMap<>();
        filters.put("instanceId", instanceId);
        filters.put("dbNames", dbNames);
        filters.put("states", states);
        filters.put("waitEventTypes", waitEventTypes);
        filters.put("queryTypes", queryTypes);
        filters.put("username", username);
        filters.put("page", page);
        filters.put("pageSize", size);
        filters.put("offset", (page - 1) * size);

        return ResponseEntity.ok(sessionActiveService.getSessionList(filters));
    }

    /** 세션 상세 정보 조회 */
    @Operation(summary = "세션 상세 정보 조회", description = "특정 세션의 상세 정보를 조회합니다 (모달용)")
    @GetMapping("/detail")
    public ResponseEntity<SessionActive> getSessionDetail(
            @RequestParam Long databaseId,
            @RequestParam Integer pid
    ) {
        return ResponseEntity.ok(sessionActiveService.getSessionDetail(databaseId, pid));
    }
}
