package com.dajanggan.domain.alarm.controller;

import com.dajanggan.domain.alarm.dto.AlarmRuleDto;
import com.dajanggan.domain.alarm.service.AlarmRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AlarmRule 컨트롤러
 *
 * 주요 기능:
 * - 알람 규칙 CRUD
 * - 규칙 활성화/비활성화
 * - 레벨별 임계값 관리
 *
 *
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-13  김민서    1. 최초작성
 *
 */
@Slf4j
@Tag(name = "Alarm Rule", description = "알람 규칙 API")
@RestController
@RequestMapping("/api/alarms/rules")
@RequiredArgsConstructor
public class AlarmRuleController {

    private final AlarmRuleService alarmRuleService;

    /**
     * 알람 규칙 목록 조회
     */
    @Operation(
            summary = "알람 규칙 목록 조회",
            description = "필터 조건에 맞는 알람 규칙 목록을 조회합니다."
    )
    @GetMapping
    public ResponseEntity<AlarmRuleDto.ListResponse> getRuleList(
            @Parameter(description = "인스턴스 ID") @RequestParam(required = false) Long instanceId,
            @Parameter(description = "데이터베이스 ID") @RequestParam(required = false) Long databaseId,
            @Parameter(description = "메트릭 타입") @RequestParam(required = false) String metricType,
            @Parameter(description = "활성화 여부") @RequestParam(required = false) Boolean enabled
    ) {
        log.debug("알람 규칙 목록 조회: instanceId={}, databaseId={}, metric={}, enabled={}",
                instanceId, databaseId, metricType, enabled);

        AlarmRuleDto.ListResponse response = alarmRuleService.getRuleList(
                instanceId, databaseId, metricType, enabled);

        return ResponseEntity.ok(response);
    }

    /**
     * 알람 규칙 상세 조회
     */
    @Operation(
            summary = "알람 규칙 상세 조회",
            description = "알람 규칙 ID로 상세 정보를 조회합니다. 레벨별 임계값 설정 포함."
    )
    @GetMapping("/{alarmRuleId}")
    public ResponseEntity<AlarmRuleDto.DetailResponse> getRuleDetail(
            @Parameter(description = "알람 규칙 ID", required = true) @PathVariable Long alarmRuleId
    ) {
        log.debug("알람 규칙 상세 조회: alarmRuleId={}", alarmRuleId);

        try {
            AlarmRuleDto.DetailResponse response = alarmRuleService.getRuleDetail(alarmRuleId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("규칙 조회 실패: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 알람 규칙 생성
     */
    @Operation(
            summary = "알람 규칙 생성",
            description = "새로운 알람 규칙을 생성합니다. 레벨별 임계값, 집계 방식, 연산자를 설정합니다."
    )
    @PostMapping
    public ResponseEntity<Map<String, Object>> createRule(
            @Valid @RequestBody AlarmRuleDto.CreateRequest request
    ) {
        log.info("알람 규칙 생성: {}", request);

        try {
            Long alarmRuleId = alarmRuleService.createRule(request);

            Map<String, Object> response = new HashMap<>();
            response.put("alarmRuleId", alarmRuleId);
            response.put("message", "알람 규칙이 생성되었습니다.");

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.warn("규칙 생성 실패 (중복 또는 유효성 검사): {}", e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);

        } catch (Exception e) {
            log.error("규칙 생성 실패: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "알람 규칙 생성에 실패했습니다: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 알람 규칙 수정
     */
    @Operation(
            summary = "알람 규칙 수정",
            description = "기존 알람 규칙의 설정을 수정합니다."
    )
    @PutMapping("/{alarmRuleId}")
    public ResponseEntity<Map<String, String>> updateRule(
            @Parameter(description = "알람 규칙 ID", required = true) @PathVariable Long alarmRuleId,
            @Valid @RequestBody AlarmRuleDto.UpdateRequest request
    ) {
        log.info("알람 규칙 수정: alarmRuleId={}, request={}", alarmRuleId, request);

        try {
            request.setAlarmRuleId(alarmRuleId);
            alarmRuleService.updateRule(request);

            Map<String, String> response = new HashMap<>();
            response.put("message", "알람 규칙이 수정되었습니다.");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("규칙 수정 실패: {}", e.getMessage());

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);

        } catch (Exception e) {
            log.error("규칙 수정 실패: {}", e.getMessage(), e);

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "알람 규칙 수정에 실패했습니다: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 알람 규칙 삭제
     */
    @Operation(
            summary = "알람 규칙 삭제",
            description = "알람 규칙을 삭제합니다. 관련된 추적 정보도 함께 정리됩니다."
    )
    @DeleteMapping("/{alarmRuleId}")
    public ResponseEntity<Map<String, String>> deleteRule(
            @Parameter(description = "알람 규칙 ID", required = true) @PathVariable Long alarmRuleId
    ) {
        log.info("알람 규칙 삭제: alarmRuleId={}", alarmRuleId);

        try {
            alarmRuleService.deleteRule(alarmRuleId);

            Map<String, String> response = new HashMap<>();
            response.put("message", "알람 규칙이 삭제되었습니다.");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("규칙 삭제 실패: {}", e.getMessage());

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);

        } catch (Exception e) {
            log.error("규칙 삭제 실패: {}", e.getMessage(), e);

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "알람 규칙 삭제에 실패했습니다: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 알람 규칙 활성화/비활성화
     */
    @Operation(
            summary = "알람 규칙 활성화/비활성화",
            description = "알람 규칙의 활성화 상태를 변경합니다."
    )
    @PatchMapping("/{alarmRuleId}/enabled")
    public ResponseEntity<Map<String, String>> toggleRuleEnabled(
            @Parameter(description = "알람 규칙 ID", required = true) @PathVariable Long alarmRuleId,
            @Parameter(description = "활성화 여부", required = true) @RequestParam Boolean enabled
    ) {
        log.info("알람 규칙 활성화 변경: alarmRuleId={}, enabled={}", alarmRuleId, enabled);

        try {
            alarmRuleService.toggleRuleEnabled(alarmRuleId, enabled);

            Map<String, String> response = new HashMap<>();
            response.put("message", "알람 규칙 상태가 변경되었습니다.");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("규칙 상태 변경 실패: {}", e.getMessage());

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }
    }
}