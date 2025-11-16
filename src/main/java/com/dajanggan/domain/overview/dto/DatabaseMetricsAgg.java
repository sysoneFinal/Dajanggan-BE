package com.dajanggan.domain.overview.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseMetricsAgg {
    private Long databaseAggId;
    private Long instanceId;
    private Long databaseId;
    private LocalDateTime collectedAt;

    // 세션/커넥션
    private Integer activeSessions;
    private Integer usedConnections;
    private Integer maxConnections;
    private BigDecimal connectionUsagePercent;

    // 트랜잭션
    private BigDecimal tpsTotal;

    // 대기 이벤트
    private Integer lockWaitCount;
    private Integer ioWaitCount;

    // Disk I/O
    private Long blksHit;
    private Long blksRead;
    private BigDecimal cacheHitRatio;
    private BigDecimal avgReadLatencyMs;
    private BigDecimal avgWriteLatencyMs;

    // 슬로우 쿼리
    private Integer slowQueryCount;
    private String topSlowQuery1;
    private BigDecimal topSlowQuery1Time;
    private String topSlowQuery2;
    private BigDecimal topSlowQuery2Time;
    private String topSlowQuery3;
    private BigDecimal topSlowQuery3Time;

    // Dead Tuple
    private Long deadTupleTotal;

    // 시스템 이벤트 (최근 5분)
    private Integer infoEventCount;
    private Integer warningEventCount;
    private Integer criticalEventCount;
    private String recentEventType;
    private String recentEventLevel;
    private Integer recentEventAgeMin;

    private LocalDateTime createdAt;
}
