package com.dajanggan.domain.alarm.controller;

import com.dajanggan.domain.alarm.dto.AlarmFeedDto;
import com.dajanggan.domain.alarm.service.AlarmFeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/alarms/feeds")
@RequiredArgsConstructor
public class AlarmFeedController {

    private final AlarmFeedService alarmFeedService;

    /**
     * 알림 목록 조회
     */
    @GetMapping
    public ResponseEntity<AlarmFeedDto.ListResponse> getAlarmList(
            @RequestParam(required = false) Long instanceId,
            @RequestParam(required = false) Long databaseId,
            @RequestParam(required = false) String severityLevel,
            @RequestParam(required = false) Boolean isRead
    ) {
        log.info("알림 목록 조회 - instanceId: {}, databaseId: {}, severity: {}, isRead: {}",
                instanceId, databaseId, severityLevel, isRead);

        AlarmFeedDto.ListResponse response = alarmFeedService.getAlarmList(
                instanceId, databaseId, severityLevel, isRead);

        return ResponseEntity.ok(response);
    }

    /**
     * 알림 상세 조회
     */
    @GetMapping("/{alarmFeedId}")
    public ResponseEntity<AlarmFeedDto.DetailResponse> getAlarmDetail(
            @PathVariable Long alarmFeedId
    ) {
        log.info("알림 상세 조회 - alarmFeedId: {}", alarmFeedId);

        AlarmFeedDto.DetailResponse response = alarmFeedService.getAlarmDetail(alarmFeedId);

        return ResponseEntity.ok(response);
    }

    /**
     * 알림 읽음 처리
     */
    @PatchMapping("/{alarmFeedId}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long alarmFeedId) {
        log.info("알림 읽음 처리 - alarmFeedId: {}", alarmFeedId);

        alarmFeedService.markAsRead(alarmFeedId);

        return ResponseEntity.ok().build();
    }

    /**
     * 알림 확인 처리 (Acknowledge)
     */
    @PatchMapping("/{alarmFeedId}/acknowledge")
    public ResponseEntity<Void> acknowledgeAlarm(@PathVariable Long alarmFeedId) {
        log.info("알림 확인 처리 - alarmFeedId: {}", alarmFeedId);

        alarmFeedService.acknowledgeAlarm(alarmFeedId);

        return ResponseEntity.ok().build();
    }

    /**
     * 알림 삭제
     */
    @DeleteMapping("/{alarmFeedId}")
    public ResponseEntity<Void> deleteAlarm(@PathVariable Long alarmFeedId) {
        log.info("알림 삭제 - alarmFeedId: {}", alarmFeedId);

        alarmFeedService.deleteAlarm(alarmFeedId);

        return ResponseEntity.ok().build();
    }

    /**
     * 미확인 알림 개수 조회
     */
    @GetMapping("/unread/count")
    public ResponseEntity<Integer> getUnreadCount(@RequestParam Long instanceId) {
        log.info("미확인 알림 개수 조회 - instanceId: {}", instanceId);

        int count = alarmFeedService.getUnreadCount(instanceId);

        return ResponseEntity.ok(count);
    }
}