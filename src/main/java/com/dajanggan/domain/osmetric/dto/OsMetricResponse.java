package com.dajanggan.domain.osmetric.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * OS 메트릭 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OsMetricResponse {
    
    private Long instanceId;
    private String instanceName;
    private String metricType;
    private Double value;
    private LocalDateTime timestamp;
}
