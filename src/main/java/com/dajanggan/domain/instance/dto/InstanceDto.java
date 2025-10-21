package com.dajanggan.domain.instance.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class InstanceDto {

    @NotBlank
    private String instanceName;

    @NotBlank
    private String host;

    @NotBlank
    private String dbname;

    @NotNull
    @Min(1) @Max(65535)
    private Integer port;

    @NotBlank
    private String username;

    // 비밀번호/키: 빈 문자열 허용 안 하면 @NotBlank
    @NotBlank
    private String secretRef;

    @Pattern(regexp = "disable|allow|prefer|require|verify-ca|verify-full")
    private String sslmode = "require";

    @NotNull
    private Boolean isEnabled = true;

    @NotNull
    private Boolean slackEnabled = false;

    private String slackChannel;
    private String slackMention;

    // URL 형식 검사 필요하면 패턴/커스텀 어노테이션 사용
    private String slackWebhookUrl;

    @NotNull
    @Min(1) @Max(60)
    private Integer collectionInterval = 5;
}
