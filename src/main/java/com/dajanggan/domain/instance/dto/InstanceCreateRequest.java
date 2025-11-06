package com.dajanggan.domain.instance.dto;

import lombok.Data;

@Data
public class InstanceCreateRequest {
    private String instanceName;
    private String host;
    private Integer port;
    private String userName;
    private String secretRef;
}
