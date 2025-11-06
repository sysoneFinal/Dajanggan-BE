package com.dajanggan.domain.instance.dto;

import com.dajanggan.domain.instance.domain.Instance;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class InstanceResponse {
    private Long instanceId;
    private String instanceName;
    private String host;
    private Integer port;
    private String userName;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String version;

    public static InstanceResponse from(Instance entity) {
        return InstanceResponse.builder()
                .instanceId(entity.getInstanceId())
                .instanceName(entity.getInstanceName())
                .host(entity.getHost())
                .port(entity.getPort())
                .userName(entity.getUserName())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .version(entity.getVersion())
                .build();
    }
}
