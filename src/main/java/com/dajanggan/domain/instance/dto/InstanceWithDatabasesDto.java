package com.dajanggan.domain.instance.dto;

import lombok.*;
import java.time.OffsetDateTime;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class InstanceWithDatabasesDto {
    // Instance 필드
    private Long instanceId;
    private String instanceName;
    private String host;
    private Integer port;
    private Boolean isEnabled;
    private String version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long uptimeMs; // 계산 필드(선택)

    // 하위 Database 목록
    private List<DatabaseDto> databases;

}