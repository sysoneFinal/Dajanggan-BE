package com.dajanggan.domain.osmetric.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

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
    private Map<String, Object> details;
    private LocalDateTime timestamp;
}
