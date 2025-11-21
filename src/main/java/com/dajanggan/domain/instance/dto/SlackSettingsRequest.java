package com.dajanggan.domain.instance.dto;

import lombok.Data;

@Data
public class SlackSettingsRequest {
    private String webhookUrl;
    private String defaultChannel;
    private String mention;
    private Boolean enabled;
}

