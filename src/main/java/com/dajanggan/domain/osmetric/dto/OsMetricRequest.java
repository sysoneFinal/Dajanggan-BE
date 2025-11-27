// 작성자 : 김동현s
package com.dajanggan.domain.osmetric.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * OS 메트릭 수신 요청 DTO
 * Agent에서 전송하는 데이터 형식
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OsMetricRequest {

    @NotBlank(message = "Instance name is required")
    private String instanceName;         // 인스턴스 이름 (자동 매핑용)

    @NotBlank(message = "Metric type is required")
    private String metricType;           // CPU, MEMORY, DISK

    @NotNull(message = "Details is required")
    private Map<String, Object> details; // 메트릭 상세 정보
    
    @NotNull(message = "Timestamp is required")
    private LocalDateTime timestamp;     // 수집 시각
}
