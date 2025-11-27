package com.dajanggan.domain.alarm.controller;

import com.dajanggan.domain.alarm.dto.AlarmFeedDto;
import com.dajanggan.domain.alarm.service.AlarmFeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AlarmFeed 컨트롤러
 *
 * 주요 기능:
 * - 알람 피드 목록 조회
 * - 알람 상세 조회
 * - 알람 읽음 처리
 * - 알람 삭제
 * - 미확인 알람 개수 조회
 *
 *
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-13  김민서    1. 최초작성
 *
 */
@Slf4j
@Tag(name = "Alarm Feed", description = "알람 피드 API")
@RestController
@RequestMapping("/api/alarms/feeds")
@RequiredArgsConstructor
public class AlarmFeedController {

    private final AlarmFeedService alarmFeedService;

    /**
     * 알람 목록 조회
     */
    @Operation(
            summary = "알람 목록 조회",
            description = "필터 조건에 맞는 알람 피드 목록을 조회합니다."
    )
    @GetMapping
    public ResponseEntity<AlarmFeedDto.ListResponse> getAlarmList(
            @Parameter(description = "인스턴스 ID") @RequestParam(required = false) Long instanceId,
            @Parameter(description = "데이터베이스 ID") @RequestParam(required = false) Long databaseId,
            @Parameter(description = "심각도 (INFO, WARN, CRITICAL)") @RequestParam(required = false) String severityLevel,
            @Parameter(description = "읽음 여부") @RequestParam(required = false) Boolean isRead
    ) {
        log.debug("알람 목록 조회: instanceId={}, databaseId={}, severity={}, isRead={}",
                instanceId, databaseId, severityLevel, isRead);

        AlarmFeedDto.ListResponse response = alarmFeedService.getAlarmList(
                instanceId, databaseId, severityLevel, isRead);

        return ResponseEntity.ok(response);
    }

    /**
     * 알람 상세 조회
     */
    @Operation(
            summary = "알람 상세 조회",
            description = "알람 피드 ID로 상세 정보를 조회합니다. 레이턴시 차트, 관련 객체 포함."
    )
    @GetMapping("/{alarmFeedId}")
    public ResponseEntity<AlarmFeedDto.DetailResponse> getAlarmDetail(
            @Parameter(description = "알람 피드 ID", required = true) @PathVariable Long alarmFeedId
    ) {
        log.debug("알람 상세 조회: alarmFeedId={}", alarmFeedId);

        AlarmFeedDto.DetailResponse response = alarmFeedService.getAlarmDetail(alarmFeedId);

        return ResponseEntity.ok(response);
    }

    /**
     * 알람 읽음 처리
     */
    @Operation(
            summary = "알람 읽음 처리",
            description = "알람을 읽음 상태로 변경합니다."
    )
    @PatchMapping("/{alarmFeedId}/read")
    public ResponseEntity<Void> markAsRead(
            @Parameter(description = "알람 피드 ID", required = true) @PathVariable Long alarmFeedId
    ) {
        log.info("알람 읽음 처리: alarmFeedId={}", alarmFeedId);

        alarmFeedService.markAsRead(alarmFeedId);

        return ResponseEntity.ok().build();
    }

    /**
     * 알람 삭제
     */
    @Operation(
            summary = "알람 삭제",
            description = "알람 피드를 삭제합니다."
    )
    @DeleteMapping("/{alarmFeedId}")
    public ResponseEntity<Void> deleteAlarm(
            @Parameter(description = "알람 피드 ID", required = true) @PathVariable Long alarmFeedId
    ) {
        log.info("알람 삭제: alarmFeedId={}", alarmFeedId);

        alarmFeedService.deleteAlarm(alarmFeedId);

        return ResponseEntity.ok().build();
    }

    /**
     * 미확인 알람 개수 조회
     */
    @Operation(
            summary = "미확인 알람 개수 조회",
            description = "특정 인스턴스의 읽지 않은 알람 개수를 조회합니다."
    )
    @GetMapping("/unread/count")
    public ResponseEntity<Integer> getUnreadCount(
            @Parameter(description = "인스턴스 ID", required = true) @RequestParam Long instanceId
    ) {
        log.debug("미확인 알람 개수 조회: instanceId={}", instanceId);

        int count = alarmFeedService.getUnreadCount(instanceId);

        return ResponseEntity.ok(count);
    }
}
