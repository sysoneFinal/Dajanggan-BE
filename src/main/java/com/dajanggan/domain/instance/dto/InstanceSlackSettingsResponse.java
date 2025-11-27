package com.dajanggan.domain.instance.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 인스턴스 Slack 설정 응답 DTO
 *
 * 사용처:
 * 
 *   GET /api/instances/slack-settings - 전체 Slack 설정 목록 조회
 * 
 *
 * 특징:
 * 
 *   인스턴스 기본 정보 + Slack 설정
 *   관리자가 전체 인스턴스의 Slack 설정을 한눈에 확인 가능
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InstanceSlackSettingsResponse {

    private Long instanceId;
    private String instanceName;

    @Builder.Default
    private Boolean slackEnabled = false;

    private String slackWebhookUrl;
    private String slackChannel;
    private String slackMention;
}