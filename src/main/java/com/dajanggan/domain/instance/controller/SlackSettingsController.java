package com.dajanggan.domain.instance.controller;

import com.dajanggan.domain.instance.dto.InstanceSlackSettingsResponse;
import com.dajanggan.domain.instance.dto.SlackSettingsRequest;
import com.dajanggan.domain.instance.service.SlackSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Slack 설정 컨트롤러
 *
 * 주요 책임:
 * 
 *   인스턴스별 Slack 알림 설정 API 제공
 *   Webhook URL, 채널, 멘션 관리
 *   HTTP 요청/응답 처리
 * 
 *
 * 엔드포인트:
 * 
 *   GET /api/instances/slack-settings - 전체 Slack 설정 목록 조회
 *   GET /api/instances/slack-settings/{instanceId} - 특정 인스턴스 Slack 설정 조회
 *   PUT /api/instances/slack-settings/{instanceId} - Slack 설정 업데이트
 *   DELETE /api/instances/slack-settings/{instanceId} - Slack 설정 삭제
 * 
 *  ----------  ------  --------------------------------------------------
 *    작업일자      작성자    Description
 *    ----------  ------  --------------------------------------------------
 *    2025-11-21  김민서    1. 최초작성자
 *
 */
@Tag(name = "Slack Settings", description = "Slack 알림 설정 관리 API")
@RestController
@RequestMapping("/api/instances/slack-settings")
@RequiredArgsConstructor
@Slf4j
public class SlackSettingsController {

    private final SlackSettingsService slackSettingsService;

    // ========== 조회 (Read) ==========

    /**
     * 모든 인스턴스의 Slack 설정 목록 조회
     *
     * @return 전체 인스턴스의 Slack 설정 목록
     */
    @Operation(
            summary = "전체 Slack 설정 목록 조회",
            description = "모든 인스턴스의 Slack 알림 설정 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping
    public ResponseEntity<List<InstanceSlackSettingsResponse>> getAllSlackSettings() {
        log.debug("전체 Slack 설정 목록 조회 API 호출");

        List<InstanceSlackSettingsResponse> settings = slackSettingsService.getAllSlackSettings();

        log.debug("Slack 설정 목록 조회 완료: count={}", settings.size());
        return ResponseEntity.ok(settings);
    }

    /**
     * 특정 인스턴스의 Slack 설정 조회
     *
     * @param instanceId 인스턴스 ID
     * @return Slack 설정 정보
     */
    @Operation(
            summary = "Slack 설정 조회",
            description = "특정 인스턴스의 Slack 알림 설정 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "인스턴스를 찾을 수 없음")
    })
    @GetMapping("/{instanceId}")
    public ResponseEntity<SlackSettingsRequest> getSlackSettings(
            @Parameter(description = "인스턴스 ID", required = true)
            @PathVariable Long instanceId
    ) {
        log.debug("Slack 설정 조회 API 호출: instanceId={}", instanceId);

        SlackSettingsRequest settings = slackSettingsService.getSlackSettingsById(instanceId);

        return ResponseEntity.ok(settings);
    }

    // ========== 수정 (Update) ==========

    /**
     * Slack 설정 업데이트
     *
     * 설정 가능 항목:
     * 
     *   enabled: Slack 알림 활성화 여부
     *   webhookUrl: Slack Webhook URL
     *   defaultChannel: 기본 채널명
     *   mention: 멘션할 사용자/그룹
     * 
     *
     * @param instanceId 인스턴스 ID
     * @param request Slack 설정 요청
     * @return 성공 메시지
     */
    @Operation(
            summary = "Slack 설정 업데이트",
            description = "인스턴스의 Slack 알림 설정을 업데이트합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "업데이트 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
            @ApiResponse(responseCode = "404", description = "인스턴스를 찾을 수 없음")
    })
    @PutMapping("/{instanceId}")
    public ResponseEntity<Map<String, String>> updateSlackSettings(
            @Parameter(description = "인스턴스 ID", required = true)
            @PathVariable Long instanceId,
            @Valid @RequestBody SlackSettingsRequest request
    ) {
        log.info("Slack 설정 업데이트 API 호출: instanceId={}, enabled={}",
                instanceId, request.getEnabled());

        slackSettingsService.updateSlackSettingsById(instanceId, request);

        log.info("Slack 설정 업데이트 완료: instanceId={}", instanceId);
        return ResponseEntity.ok(Map.of("message", "Slack 설정이 업데이트되었습니다."));
    }

    // ========== 삭제 (Delete) ==========

    /**
     * Slack 설정 삭제/초기화
     *
     * Slack 관련 모든 설정을 초기 상태로 되돌림
     * (enabled=false, webhookUrl=null, channel=null, mention=null)
     *
     * @param instanceId 인스턴스 ID
     * @return 성공 메시지
     */
    @Operation(
            summary = "Slack 설정 삭제",
            description = "인스턴스의 Slack 알림 설정을 초기화합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "인스턴스를 찾을 수 없음")
    })
    @DeleteMapping("/{instanceId}")
    public ResponseEntity<Map<String, String>> deleteSlackSettings(
            @Parameter(description = "인스턴스 ID", required = true)
            @PathVariable Long instanceId
    ) {
        log.info("Slack 설정 삭제 API 호출: instanceId={}", instanceId);

        slackSettingsService.deleteSlackSettingsById(instanceId);

        log.info("Slack 설정 삭제 완료: instanceId={}", instanceId);
        return ResponseEntity.ok(Map.of("message", "Slack 설정이 삭제되었습니다."));
    }
}