/** 작성자 : 서샘이 */
package com.dajanggan.domain.session.dto.raw;

import lombok.*;
import org.springframework.data.annotation.Transient;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionRawMetricDto {

    private Long databaseId;
    private Long instanceId;
    private OffsetDateTime collectedAt;

    private Integer pid;
    private String username;
    private String databasename;
    private String clientAddr;
    private String applicationName;
    private String state;
    private Integer maxConnections;

    private String waitEventType;
    private String waitEvent;
    private String waitClass;
    private String bottleneckCause;

    private String impactLevel;
    private Double waitDurationSec;
    private Integer blockingPid;
    private String blockingUsername;

    private OffsetDateTime xactStart;  // 트랜잭션 시작 시간
    private String queryType;
    private String query;
    private OffsetDateTime queryStart;
    private Double queryAgeSec;

    private Double cpuUsage;
    private Double memoryUsageMb;

    private String lockType;
    private Double lockDurationMs;

    private Boolean isDeadlock;
    private String tableName;

    private OffsetDateTime createdAt;

    @Transient  // 임시저장 - 마이바티스면 매퍼에서 제외
    private LockSessionDto lockInfo;
}
