package com.dajanggan.domain.instance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstanceSlackSettingsResponse {
    private Long instanceId;
    private String instanceName;
    private Boolean slackEnabled;
    private String slackWebhookUrl;
    private String slackChannel;
    private String slackMention;
}

