package com.dajanggan.domain.osmetric.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * OS 메트릭 집계 데이터 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OsMetricAggResponse {
    
    private Long instanceId;
    private String instanceName;
    private String metricType;
    private Double avgValue;
    private Double maxValue;
    private Double minValue;
    private Integer sampleCount;
    private LocalDateTime collectedAt;
}
