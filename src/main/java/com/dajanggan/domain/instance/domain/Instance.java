package com.dajanggan.domain.instance.domain;

import lombok.*;

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
    private String username;
    private String secretRef;
    private String sslmode;
    private Boolean isEnabled;
    private Boolean slackEnabled;
    private String slackChannel;
    private String slackMention;
    private String slackWebhookUrl;
    private Integer collectionInterval;
}
