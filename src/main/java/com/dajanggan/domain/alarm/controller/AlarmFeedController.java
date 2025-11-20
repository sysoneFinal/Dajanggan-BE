package com.dajanggan.domain.alarm.controller;

import com.dajanggan.domain.alarm.dto.AlarmFeedDto;
import com.dajanggan.domain.alarm.service.AlarmFeedService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Alarm-Feed", description = "alarm 피드 페이지 관련 API")
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

    @Tag(name = "Alarm-Feed-detail", description = "알람 피드 아이디 별 alarm 상세 조회합니다")
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
    @Tag(name = "Alarm-Feed-read", description = "알람 피드의 읽음을 처리합니다")
    @PatchMapping("/{alarmFeedId}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long alarmFeedId) {
        log.info("알림 읽음 처리 - alarmFeedId: {}", alarmFeedId);

        alarmFeedService.markAsRead(alarmFeedId);

        return ResponseEntity.ok().build();
    }
    /**
     * 알림 삭제
     */
    @Tag(name = "Alarm-Feed-delete", description = "알람을 삭제합니다")
    @DeleteMapping("/{alarmFeedId}")
    public ResponseEntity<Void> deleteAlarm(@PathVariable Long alarmFeedId) {
        log.info("알림 삭제 - alarmFeedId: {}", alarmFeedId);

        alarmFeedService.deleteAlarm(alarmFeedId);

        return ResponseEntity.ok().build();
    }

    /**
     * 미확인 알림 개수 조회
     */
    @Tag(name = "Alarm-Feed-unread-count", description = "알람 미확인 개수를 조회합니다")
    @GetMapping("/unread/count")
    public ResponseEntity<Integer> getUnreadCount(@RequestParam Long instanceId) {
        log.info("미확인 알림 개수 조회 - instanceId: {}", instanceId);

        int count = alarmFeedService.getUnreadCount(instanceId);

        return ResponseEntity.ok(count);
    }
}