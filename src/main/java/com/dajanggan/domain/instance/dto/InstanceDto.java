package com.dajanggan.domain.instance.dto;

import lombok.Data;

@Data
public class InstanceDto {
    private String instanceName;
    private String host;
    private String dbname;
    private Integer port;
    private String username;
    private String secretRef;   // 비밀번호 (암호화 전 평문 or 암호화 후 키)
    private String sslmode = "require";
    private Boolean isEnabled = true;
    private Boolean slackEnabled = false;
    private String slackChannel;
    private String slackMention;
    private String slackWebhookUrl;
    private Integer collectionInterval = 5;
}
