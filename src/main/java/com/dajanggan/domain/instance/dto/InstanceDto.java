package com.dajanggan.domain.instance.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class InstanceDto {
    private Long instanceId;
    private String instanceName;
    private String host;
    private String dbname;
    private Integer port;
    private String username;
    private String secretRef;
    private String version;
    @Pattern(regexp = "disable|allow|prefer|require|verify-ca|verify-full")
    private String sslmode = "require";
    private Boolean isEnabled = true;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    private Boolean slackEnabled = false;
    private String slackChannel;
    private String slackMention;

    // URL 형식 검사 필요하면 패턴/커스텀 어노테이션 사용
    private String slackWebhookUrl;

}
