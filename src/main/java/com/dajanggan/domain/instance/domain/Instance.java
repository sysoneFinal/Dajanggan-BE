package com.dajanggan.domain.instance.domain;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Instance {
    private Long instanceId;
    private String instanceName;
    private String host;
    private String dbname;
    private Integer port;
    private String status;
    private String userName;
    private String secretRef;
    private String sslmode;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Boolean slackEnabled;
    private String slackChannel;
    private String slackMention;
    private String slackWebhookUrl;
    private String version;

}
