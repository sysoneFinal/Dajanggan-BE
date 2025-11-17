package com.dajanggan.domain.osmetric.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent로부터 받는 OS 메트릭 데이터 요청 DTO
 * Agent 전송 형식:
 * {
 *   "instanceName": "gcp-wiki-dong-vm",
 *   "metricType": "CPU",
 *   "timestamp": "2024-11-15T10:30:45",
 *   "details": { ... }
 * }
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentOsMetricRequest {
    
    /**
     * 모니터링 인스턴스 이름 (Agent가 전송)
     */
    private String instanceName;
    
    /**
     * 메트릭 타입
     * CPU, MEMORY, DISK
     */
    private String metricType;
    
    /**
     * 데이터 수집 시각
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
    
    /**
     * 메트릭 상세 정보 (JSON 형태)
     * CPU: totalUsage, perCoreUsage, loadAverage
     * MEMORY: total, used, available, swap
     * DISK: readBytes, writeBytes, filesystem
     */
    private Map<String, Object> details;
}
