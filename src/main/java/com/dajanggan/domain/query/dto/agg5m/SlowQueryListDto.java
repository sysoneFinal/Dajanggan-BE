package com.dajanggan.domain.query.dto.agg5m;

import lombok.*;

import java.time.OffsetDateTime;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class SlowQueryListDto {
    private Long queryMetricId;
    private OffsetDateTime collectedAt;
    private String queryText;
    private String shortQuery;
    private Double executionTimeMs;
    private String username;
    private String queryType;

    // 리소스 사용량 정보 추가
    private Double cpuUsagePercent;
    private Double memoryUsageMb;
    private Integer ioBlocks;
}