package com.dajanggan.domain.alarm.controller;

import com.dajanggan.domain.alarm.dto.AlarmRuleDto;
import com.dajanggan.domain.alarm.service.AlarmRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/alarms/rules")
@RequiredArgsConstructor
public class AlarmRuleController {

    private final AlarmRuleService alarmRuleService;

    /**
     * 알림 규칙 목록 조회
     */
    @GetMapping
    public ResponseEntity<AlarmRuleDto.ListResponse> getRuleList(
            @RequestParam(required = false) Long instanceId,
            @RequestParam(required = false) Long databaseId,
            @RequestParam(required = false) String metricType,
            @RequestParam(required = false) Boolean enabled
    ) {
        log.info("알림 규칙 목록 조회 - instanceId: {}, databaseId: {}, metric: {}, enabled: {}",
                instanceId, databaseId, metricType, enabled);

        AlarmRuleDto.ListResponse response = alarmRuleService.getRuleList(
                instanceId, databaseId, metricType, enabled);

        return ResponseEntity.ok(response);
    }

    /**
     * 알림 규칙 상세 조회
     */
    @GetMapping("/{alarmRuleId}")
    public ResponseEntity<AlarmRuleDto.DetailResponse> getRuleDetail(
            @PathVariable Long alarmRuleId
    ) {
        log.info("알림 규칙 상세 조회 - alarmRuleId: {}", alarmRuleId);

        try {
            AlarmRuleDto.DetailResponse response = alarmRuleService.getRuleDetail(alarmRuleId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("규칙 조회 실패: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 알림 규칙 생성
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createRule(@RequestBody AlarmRuleDto.CreateRequest request) {
        log.info("알림 규칙 생성 - request: {}", request);

        try {
            Long alarmRuleId = alarmRuleService.createRule(request);

            Map<String, Object> response = new HashMap<>();
            response.put("alarmRuleId", alarmRuleId);
            response.put("message", "알림 규칙이 생성되었습니다.");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("규칙 생성 실패: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "알림 규칙 생성에 실패했습니다: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 알림 규칙 수정
     */
    @PutMapping("/{alarmRuleId}")
    public ResponseEntity<Map<String, String>> updateRule(
            @PathVariable Long alarmRuleId,
            @RequestBody AlarmRuleDto.UpdateRequest request
    ) {
        log.info("알림 규칙 수정 - alarmRuleId: {}, request: {}", alarmRuleId, request);

        try {
            request.setAlarmRuleId(alarmRuleId);
            alarmRuleService.updateRule(request);

            Map<String, String> response = new HashMap<>();
            response.put("message", "알림 규칙이 수정되었습니다.");

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("규칙 수정 실패: {}", e.getMessage());

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        } catch (Exception e) {
            log.error("규칙 수정 실패: {}", e.getMessage(), e);

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "알림 규칙 수정에 실패했습니다: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 알림 규칙 삭제
     */
    @DeleteMapping("/{alarmRuleId}")
    public ResponseEntity<Map<String, String>> deleteRule(@PathVariable Long alarmRuleId) {
        log.info("알림 규칙 삭제 - alarmRuleId: {}", alarmRuleId);

        try {
            alarmRuleService.deleteRule(alarmRuleId);

            Map<String, String> response = new HashMap<>();
            response.put("message", "알림 규칙이 삭제되었습니다.");

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("규칙 삭제 실패: {}", e.getMessage());

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        } catch (Exception e) {
            log.error("규칙 삭제 실패: {}", e.getMessage(), e);

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "알림 규칙 삭제에 실패했습니다: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 알림 규칙 활성화/비활성화
     */
    @PatchMapping("/{alarmRuleId}/enabled")
    public ResponseEntity<Map<String, String>> toggleRuleEnabled(
            @PathVariable Long alarmRuleId,
            @RequestParam Boolean enabled
    ) {
        log.info("알림 규칙 활성화 변경 - alarmRuleId: {}, enabled: {}", alarmRuleId, enabled);

        try {
            alarmRuleService.toggleRuleEnabled(alarmRuleId, enabled);

            Map<String, String> response = new HashMap<>();
            response.put("message", "알림 규칙 상태가 변경되었습니다.");

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("규칙 상태 변경 실패: {}", e.getMessage());

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }
    }
}