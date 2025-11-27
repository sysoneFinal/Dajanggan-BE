package com.dajanggan.domain.alarm.controller;

import com.dajanggan.domain.alarm.dto.AlarmTrackingDto;
import com.dajanggan.domain.alarm.service.AlarmTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AlarmTracking 컨트롤러
 *
 * 주요 기능:
 * - 알람 추적 상태 조회
 * - 실시간 알람 모니터링
 *
 *
 * ----------  ------  --------------------------------------------------
 * 작업일자      작성자    Description
 * ----------  ------  --------------------------------------------------
 * 2025-11-13  김민서    1. 최초작성
 *
 */
@Slf4j
@Tag(name = "Alarm Tracking", description = "알람 추적 API")
@RestController
@RequestMapping("/api/alarms/tracking")
@RequiredArgsConstructor
public class AlarmTrackingController {

    private final AlarmTrackingService alarmTrackingService;

    /**
     * 추적 상태 목록 조회
     */
    @Operation(
            summary = "추적 상태 조회",
            description = "알람 추적 상태를 조회합니다. 실시간 알람 모니터링에 사용됩니다."
    )
    @GetMapping
    public ResponseEntity<List<AlarmTrackingDto.TrackingStatus>> getTrackingStatus(
            @Parameter(description = "인스턴스 ID") @RequestParam(required = false) Long instanceId,
            @Parameter(description = "상태 (PENDING, FIRED, RESOLVED)") @RequestParam(required = false) String status
    ) {
        log.debug("추적 상태 조회: instanceId={}, status={}", instanceId, status);

        List<AlarmTrackingDto.TrackingStatus> response =
                alarmTrackingService.getTrackingStatus(instanceId, status);

        return ResponseEntity.ok(response);
    }
}
