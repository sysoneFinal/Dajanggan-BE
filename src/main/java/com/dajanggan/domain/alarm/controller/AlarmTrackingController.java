package com.dajanggan.domain.alarm.controller;

import com.dajanggan.domain.alarm.dto.AlarmTrackingDto;
import com.dajanggan.domain.alarm.service.AlarmTrackingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "Alarm-Tracking", description = "alarm 트래킹 페이지 관련 API")
@RestController
@RequestMapping("/api/alarms/tracking")
@RequiredArgsConstructor
public class AlarmTrackingController {

    private final AlarmTrackingService alarmTrackingService;

    /**
     * 트래킹 상태 목록 조회
     */
    @Tag(name = "Alarm-Tracking-view", description = "알람 트래킹 상태를 조회합니다")
    @GetMapping
    public ResponseEntity<List<AlarmTrackingDto.TrackingStatus>> getTrackingStatus(
            @RequestParam(required = false) Long instanceId,
            @RequestParam(required = false) String status
    ) {
        log.info("트래킹 상태 조회 - instanceId: {}, status: {}", instanceId, status);

        List<AlarmTrackingDto.TrackingStatus> response =
                alarmTrackingService.getTrackingStatus(instanceId, status);

        return ResponseEntity.ok(response);
    }
}