package com.dajanggan.domain.instance.dto;

import com.dajanggan.domain.instance.domain.Instance;
import lombok.Data;

@Data
public class InstanceCreateRequest {
    private String instanceName;
    private String host;
    private Integer port;
    private String userName;
    private String secretRef;
    private String sslmode;  // 기본값은 여기서 설정 안 함

    public Instance toEntity() {
        return Instance.builder()
                .instanceName(this.instanceName)
                .host(this.host)
                .port(this.port)
                .userName(this.userName)
                .secretRef(this.secretRef)
                .sslmode(this.sslmode != null && !this.sslmode.trim().isEmpty()
                        ? this.sslmode
                        : "disable")
                .build();
    }
}