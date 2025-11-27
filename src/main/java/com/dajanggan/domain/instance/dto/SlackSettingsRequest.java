package com.dajanggan.domain.instance.dto;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Slack 설정 요청 DTO
 *
 * 사용처:
 * 
 *   PUT /api/instances/slack-settings/{instanceId} - Slack 설정 업데이트
 *   GET /api/instances/slack-settings/{instanceId} - Slack 설정 조회 응답
 * 
 *
 * 검증 규칙:
 * 
 *   webhookUrl: Slack Webhook URL 형식
 *   defaultChannel: # 또는 @ 시작 (선택)
 *   mention: @ 시작 (선택)
 *   enabled: true/false
 * 
 *
 *     ----------  ------  --------------------------------------------------
 *      작업일자      작성자    Description
 *      ----------  ------  --------------------------------------------------
 *      2025-11-21  김민서    1. 최초작성자
 *
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlackSettingsRequest {

    @Pattern(
            regexp = "^https://hooks\\.slack\\.com/services/.*$",
            message = "올바른 Slack Webhook URL 형식이 아닙니다."
    )
    private String webhookUrl;

    @Pattern(
            regexp = "^#[a-z0-9_-]+$",
            message = "채널명은 #으로 시작하고 영문 소문자, 숫자, _, - 만 사용 가능합니다."
    )
    private String defaultChannel;

    @Pattern(
            regexp = "^@[a-zA-Z0-9_-]+$",
            message = "멘션은 @로 시작해야 합니다."
    )
    private String mention;

    @Builder.Default
    private Boolean enabled = false;
}