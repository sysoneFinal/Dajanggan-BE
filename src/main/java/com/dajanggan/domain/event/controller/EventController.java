package com.dajanggan.domain.event.controller;

import com.dajanggan.domain.event.dto.EventLog;
import com.dajanggan.domain.event.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "event-log", description = "이벤트 로그 API")
@RequestMapping("/api/event")
@RestController
public class EventController {
    private final EventService eventService;

    public EventController(EventService eventService){
        this.eventService = eventService;
    }

    // 필터링 목록
    @Operation(summary = "이벤트 카테고리", description = "이벤트 로그에 대한 카테고리를 조회합니다")
    @GetMapping("/filter-options")
    public ResponseEntity<Map<String, Object>> getFilterOptions(
            @RequestParam Long instanceId
    ) {
        Map<String, Object> result = new HashMap<>();

        result.put("databases", eventService.getDistinctDatabases(instanceId));
        result.put("categories", eventService.getDistinctCategories(instanceId));
        result.put("levels", List.of("INFO", "WARN", "CRITICAL"));

        return ResponseEntity.ok(result);
    }

    /** 요약카드 (최근 15분 내)*/
    @Operation(summary = "이벤트 요약 카드", description = "최근 15분 내 발생한 INFO/WARN/ERROR 이벤트 통계를 반환합니다.")
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getRecentSummary(@RequestParam Long instanceId) {
        return ResponseEntity.ok(eventService.getRecentSummary(instanceId));
    }

    // 이벤트 로그 조회
    @Operation(summary = "이벤트 로그 조회", description = "필터링과 함께 이벤트 로그를 조회합니다.")
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getEventList(
            @RequestParam Long instanceId,
            @RequestParam(required = false) String selectedTime,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String dbNames,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "14") int size
    ) {
        Map<String, Object> filters = new HashMap<>();
        filters.put("instanceId", instanceId);
        filters.put("selectedTime", selectedTime);
        filters.put("level", level);
        filters.put("dbNames", dbNames);
        filters.put("category", category);
        filters.put("page", page);
        filters.put("pageSize", size);
        filters.put("offset", (page - 1) * size);

        return ResponseEntity.ok(eventService.getEventList(filters));
    }

}
